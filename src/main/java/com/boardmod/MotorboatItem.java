package com.boardmod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MotorboatItem extends Item {

    public MotorboatItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            MotorboatEntity entity = BootMod.MOTORBOAT_ENTITY.get().create(level);
            if (entity != null) {
                Vec3 pos = player.position();
                entity.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0);
                level.addFreshEntity(entity);
                player.startRiding(entity, true);

                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
