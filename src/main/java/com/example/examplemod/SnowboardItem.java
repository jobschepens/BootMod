package com.example.examplemod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SnowboardItem extends Item {

    public SnowboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            // Spawn snowboard entity exactly at player feet, then mount
            SnowboardEntity entity = BootMod.SNOWBOARD_ENTITY.get().create(level);
            if (entity != null) {
                Vec3 pos = player.position();
                entity.moveTo(pos.x, pos.y, pos.z, player.getYRot(), 0);
                level.addFreshEntity(entity);
                player.startRiding(entity, true);

                // Consume one snowboard from the stack
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
