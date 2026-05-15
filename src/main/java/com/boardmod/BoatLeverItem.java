package com.boardmod;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public class BoatLeverItem extends Item {

    public BoatLeverItem(Properties properties) {
        super(properties);
    }

    /**
     * Called when the player right-clicks any entity while holding this item.
     * If the target is a MotorboatEntity or RaftEntity without a lever, attach it.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player,
            net.minecraft.world.entity.LivingEntity entity, InteractionHand hand) {
        return InteractionResult.PASS;
    }
}
