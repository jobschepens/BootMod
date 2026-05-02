package com.boardmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class MotorboatRenderer extends EntityRenderer<MotorboatEntity> {

    public MotorboatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(MotorboatEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // Rotate to face entity yaw and lay flat on water
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));

        // Wider and longer than the boards to look like a boat
        poseStack.scale(2.2f, 2.2f, 0.15f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(BootMod.MOTORBOAT.get()),
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.level(),
                (int) entity.getId()
        );

        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MotorboatEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(BootMod.MODID, "textures/item/motorboat.png");
    }
}
