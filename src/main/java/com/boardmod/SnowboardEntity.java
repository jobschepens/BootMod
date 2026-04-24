package com.boardmod;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

public class SnowboardEntity extends Entity {

    // 10 blocks/sec = 0.5 blocks/tick
    private static final double SNOW_SPEED  = 0.5;
    private static final double NORMAL_SPEED = 0.1;

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

    public SnowboardEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // no synced data needed
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) return;

        // No rider → drop item and remove entity
        if (this.getPassengers().isEmpty()) {
            this.spawnAtLocation(new ItemStack(BootMod.SNOWBOARD.get()));
            this.discard();
            return;
        }

        Entity passenger = this.getPassengers().get(0);
        if (!(passenger instanceof Player rider)) return;

        // Steer by rider yaw
        this.setYRot(rider.getYRot());
        this.yRotO = this.getYRot();

        // Read movement input from the rider
        float forward = rider.zza;  // 1 = W, -1 = S
        float strafe  = rider.xxa;  // 1 = A, -1 = D

        double speed = isOnSnow() ? SNOW_SPEED : NORMAL_SPEED;
        double yawRad = Math.toRadians(this.getYRot());

        double vx = (-Math.sin(yawRad) * forward + -Math.cos(yawRad) * strafe) * speed;
        double vz = ( Math.cos(yawRad) * forward +  Math.sin(yawRad) * strafe) * speed;

        // Gravity & jumping
        double vy = this.getDeltaMovement().y;
        if (this.onGround()) {
            vy = 0;
            // Jump: Space bar sets jumping flag on the player
            if (isJumping(rider)) {
                vy = 0.42;
            }
        } else {
            vy -= 0.08;
        }
        vy = Math.max(vy, -0.5);

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        // Friction
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.91, 1.0, 0.91));

        // No fall damage for passengers
        this.resetFallDistance();
        rider.resetFallDistance();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Entity itself is indestructible (player dismounts via Shift)
        return false;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(net.minecraft.world.entity.LivingEntity passenger) {
        return this.position().add(1.2, 0, 0);
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().isEmpty() && passenger instanceof Player;
    }

    private boolean isOnSnow() {
        BlockPos pos = this.blockPosition();
        return isSnowBlock(level().getBlockState(pos))
                || isSnowBlock(level().getBlockState(pos.below()));
    }

    private static boolean isSnowBlock(BlockState state) {
        return state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.SNOW)
                || state.is(Blocks.POWDER_SNOW);
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

