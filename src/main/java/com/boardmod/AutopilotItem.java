package com.boardmod;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public class AutopilotItem extends Item {

    public AutopilotItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
            net.minecraft.world.entity.LivingEntity target, InteractionHand hand) {
        return InteractionResult.PASS;
    }
}
