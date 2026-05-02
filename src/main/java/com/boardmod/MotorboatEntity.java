package com.boardmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MotorboatEntity extends Entity {

    // 12 blocks/sec = 0.6 blocks/tick on water
    private static final double WATER_SPEED = 0.6;
    // Nearly stuck on land
    private static final double LAND_SPEED  = 0.05;

    public MotorboatEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

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

        // No rider → drop item and remove
        if (this.getPassengers().isEmpty()) {
            this.spawnAtLocation(new ItemStack(BootMod.MOTORBOAT.get()));
            this.discard();
            return;
        }

        Entity passenger = this.getPassengers().get(0);
        if (!(passenger instanceof Player rider)) return;

        // Steer by rider yaw
        this.setYRot(rider.getYRot());
        this.yRotO = this.getYRot();

        boolean onWater = isOnOrInWater();
        double speed   = onWater ? WATER_SPEED : LAND_SPEED;

        float forward = rider.zza;
        float strafe  = rider.xxa;

        double yawRad = Math.toRadians(this.getYRot());
        double vx = (-Math.sin(yawRad) * forward + -Math.cos(yawRad) * strafe) * speed;
        double vz = ( Math.cos(yawRad) * forward +  Math.sin(yawRad) * strafe) * speed;

        // Vertical: buoyancy in water, gravity on land
        double vy = this.getDeltaMovement().y;
        if (onWater) {
            if (this.isInWater()) {
                // Submerged: buoyancy pushes up toward surface
                vy += 0.06;
                vy  = Math.min(vy, 0.15);
            } else {
                // On water surface: gentle settling
                vy = Math.max(vy - 0.02, -0.05);
            }
        } else {
            // Gravity on land
            vy = this.onGround() ? 0 : Math.max(vy - 0.08, -0.5);
        }

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        // Friction (water is smoother)
        double friction = onWater ? 0.85 : 0.91;
        this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 1.0, friction));

        this.resetFallDistance();
        rider.resetFallDistance();

        // Propeller bubble particles when moving on water
        boolean moving = Math.abs(forward) > 0.01 || Math.abs(strafe) > 0.01;
        if (onWater && moving && level() instanceof ServerLevel serverLevel) {
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
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return this.position().add(1.5, 0, 0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) {
        return dist < 4096;
    }
}
