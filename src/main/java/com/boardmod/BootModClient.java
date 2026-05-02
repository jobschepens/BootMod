package com.boardmod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = BootMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BootMod.MODID, value = Dist.CLIENT)
public class BootModClient {
    public BootModClient(ModContainer container) { }

    @SubscribeEvent
    static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BootMod.SNOWBOARD_ENTITY.get(), SnowboardRenderer::new);
        event.registerEntityRenderer(BootMod.SKATEBOARD_ENTITY.get(), SkateboardRenderer::new);
        event.registerEntityRenderer(BootMod.MOTORBOAT_ENTITY.get(), MotorboatRenderer::new);
    }
}