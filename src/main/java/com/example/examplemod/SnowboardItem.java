package com.example.examplemod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SnowboardItem extends Item {

    private static final String NBT_KEY = "snowboarding";

    public SnowboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            boolean current = player.getPersistentData().getBoolean(NBT_KEY);
            boolean next = !current;
            player.getPersistentData().putBoolean(NBT_KEY, next);

            if (next) {
                player.displayClientMessage(
                        Component.translatable("item.bootmod.snowboard.equipped"), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("item.bootmod.snowboard.unequipped"), true);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
