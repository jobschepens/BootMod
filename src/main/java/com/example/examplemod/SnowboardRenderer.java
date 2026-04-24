package com.example.examplemod;

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

public class SnowboardRenderer extends EntityRenderer<SnowboardEntity> {

    public SnowboardRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(SnowboardEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();

        // Rotate to face the entity's yaw
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));

        // Tilt flat on the ground: rotate -90° around X so item faces up
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));

        // Scale up a bit so it looks like a board under feet
        poseStack.scale(1.5f, 1.5f, 0.1f);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(BootMod.SNOWBOARD.get()),
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
    public ResourceLocation getTextureLocation(SnowboardEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(BootMod.MODID, "textures/item/snowboard.png");
    }
}
