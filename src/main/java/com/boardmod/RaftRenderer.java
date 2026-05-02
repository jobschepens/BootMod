package com.boardmod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class RaftRenderer extends EntityRenderer<RaftEntity> {

    public RaftRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(RaftEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {

        List<RaftEntity.RaftBlock> blocks = entity.getRaftBlocks();
        if (!blocks.isEmpty()) {
            BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();

            poseStack.pushPose();
            for (RaftEntity.RaftBlock block : blocks) {
                poseStack.pushPose();
                // Block origin: entity position is the center of the steering-wheel block.
                // Blocks are 1x1x1 rendered from their min-corner, so shift by -0.5 in X/Z.
                poseStack.translate(block.rx() - 0.5, block.ry(), block.rz() - 0.5);
                blockRenderer.renderSingleBlock(
                        block.state(),
                        poseStack,
                        bufferSource,
                        packedLight,
                        OverlayTexture.NO_OVERLAY
                );
                poseStack.popPose();
            }
            poseStack.popPose();
        }

        // Render the steering wheel item upright at the entity origin (on top of the helm block)
        poseStack.pushPose();
        poseStack.translate(0.0, 1.0, 0.0);         // lift one block up
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw)); // face entity's direction
        poseStack.scale(0.6f, 0.6f, 0.6f);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(BootMod.STEERING_WHEEL.get()),
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
    public ResourceLocation getTextureLocation(RaftEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(BootMod.MODID, "textures/item/steering_wheel.png");
    }
}
