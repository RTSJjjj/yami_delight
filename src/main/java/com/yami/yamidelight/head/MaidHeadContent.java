package com.yami.yamidelight.head;

import com.yami.yamidelight.YamiDelight;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The one item and the one block this mod adds: {@code yamidelight:winefox_head}.
 *
 * <p>The item is a {@link BlockItem} because a head is placed once and picked back up as itself, and the
 * model it draws is carried in a data component instead of in the item's damage value or custom NBT, so
 * a stack of heads from two different maids can never merge into one.
 */
public final class MaidHeadContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(YamiDelight.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YamiDelight.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, YamiDelight.MODID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, YamiDelight.MODID);
    private static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, YamiDelight.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MaidHeadData>> MAID_HEAD_DATA =
            DATA_COMPONENTS.register("winefox_head", () -> DataComponentType.<MaidHeadData>builder()
                    .persistent(MaidHeadData.CODEC)
                    .networkSynchronized(MaidHeadData.STREAM_CODEC)
                    .build());

    public static final DeferredBlock<MaidHeadBlock> MAID_HEAD = BLOCKS.register(
            "winefox_head",
            () -> new MaidHeadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOL)
                    .strength(0.4F)
                    .sound(SoundType.WOOL)
                    .noOcclusion()
                    // Pushing a trophy around would leave the head in a cell it was never mounted on.
                    .pushReaction(PushReaction.DESTROY)));

    public static final DeferredBlock<MaidBodyBlock> MAID_BODY = BLOCKS.register("winefox_body",
            () -> new MaidBodyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(0.4F).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> MAID_BODY_ITEM = ITEMS.register("winefox_body",
            () -> new MaidBodyItem(MAID_BODY.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<MeatHookBlock> MEAT_HOOK = BLOCKS.register("meat_hook",
            () -> new MeatHookBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(1.5F).sound(SoundType.CHAIN).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> MEAT_HOOK_ITEM = ITEMS.register("meat_hook",
            () -> new BlockItem(MEAT_HOOK.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeatHookBlockEntity>> MEAT_HOOK_BE =
            BLOCK_ENTITIES.register("meat_hook",
                    () -> BlockEntityType.Builder.of(MeatHookBlockEntity::new, MEAT_HOOK.get()).build(null));
    public static final DeferredBlock<DissectionTableBlock> DISSECTION_TABLE = BLOCKS.register("dissection_table",
            () -> new DissectionTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
                    .strength(2.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> DISSECTION_TABLE_ITEM = ITEMS.register("dissection_table",
            () -> new BlockItem(DISSECTION_TABLE.get(), new Item.Properties()));
    // Drops made while preparing a wine fox on the dissection table.  They are deliberately plain
    // items for now: this keeps the table recipe-independent and leaves food behaviour to a later pass.
    public static final DeferredItem<Item> WINE_FOX_TAIL = part("wine_fox_tail");
    public static final DeferredItem<Item> WINE_FOX_EAR = part("wine_fox_ear");
    public static final DeferredItem<Item> WINE_FOX_EYE = part("wine_fox_eye");
    public static final DeferredItem<Item> WINE_FOX_BRAIN = part("wine_fox_brain");
    public static final DeferredItem<Item> WINE_FOX_TONGUE = part("wine_fox_tongue");
    public static final DeferredItem<Item> WINE_FOX_HEART = part("wine_fox_heart");
    public static final DeferredItem<Item> WINE_FOX_LUNG = part("wine_fox_lung");
    public static final DeferredItem<Item> WINE_FOX_DIAPHRAGM = part("wine_fox_diaphragm");
    public static final DeferredItem<Item> WINE_FOX_LIVER = part("wine_fox_liver");
    public static final DeferredItem<Item> WINE_FOX_STOMACH = part("wine_fox_stomach");
    public static final DeferredItem<Item> WINE_FOX_PANCREAS = part("wine_fox_pancreas");
    public static final DeferredItem<Item> WINE_FOX_INTESTINE = part("wine_fox_intestine");
    public static final DeferredItem<Item> WINE_FOX_KIDNEY = part("wine_fox_kidney");
    public static final DeferredItem<Item> WINE_FOX_UTERUS = part("wine_fox_uterus");
    public static final DeferredItem<Item> WINE_FOX_ARM = part("wine_fox_arm");
    public static final DeferredItem<Item> WINE_FOX_FOOT = part("wine_fox_foot");
    public static final DeferredItem<Item> WINE_FOX_HAM = part("wine_fox_ham");
    public static final DeferredItem<Item> WINE_FOX_LOIN = part("wine_fox_loin");
    public static final DeferredItem<Item> WINE_FOX_RIB_MEAT = part("wine_fox_rib_meat");
    public static final DeferredItem<Item> WINE_FOX_WAIST_MEAT = part("wine_fox_waist_meat");
    public static final DeferredItem<Item> WINE_FOX_BREAST = part("wine_fox_breast");
    public static final DeferredItem<Item> WINE_FOX_HONEY_HOLE = part("wine_fox_honey_hole");
    public static final DeferredItem<Item> WINE_FOX_WAIST_SLICE = part("wine_fox_waist_slice");
    public static final DeferredItem<Item> WINE_FOX_RIB_MEAT_SLICE = part("wine_fox_rib_meat_slice");
    public static final DeferredItem<Item> WINE_FOX_LOIN_SLICE = part("wine_fox_loin_slice");
    public static final DeferredItem<Item> WINE_FOX_SKINNED_TAIL = part("wine_fox_skinned_tail");
    public static final DeferredItem<Item> WINE_FOX_TAIL_SEGMENT = part("wine_fox_tail_segment");
    public static final DeferredItem<Item> ZASUI = part("zasui");
    public static final DeferredItem<Item> COOKED_WINE_FOX_WAIST_MEAT = part("cooked_wine_fox_waist_meat");
    public static final DeferredItem<Item> COOKED_WINE_FOX_RIB_MEAT = part("cooked_wine_fox_rib_meat");
    public static final DeferredItem<Item> COOKED_WINE_FOX_LOIN = part("cooked_wine_fox_loin");
    public static final DeferredItem<Item> COOKED_WINE_FOX_WAIST_SLICE = part("cooked_wine_fox_waist_slice");
    public static final DeferredItem<Item> COOKED_WINE_FOX_RIB_MEAT_SLICE = part("cooked_wine_fox_rib_meat_slice");
    public static final DeferredItem<Item> COOKED_WINE_FOX_LOIN_SLICE = part("cooked_wine_fox_loin_slice");
    public static final DeferredItem<Item> COOKED_WINE_FOX_DIAPHRAGM = part("cooked_wine_fox_diaphragm");
    public static final DeferredItem<Item> COOKED_WINE_FOX_TAIL_SEGMENT = part("cooked_wine_fox_tail_segment");
    public static final DeferredItem<Item> PROCESSED_MAID = part("processed_winefox");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DissectionTableBlockEntity>> DISSECTION_TABLE_BE =
            BLOCK_ENTITIES.register("dissection_table",
                    () -> BlockEntityType.Builder.of(DissectionTableBlockEntity::new, DISSECTION_TABLE.get()).build(null));
    public static final DeferredBlock<MaidBodyBlock> MAID_REMAINS = BLOCKS.register("winefox_remains",
            () -> new MaidBodyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL)
                    .strength(0.4F).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> MAID_REMAINS_ITEM = ITEMS.register("winefox_remains",
            () -> new MaidBodyItem(MAID_REMAINS.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaidBodyBlockEntity>> MAID_BODY_BE =
            BLOCK_ENTITIES.register("winefox_body",
                    () -> BlockEntityType.Builder.of(MaidBodyBlockEntity::new, MAID_BODY.get(), MAID_REMAINS.get()).build(null));

    public static ItemStack remains(MaidHeadData data) {
        ItemStack stack = new ItemStack(MAID_REMAINS_ITEM.get());
        if (data != null && !MaidHeadData.EMPTY.equals(data)) stack.set(MAID_HEAD_DATA.get(), data);
        return stack;
    }

    public static ItemStack body(MaidHeadData data) {
        ItemStack stack = new ItemStack(MAID_BODY_ITEM.get());
        if (data != null && !MaidHeadData.EMPTY.equals(data)) stack.set(MAID_HEAD_DATA.get(), data);
        return stack;
    }

    /**
     * The processed-maid icon is intentionally the chulihao item texture, but its stack still owns
     * the original maid appearance and dissection state so it can be mounted again.
     */
    public static ItemStack processedMaid(MaidHeadData data, CompoundTag interactionData) {
        ItemStack stack = new ItemStack(PROCESSED_MAID.get());
        if (data != null && !MaidHeadData.EMPTY.equals(data)) stack.set(MAID_HEAD_DATA.get(), data);
        if (!interactionData.isEmpty()) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(interactionData.copy()));
        return stack;
    }

    public static final DeferredItem<MaidHeadItem> MAID_HEAD_ITEM = ITEMS.register(
            "winefox_head",
            () -> new MaidHeadItem(MAID_HEAD.get(), new Item.Properties().stacksTo(1)));
    /** One shared recipe-facing item id for every head taken from STAGE3 or later. */
    public static final DeferredItem<MaidHeadItem> EMPTY_WINEFOX_HEAD = ITEMS.register(
            "empty_winefox_head",
            () -> new MaidHeadItem(MAID_HEAD.get(), new Item.Properties().stacksTo(1)));

    /**
     * The testing tool: a wooden sword that takes a maid's head in one hit. It deals no damage at all
     * (the attack is cancelled) so the headless maid keeps standing there to be looked at, and so a model
     * that turns into something else at low health is never reached.
     */
    public static final DeferredItem<MaidHeadTestSword> TEST_SWORD = ITEMS.register(
            "winefox_head_test_sword",
            () -> new MaidHeadTestSword(Tiers.WOOD, new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MaidHeadBlockEntity>> MAID_HEAD_BE =
            BLOCK_ENTITIES.register(
                    "winefox_head",
                    () -> BlockEntityType.Builder.of(MaidHeadBlockEntity::new, MAID_HEAD.get()).build(null));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + YamiDelight.MODID + ".main"))
                    .icon(() -> MAID_HEAD_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(MAID_HEAD_ITEM.get().getDefaultInstance());
                        output.accept(MAID_BODY_ITEM.get().getDefaultInstance());
                        output.accept(MEAT_HOOK_ITEM.get().getDefaultInstance());
                        output.accept(DISSECTION_TABLE_ITEM.get().getDefaultInstance());
                        output.accept(com.yami.yamidelight.feast.FeastContent.FOX_BRAIN_BOWL_ITEM.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.ROASTED_AGED_FOX_BRAIN_ITEM.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.FOX_LANTERN_ITEM.get());
                        output.accept(MAID_REMAINS_ITEM.get().getDefaultInstance());
                        output.accept(TEST_SWORD.get().getDefaultInstance());
                        output.accept(WINE_FOX_TAIL.get());
                        output.accept(WINE_FOX_EAR.get());
                        output.accept(WINE_FOX_EYE.get());
                        output.accept(WINE_FOX_BRAIN.get());
                        output.accept(WINE_FOX_TONGUE.get());
                        output.accept(WINE_FOX_HEART.get());
                        output.accept(WINE_FOX_LUNG.get());
                        output.accept(WINE_FOX_DIAPHRAGM.get());
                        output.accept(WINE_FOX_LIVER.get());
                        output.accept(WINE_FOX_STOMACH.get());
                        output.accept(WINE_FOX_PANCREAS.get());
                        output.accept(WINE_FOX_INTESTINE.get());
                        output.accept(WINE_FOX_KIDNEY.get());
                        output.accept(WINE_FOX_UTERUS.get());
                        output.accept(WINE_FOX_ARM.get());
                        output.accept(WINE_FOX_FOOT.get());
                        output.accept(WINE_FOX_HAM.get());
                        output.accept(WINE_FOX_LOIN.get());
                        output.accept(WINE_FOX_RIB_MEAT.get());
                        output.accept(WINE_FOX_WAIST_MEAT.get());
                        output.accept(WINE_FOX_BREAST.get());
                        output.accept(WINE_FOX_HONEY_HOLE.get());
                        output.accept(WINE_FOX_WAIST_SLICE.get());
                        output.accept(WINE_FOX_RIB_MEAT_SLICE.get());
                        output.accept(WINE_FOX_LOIN_SLICE.get());
                        output.accept(WINE_FOX_SKINNED_TAIL.get());
                        output.accept(WINE_FOX_TAIL_SEGMENT.get());
                        output.accept(ZASUI.get());
                        output.accept(COOKED_WINE_FOX_WAIST_MEAT.get());
                        output.accept(COOKED_WINE_FOX_RIB_MEAT.get());
                        output.accept(COOKED_WINE_FOX_LOIN.get());
                        output.accept(COOKED_WINE_FOX_WAIST_SLICE.get());
                        output.accept(COOKED_WINE_FOX_RIB_MEAT_SLICE.get());
                        output.accept(COOKED_WINE_FOX_LOIN_SLICE.get());
                        output.accept(COOKED_WINE_FOX_DIAPHRAGM.get());
                        output.accept(COOKED_WINE_FOX_TAIL_SEGMENT.get());
                        output.accept(PROCESSED_MAID.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.STUFFED_WINEFOX_ITEM.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.RAW_STUFFED_WINEFOX.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.FOX_OFFAL_FRIED_RICE.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.FOX_CARROT_RICE.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.FOX_UTERUS_RICE.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.FOX_INTIMATE_RICE.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.STUFFED_WINEFOX_HAM_PLATE.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.STUFFED_WINEFOX_PLATE.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.SEA_LAND_WIND_WINEFOX_ITEM.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.SEA_LAND_WIND_WINEFOX_LEG_SKEWER.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.SEA_LAND_WIND_WINEFOX_ARM_SKEWER.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.SEA_LAND_WIND_WINEFOX_SKEWER.get());
                        output.accept(com.yami.yamidelight.feast.FeastContent.SEA_LAND_WIND_FRUIT_WINEFOX_HOLE_SKEWER.get());
                    })
                    .build());

    private MaidHeadContent() {}

    private static DeferredItem<Item> part(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties()));
    }

    /** The head of one particular maid, as an item. */
    public static ItemStack head(MaidHeadData data) {
        ItemStack stack = new ItemStack(MAID_HEAD_ITEM.get());
        if (!MaidHeadData.EMPTY.equals(data)) {
            stack.set(MAID_HEAD_DATA.get(), data);
        }
        return stack;
    }

    public static ItemStack emptyHead(MaidHeadData data, int stage) {
        ItemStack stack = new ItemStack(EMPTY_WINEFOX_HEAD.get());
        if (!MaidHeadData.EMPTY.equals(data)) stack.set(MAID_HEAD_DATA.get(), data);
        var custom = new CompoundTag();
        custom.putInt(DissectionTableBlockEntity.HEAD_DISSECTION_STAGE, Math.clamp(stage, 3, 5));
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(custom));
        return stack;
    }

    public static boolean isHeadItem(ItemStack stack) {
        return stack.is(MAID_HEAD_ITEM.get()) || stack.is(EMPTY_WINEFOX_HEAD.get());
    }

    /** The data on a head stack, or {@link MaidHeadData#EMPTY} for a head that has none. */
    public static MaidHeadData dataOf(ItemStack stack) {
        MaidHeadData data = stack.get(MAID_HEAD_DATA.get());
        return data == null ? MaidHeadData.EMPTY : data;
    }

    /** The roasted feast reuses the winefox-only AllHead3 topology, so unrelated trophy heads cannot fit it. */
    public static boolean isWinefoxHead(ItemStack stack) {
        MaidHeadData data = dataOf(stack);
        return isHeadItem(stack) && data.hasModel()
                && data.modelId().toLowerCase(java.util.Locale.ROOT).contains("winefox");
    }

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        DATA_COMPONENTS.register(bus);
        CREATIVE_TABS.register(bus);
        bus.addListener(MaidHeadNetwork::register);
        bus.addListener(MaidHangingNetwork::register);
    }
}
