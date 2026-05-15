package com.boardmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RaftEntity extends Entity {

    /**
     * One block in the raft, with its position relative to the steering-wheel block
     * (= entity origin) and its BlockState.
     */
    public record RaftBlock(int rx, int ry, int rz, BlockState state) {}

    // Synced to clients so the renderer can draw the actual blocks
    private static final EntityDataAccessor<CompoundTag> BLOCKS_DATA =
            SynchedEntityData.defineId(RaftEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final EntityDataAccessor<Boolean> HAS_LEVER =
            SynchedEntityData.defineId(RaftEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> HAS_AUTOPILOT =
            SynchedEntityData.defineId(RaftEntity.class, EntityDataSerializers.BOOLEAN);

    private static final double WATER_SPEED  = 0.35;
    private static final double LAND_SPEED   = 0.03;
    private static final double LEVER_SPEED  = 0.3;
    private static final double AUTOPILOT_SPEED = 0.25;

    // Server-side autopilot wander state
    private float autopilotTargetYaw = 0f;
    private int   autopilotTurnTimer = 0;

    private static final Field JUMPING_FIELD;
    static {
        try {
            JUMPING_FIELD = LivingEntity.class.getDeclaredField("jumping");
            JUMPING_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Could not find jumping field", e);
        }
    }

    private static boolean isJumping(Player rider) {
        try {
            return (boolean) JUMPING_FIELD.get(rider);
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    // Client-side cache; invalidated whenever the synced data changes
    private List<RaftBlock> cachedBlocks = null;

    // Bounding-box extents computed from raft blocks (relative offsets, default to 1-block)
    private double bbMinX = -0.5, bbMaxX = 0.5;
    private double bbMinZ = -0.5, bbMaxZ = 0.5;
    private double bbMaxY = 1.0;

    // Client-side smooth interpolation toward the latest server position
    private int lerpSteps = 0;
    private double lerpX, lerpY, lerpZ;
    private double lerpYRot;

    public RaftEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCKS_DATA, new CompoundTag());
        builder.define(HAS_LEVER, false);
        builder.define(HAS_AUTOPILOT, false);
    }

    public boolean hasLever() {
        return this.entityData.get(HAS_LEVER);
    }

    public void setHasLever(boolean value) {
        this.entityData.set(HAS_LEVER, value);
    }

    public boolean hasAutopilot() {
        return this.entityData.get(HAS_AUTOPILOT);
    }

    public void setAutopilot(boolean value) {
        this.entityData.set(HAS_AUTOPILOT, value);
    }

    // -------------------------------------------------------------------------
    //  Block data helpers
    // -------------------------------------------------------------------------

    public void setRaftBlocks(List<RaftBlock> blocks) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (RaftBlock b : blocks) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", b.rx());
            entry.putInt("y", b.ry());
            entry.putInt("z", b.rz());
            entry.put("state", NbtUtils.writeBlockState(b.state()));
            list.add(entry);
        }
        tag.put("blocks", list);
        this.entityData.set(BLOCKS_DATA, tag);
        this.cachedBlocks = blocks;
        updateBlockExtents(blocks);
    }

    private void updateBlockExtents(List<RaftBlock> blocks) {
        if (blocks.isEmpty()) return;
        int minRX = 0, maxRX = 0, minRZ = 0, maxRZ = 0, maxRY = 0;
        for (RaftBlock b : blocks) {
            minRX = Math.min(minRX, b.rx());
            maxRX = Math.max(maxRX, b.rx());
            minRZ = Math.min(minRZ, b.rz());
            maxRZ = Math.max(maxRZ, b.rz());
            maxRY = Math.max(maxRY, b.ry());
        }
        bbMinX = minRX - 0.5;
        bbMaxX = maxRX + 0.5;
        bbMinZ = minRZ - 0.5;
        bbMaxZ = maxRZ + 0.5;
        bbMaxY = maxRY + 1.0;
    }

    public List<RaftBlock> getRaftBlocks() {
        if (cachedBlocks != null) return cachedBlocks;
        cachedBlocks = parseBlocksFromTag(this.entityData.get(BLOCKS_DATA));
        return cachedBlocks;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (BLOCKS_DATA.equals(key)) {
            cachedBlocks = null; // force re-parse on next access
            updateBlockExtents(getRaftBlocks()); // recalculate AABB extents
        }
        super.onSyncedDataUpdated(key);
    }

    private List<RaftBlock> parseBlocksFromTag(CompoundTag tag) {
        if (!tag.contains("blocks")) return Collections.emptyList();
        List<RaftBlock> blocks = new ArrayList<>();
        ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int rx = entry.getInt("x");
            int ry = entry.getInt("y");
            int rz = entry.getInt("z");
            BlockState state = NbtUtils.readBlockState(
                    level().registryAccess().lookupOrThrow(Registries.BLOCK),
                    entry.getCompound("state"));
            blocks.add(new RaftBlock(rx, ry, rz, state));
        }
        return blocks;
    }

    // -------------------------------------------------------------------------
    //  Physics
    // -------------------------------------------------------------------------

    private boolean isOnOrInWater() {
        if (this.isInWater()) return true;
        BlockPos pos = this.blockPosition();
        return level().getFluidState(pos).is(FluidTags.WATER)
                || level().getFluidState(pos.below()).is(FluidTags.WATER);
    }

    /**
     * Returns the Y that the entity should sit at so the bottom raft blocks rest
     * exactly on the water surface (= top of the highest water block beneath us).
     * Returns Double.NaN if no water block is found nearby.
     */
    private double getWaterSurfaceTargetY() {
        int bx = (int) Math.floor(this.getX());
        int bz = (int) Math.floor(this.getZ());
        int startY = (int) Math.floor(this.getY()) + 2;
        for (int dy = 0; dy <= 5; dy++) {
            BlockPos p = new BlockPos(bx, startY - dy, bz);
            if (level().getFluidState(p).is(FluidTags.WATER)) {
                return p.getY() + 1.0; // exact top of the water block
            }
        }
        return Double.NaN;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = steps;
    }

    @Override
    public void tick() {
        super.tick();

        // Client: smoothly interpolate toward the latest server position each tick
        if (level().isClientSide()) {
            if (lerpSteps > 0) {
                double nx = this.getX() + (lerpX - this.getX()) / lerpSteps;
                double ny = this.getY() + (lerpY - this.getY()) / lerpSteps;
                double nz = this.getZ() + (lerpZ - this.getZ()) / lerpSteps;
                float nyRot = (float)(this.getYRot() + (lerpYRot - this.getYRot()) / lerpSteps);
                lerpSteps--;
                this.setPos(nx, ny, nz);
                this.setRot(nyRot, this.getXRot());
            }
            return;
        }

        // If empty, just float in place (or autopilot)
        if (this.getPassengers().isEmpty()) {
            if (hasAutopilot()) {
                tickAutopilot();
            } else {
                applyBuoyancy();
            }
            return;
        }

        Entity passenger = this.getPassengers().get(0);
        if (!(passenger instanceof Player rider)) return;

        this.setYRot(rider.getYRot());
        this.yRotO = this.getYRot();

        boolean onWater = isOnOrInWater();
        double speed = onWater ? WATER_SPEED : LAND_SPEED;

        float forward = rider.zza;
        float strafe  = rider.xxa;

        double yawRad = Math.toRadians(this.getYRot());
        double vx = (-Math.sin(yawRad) * forward + -Math.cos(yawRad) * strafe) * speed;
        double vz = ( Math.cos(yawRad) * forward +  Math.sin(yawRad) * strafe) * speed;

        double vy;
        if (hasLever()) {
            boolean jumping = isJumping(rider);
            boolean sneaking = rider.isShiftKeyDown();
            if (jumping) {
                vy = LEVER_SPEED;
            } else if (sneaking) {
                vy = -LEVER_SPEED;
            } else {
                vy = 0; // hover
            }
        } else if (onWater) {
            // Spring toward water surface: converges without oscillation
            double target = getWaterSurfaceTargetY();
            vy = Double.isNaN(target)
                    ? this.getDeltaMovement().y * 0.5
                    : Math.max(-0.15, Math.min(0.15, (target - this.getY()) * 0.35));
        } else {
            vy = this.onGround() ? 0 : Math.max(this.getDeltaMovement().y - 0.08, -0.5);
        }

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        double friction = onWater ? 0.87 : 0.91;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 1.0, friction));

        this.resetFallDistance();
        rider.resetFallDistance();
    }

    /**
     * Autopilot tick for the raft: wander randomly.
     */
    private void tickAutopilot() {
        boolean onWater = isOnOrInWater();

        if (autopilotTurnTimer <= 0) {
            float delta = (float)(this.random.nextFloat() * 120f - 60f);
            autopilotTargetYaw = this.getYRot() + delta;
            autopilotTurnTimer = 60 + this.random.nextInt(61); // 3–6 sec
        }
        autopilotTurnTimer--;

        float currentYaw = this.getYRot();
        float diff = autopilotTargetYaw - currentYaw;
        while (diff > 180f)  diff -= 360f;
        while (diff < -180f) diff += 360f;
        float step = Math.max(-1.5f, Math.min(1.5f, diff));
        float newYaw = currentYaw + step;
        this.setYRot(newYaw);
        this.yRotO = newYaw;

        double speed = onWater ? AUTOPILOT_SPEED : LAND_SPEED;
        double yawRad = Math.toRadians(newYaw);
        double vx = -Math.sin(yawRad) * speed;
        double vz =  Math.cos(yawRad) * speed;

        double vy;
        if (onWater) {
            double target = getWaterSurfaceTargetY();
            vy = Double.isNaN(target)
                    ? this.getDeltaMovement().y * 0.5
                    : Math.max(-0.1, Math.min(0.1, (target - this.getY()) * 0.2));
        } else {
            vy = Math.max(this.getDeltaMovement().y - 0.08, -0.5);
        }

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        double friction = onWater ? 0.87 : 0.91;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 1.0, friction));
        this.resetFallDistance();
    }

    private void applyBuoyancy() {
        double vy;
        if (isOnOrInWater()) {
            double target = getWaterSurfaceTargetY();
            vy = Double.isNaN(target)
                    ? this.getDeltaMovement().y * 0.5
                    : Math.max(-0.1, Math.min(0.1, (target - this.getY()) * 0.2));
        } else {
            vy = Math.max(this.getDeltaMovement().y - 0.08, -0.5);
        }
        this.setDeltaMovement(
            this.getDeltaMovement().x * 0.9,
            vy,
            this.getDeltaMovement().z * 0.9
        );
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    // -------------------------------------------------------------------------
    //  Interactions
    // -------------------------------------------------------------------------

    /** Dynamic AABB covering all raft blocks so players can stand anywhere on the structure. */
    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        this.setBoundingBox(new AABB(x + bbMinX, y, z + bbMinZ, x + bbMaxX, y + bbMaxY, z + bbMaxZ));
    }

    /** Makes the raft physically solid — players cannot walk through it. */
    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    /** Right-click to remount, or sneak+click to attach lever/autopilot. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            // Sneak + lever: bevestig hendel
            if (held.getItem() instanceof BoatLeverItem && !hasLever()) {
                if (!level().isClientSide()) {
                    setHasLever(true);
                    if (!player.getAbilities().instabuild) held.shrink(1);
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }

            // Sneak + autopilot: bevestig autopilot
            if (held.getItem() instanceof AutopilotItem && !hasAutopilot()) {
                if (!level().isClientSide()) {
                    setAutopilot(true);
                    autopilotTargetYaw = this.getYRot();
                    autopilotTurnTimer = 0;
                    if (!player.getAbilities().instabuild) held.shrink(1);
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
        }

        // Gewone rechtsklik + lege hand: instappen (nooit bij sneaken → anders val je erdoorheen)
        if (held.isEmpty() && !player.isShiftKeyDown() && this.getPassengers().isEmpty()) {
            if (!level().isClientSide()) {
                player.startRiding(this, true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        // Voorkom dat andere items hun use() afvuren op het vlot
        return InteractionResult.CONSUME;
    }

    /** Makes the raft's bounding box solid so players can stand on it after dismounting. */
    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    /** Hitting the raft with any tool breaks it and drops the blocks + steering wheel + lever. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return true;
        if (!(source.getEntity() instanceof Player)) return false;

        for (RaftBlock block : getRaftBlocks()) {
            BlockPos dropPos = this.blockPosition().offset(block.rx(), block.ry(), block.rz());
            Block.dropResources(block.state(), level(), dropPos);
        }
        this.spawnAtLocation(new ItemStack(BootMod.STEERING_WHEEL.get()));
        if (hasLever()) {
            this.spawnAtLocation(new ItemStack(BootMod.BOAT_LEVER.get()));
        }
        if (hasAutopilot()) {
            this.spawnAtLocation(new ItemStack(BootMod.AUTOPILOT.get()));
        }
        this.discard();
        return true;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        // Speler staat bovenop het vlot (voeten op bbMaxY)
        return this.position().add(0, bbMaxY, 0);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Plaats speler bovenop het vlot zodat ze er niet doorheen vallen
        return this.position().add(0, bbMaxY + 0.1, 0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    // -------------------------------------------------------------------------
    //  Save / Load
    // -------------------------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("raft_blocks", this.entityData.get(BLOCKS_DATA));
        tag.putBoolean("HasLever", hasLever());
        tag.putBoolean("HasAutopilot", hasAutopilot());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("raft_blocks")) {
            this.entityData.set(BLOCKS_DATA, tag.getCompound("raft_blocks"));
        }
        setHasLever(tag.getBoolean("HasLever"));
        setAutopilot(tag.getBoolean("HasAutopilot"));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 4096;
    }
}
