package com.boardmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

public class MotorboatEntity extends Entity {

    // 12 blocks/sec = 0.6 blocks/tick on water
    private static final double WATER_SPEED  = 0.6;
    // Nearly stuck on land
    private static final double LAND_SPEED   = 0.05;
    // Vertical speed when the lever is used
    private static final double LEVER_SPEED  = 0.3;
    // Autopilot cruising speed
    private static final double AUTOPILOT_SPEED = 0.35;

    private static final EntityDataAccessor<Boolean> HAS_LEVER =
            SynchedEntityData.defineId(MotorboatEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_AUTOPILOT =
            SynchedEntityData.defineId(MotorboatEntity.class, EntityDataSerializers.BOOLEAN);

    // Server-side autopilot wander state (not synced, recomputed each session)
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

    public MotorboatEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
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

        // No rider
        if (this.getPassengers().isEmpty()) {
            if (hasAutopilot()) {
                // Autopilot active: wander around without a rider
                tickAutopilot();
            } else {
                // Float/drift in place — do NOT drop or discard
                applyIdlePhysics();
            }
            return;
        }

        Entity passenger = this.getPassengers().get(0);
        if (!(passenger instanceof Player rider)) return;

        // Player takes over — steer by rider yaw
        this.setYRot(rider.getYRot());
        this.yRotO = this.getYRot();

        boolean onWater = isOnOrInWater();
        double speed   = onWater ? WATER_SPEED : LAND_SPEED;

        float forward = rider.zza;
        float strafe  = rider.xxa;

        double yawRad = Math.toRadians(this.getYRot());
        double vx = (-Math.sin(yawRad) * forward + -Math.cos(yawRad) * strafe) * speed;
        double vz = ( Math.cos(yawRad) * forward +  Math.sin(yawRad) * strafe) * speed;

        // Vertical: lever overrides all other vertical physics
        double vy = this.getDeltaMovement().y;
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
            if (this.isInWater()) {
                vy += 0.06;
                vy  = Math.min(vy, 0.15);
            } else {
                vy = Math.max(vy - 0.02, -0.05);
            }
        } else {
            vy = this.onGround() ? 0 : Math.max(vy - 0.08, -0.5);
        }

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        double friction = onWater ? 0.85 : 0.91;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 1.0, friction));

        this.resetFallDistance();
        rider.resetFallDistance();

        // Propeller bubble particles when moving on water
        boolean moving = Math.abs(forward) > 0.01 || Math.abs(strafe) > 0.01;
        if (onWater && moving && level() instanceof ServerLevel serverLevel) {
            spawnPropellerParticles(serverLevel);
        }
    }

    /**
     * Autopilot tick: wander randomly by periodically picking a new target yaw,
     * smoothly rotating toward it, and always moving forward.
     */
    private void tickAutopilot() {
        boolean onWater = isOnOrInWater();

        // Pick a new random target direction every 40–100 ticks (2–5 seconds)
        if (autopilotTurnTimer <= 0) {
            float delta = (float)(this.random.nextFloat() * 120f - 60f); // -60 to +60 degrees
            autopilotTargetYaw = this.getYRot() + delta;
            autopilotTurnTimer = 40 + this.random.nextInt(61);
        }
        autopilotTurnTimer--;

        // Smoothly rotate toward target yaw (max 2 degrees/tick)
        float currentYaw = this.getYRot();
        float diff = autopilotTargetYaw - currentYaw;
        // Normalize to [-180, 180]
        while (diff > 180f)  diff -= 360f;
        while (diff < -180f) diff += 360f;
        float step = Math.max(-2f, Math.min(2f, diff));
        float newYaw = currentYaw + step;
        this.setYRot(newYaw);
        this.yRotO = newYaw;

        // Move forward at autopilot speed
        double speed = onWater ? AUTOPILOT_SPEED : LAND_SPEED;
        double yawRad = Math.toRadians(newYaw);
        double vx = -Math.sin(yawRad) * speed;
        double vz =  Math.cos(yawRad) * speed;

        double vy = this.getDeltaMovement().y;
        if (onWater) {
            if (this.isInWater()) {
                vy += 0.06;
                vy  = Math.min(vy, 0.15);
            } else {
                vy = Math.max(vy - 0.02, -0.05);
            }
        } else {
            vy = this.onGround() ? 0 : Math.max(vy - 0.08, -0.5);
        }

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        double friction = onWater ? 0.85 : 0.91;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 1.0, friction));
        this.resetFallDistance();

        // Particles
        if (onWater && level() instanceof ServerLevel serverLevel) {
            spawnPropellerParticles(serverLevel);
        }
    }

    private void spawnPropellerParticles(ServerLevel serverLevel) {
        double rearYaw = Math.toRadians(this.getYRot() + 180);
        double px = this.getX() + Math.sin(rearYaw) * 0.8;
        double pz = this.getZ() - Math.cos(rearYaw) * 0.8;
        serverLevel.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP,
                px, this.getY(), pz,
                4, 0.2, 0.1, 0.2, 0.05);
        serverLevel.sendParticles(ParticleTypes.SPLASH,
                px, this.getY() + 0.1, pz,
                3, 0.3, 0.05, 0.3, 0.1);
    }

    /** Idle physics: buoyancy on water, gravity on land — no horizontal movement. */
    private void applyIdlePhysics() {
        boolean onWater = isOnOrInWater();
        double vy = this.getDeltaMovement().y;
        if (onWater) {
            if (this.isInWater()) {
                vy += 0.06;
                vy  = Math.min(vy, 0.15);
            } else {
                vy = Math.max(vy - 0.02, -0.05);
            }
        } else {
            vy = this.onGround() ? 0 : Math.max(vy - 0.08, -0.5);
        }
        double friction = onWater ? 0.85 : 0.91;
        this.setDeltaMovement(
            this.getDeltaMovement().x * friction,
            vy,
            this.getDeltaMovement().z * friction
        );
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.resetFallDistance();
    }

    /**
     * Interact logic:
     *  - Sneak + rechtsklik met hendel/autopilot → vastzetten (ook terwijl gemount)
     *  - Sneak + rechtsklik lege hand           → boot oppakken
     *  - Rechtsklik lege hand (niet gemount)    → instappen
     */
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

            // Sneak + lege hand: boot oppakken
            if (held.isEmpty()) {
                if (!level().isClientSide()) {
                    player.addItem(new ItemStack(BootMod.MOTORBOAT.get()));
                    if (hasLever()) player.addItem(new ItemStack(BootMod.BOAT_LEVER.get()));
                    if (hasAutopilot()) player.addItem(new ItemStack(BootMod.AUTOPILOT.get()));
                    this.discard();
                }
                return InteractionResult.sidedSuccess(level().isClientSide());
            }
        }

        // Gewone rechtsklik met lege hand: instappen
        if (held.isEmpty() && this.getPassengers().isEmpty()) {
            if (!level().isClientSide()) {
                player.startRiding(this, true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** Maakt de boot fysiek solid zodat spelers er niet doorheen vallen. */
    @Override
    public boolean canBeCollidedWith() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // Plaats speler bovenop de boot zodat ze er niet doorheen vallen
        return this.position().add(0, this.getBbHeight() + 0.1, 0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setHasLever(tag.getBoolean("HasLever"));
        setAutopilot(tag.getBoolean("HasAutopilot"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("HasLever", hasLever());
        tag.putBoolean("HasAutopilot", hasAutopilot());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 4096;
    }
}
