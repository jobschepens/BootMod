package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SnowboardEntity extends Entity {

    // 10 blocks/sec = 0.5 blocks/tick
    private static final double SNOW_SPEED = 0.5;
    private static final double NORMAL_SPEED = 0.1;

    public SnowboardEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = false;
        this.setMaxUpStep(0.6f);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // no synced data needed
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) return;

        // If no rider → remove entity and drop the snowboard item
        if (this.getPassengers().isEmpty()) {
            if (!level().isClientSide()) {
                this.spawnAtLocation(new ItemStack(BootMod.SNOWBOARD.get()));
            }
            this.discard();
            return;
        }

        // Gravity
        if (!this.onGround()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.08, 0));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(0.91));
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 rawInput) {
        float forward = rider.xxa != 0 ? rider.xxa * 0.5f : rider.zza;
        return new Vec3(0.0, 0.0, forward > 0 ? 1.0 : (forward < 0 ? -0.5 : 0.0));
    }

    @Override
    protected double getRiddenSpeed(Player rider) {
        return isOnSnow() ? SNOW_SPEED : NORMAL_SPEED;
    }

    @Override
    protected void tickRidden(Player rider, Vec3 input) {
        // Steer by rider's yaw
        this.setYRot(rider.getYRot());
        this.yRotO = this.getYRot();
        this.setXRot(rider.getXRot() * 0.5f);

        double speed = getRiddenSpeed(rider);
        double vx = -Math.sin(Math.toRadians(this.getYRot())) * input.z * speed;
        double vz =  Math.cos(Math.toRadians(this.getYRot())) * input.z * speed;

        double vy = this.getDeltaMovement().y;
        if (this.onGround()) vy = 0;
        vy -= 0.08; // gravity
        vy = Math.max(vy, -0.5);

        this.setDeltaMovement(vx, vy, vz);
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
