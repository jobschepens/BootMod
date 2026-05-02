package com.boardmod;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(BootMod.MODID)
public class BootMod {
    public static final String MODID = "bootmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MODID);

    // Legacy stubs - keep registered so existing world data doesn't break on login
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of());
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);
    public static final DeferredItem<Item> RUBIN = ITEMS.registerSimpleItem("rubin", new Item.Properties());

    public static final DeferredItem<SnowboardItem> SNOWBOARD = ITEMS.registerItem("snowboard",
            SnowboardItem::new, new Item.Properties());

    public static final DeferredHolder<EntityType<?>, EntityType<SnowboardEntity>> SNOWBOARD_ENTITY =
            ENTITY_TYPES.register("snowboard", () -> EntityType.Builder
                    .<SnowboardEntity>of(SnowboardEntity::new, MobCategory.MISC)
                    .sized(1.0f, 0.4f)
                    .clientTrackingRange(10)
                    .build("bootmod:snowboard"));

    public static final DeferredItem<SkateboardItem> SKATEBOARD = ITEMS.registerItem("skateboard",
            SkateboardItem::new, new Item.Properties());

    public static final DeferredHolder<EntityType<?>, EntityType<SkateboardEntity>> SKATEBOARD_ENTITY =
            ENTITY_TYPES.register("skateboard", () -> EntityType.Builder
                    .<SkateboardEntity>of(SkateboardEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.3f)
                    .clientTrackingRange(10)
                    .build("bootmod:skateboard"));

    public static final DeferredItem<MotorboatItem> MOTORBOAT = ITEMS.registerItem("motorboat",
            MotorboatItem::new, new Item.Properties());

    public static final DeferredHolder<EntityType<?>, EntityType<MotorboatEntity>> MOTORBOAT_ENTITY =
            ENTITY_TYPES.register("motorboat", () -> EntityType.Builder
                    .<MotorboatEntity>of(MotorboatEntity::new, MobCategory.MISC)
                    .sized(1.5f, 0.6f)
                    .clientTrackingRange(10)
                    .build("bootmod:motorboat"));

    public static final DeferredItem<SteeringWheelItem> STEERING_WHEEL = ITEMS.registerItem("steering_wheel",
            SteeringWheelItem::new, new Item.Properties());

    public static final DeferredHolder<EntityType<?>, EntityType<RaftEntity>> RAFT_ENTITY =
            ENTITY_TYPES.register("raft", () -> EntityType.Builder
                    .<RaftEntity>of(RaftEntity::new, MobCategory.MISC)
                    .sized(2.0f, 0.75f)
                    .clientTrackingRange(10)
                    .build("bootmod:raft"));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BOARDMOD_TAB = CREATIVE_MODE_TABS.register("boardmod_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bootmod"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> SNOWBOARD.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SNOWBOARD.get());
                output.accept(SKATEBOARD.get());
                output.accept(MOTORBOAT.get());
                output.accept(STEERING_WHEEL.get());
            }).build());

    public BootMod(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
    }
}