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

public class SkateboardRenderer extends EntityRenderer<SkateboardEntity> {

    public SkateboardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SkateboardEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
        poseStack.scale(1.5f, 1.5f, 0.1f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(BootMod.SKATEBOARD.get()),
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
    public ResourceLocation getTextureLocation(SkateboardEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(BootMod.MODID, "textures/item/skateboard.png");
    }
}
