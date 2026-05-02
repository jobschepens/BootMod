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
import net.minecraft.world.phys.Vec3;

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

    private static final double WATER_SPEED = 0.35;
    private static final double LAND_SPEED  = 0.03;

    // Client-side cache; invalidated whenever the synced data changes
    private List<RaftBlock> cachedBlocks = null;

    public RaftEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(BLOCKS_DATA, new CompoundTag());
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

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;

        // If empty, just float in place
        if (this.getPassengers().isEmpty()) {
            applyBuoyancy();
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

        double vy = this.getDeltaMovement().y;
        if (onWater) {
            vy = this.isInWater() ? Math.min(vy + 0.05, 0.1) : Math.max(vy - 0.02, -0.04);
        } else {
            vy = this.onGround() ? 0 : Math.max(vy - 0.08, -0.5);
        }

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        double friction = onWater ? 0.87 : 0.91;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 1.0, friction));

        this.resetFallDistance();
        rider.resetFallDistance();
    }

    private void applyBuoyancy() {
        if (!isOnOrInWater()) return;
        double vy = this.getDeltaMovement().y;
        vy = this.isInWater() ? Math.min(vy + 0.03, 0.05) : Math.max(vy - 0.01, -0.02);
        this.setDeltaMovement(
            this.getDeltaMovement().x * 0.95,
            vy,
            this.getDeltaMovement().z * 0.95
        );
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    // -------------------------------------------------------------------------
    //  Interactions
    // -------------------------------------------------------------------------

    /** Right-click with empty hand to remount. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.getPassengers().isEmpty() && !level().isClientSide()) {
            player.startRiding(this, true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** Hitting the raft with any tool breaks it and drops the blocks + steering wheel. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide()) return true;
        if (!(source.getEntity() instanceof Player)) return false;

        for (RaftBlock block : getRaftBlocks()) {
            BlockPos dropPos = this.blockPosition().offset(block.rx(), block.ry(), block.rz());
            Block.dropResources(block.state(), level(), dropPos);
        }
        this.spawnAtLocation(new ItemStack(BootMod.STEERING_WHEEL.get()));
        this.discard();
        return true;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return this.position().add(2.0, 0, 0);
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
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("raft_blocks")) {
            this.entityData.set(BLOCKS_DATA, tag.getCompound("raft_blocks"));
        }
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 4096;
    }
}
