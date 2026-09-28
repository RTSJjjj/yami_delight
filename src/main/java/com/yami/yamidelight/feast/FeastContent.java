package com.yami.yamidelight.feast;

import com.yami.yamidelight.YamiDelight;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
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
import vectorwing.farmersdelight.common.item.ConsumableItem;
import vectorwing.farmersdelight.common.registry.ModEffects;

public final class FeastContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(YamiDelight.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(YamiDelight.MODID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, YamiDelight.MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, YamiDelight.MODID);
    public static final DeferredBlock<FoxHeadFoodBlock> FOX_BRAIN_BOWL = headFood("fox_brain_bowl", 0);
    public static final DeferredBlock<FoxHeadFoodBlock> ROASTED_AGED_FOX_BRAIN = headFood("roasted_aged_fox_brain", 0);
    public static final DeferredBlock<FoxHeadFoodBlock> FOX_LANTERN = headFood("fox_lantern", 15);
    public static final DeferredItem<BlockItem> FOX_BRAIN_BOWL_ITEM = ITEMS.register("fox_brain_bowl",
            () -> new BlockItem(FOX_BRAIN_BOWL.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> ROASTED_AGED_FOX_BRAIN_ITEM = ITEMS.register("roasted_aged_fox_brain",
            () -> new BlockItem(ROASTED_AGED_FOX_BRAIN.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> FOX_LANTERN_ITEM = ITEMS.register("fox_lantern",
            () -> new BlockItem(FOX_LANTERN.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FoxHeadFoodBlockEntity>> FOX_HEAD_FOOD_BE = ENTITIES.register("fox_head_food",
            () -> BlockEntityType.Builder.of(FoxHeadFoodBlockEntity::new, FOX_BRAIN_BOWL.get(), ROASTED_AGED_FOX_BRAIN.get(), FOX_LANTERN.get()).build(null));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FoxHeadCraftingRecipe>> FOX_BRAIN_BOWL_CRAFTING =
            RECIPE_SERIALIZERS.register("fox_brain_bowl", () -> new net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer<>(FoxHeadCraftingRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FoxLanternRecipe>> FOX_LANTERN_CRAFTING =
            RECIPE_SERIALIZERS.register("fox_lantern", FoxLanternRecipe.Serializer::new);

    private static DeferredBlock<FoxHeadFoodBlock> headFood(String id, int light) {
        return BLOCKS.register(id, () -> new FoxHeadFoodBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                .strength(.5F).sound(SoundType.WOOD).noOcclusion().lightLevel(state -> light).pushReaction(PushReaction.DESTROY)));
    }
    public static final DeferredItem<Item> RAW_STUFFED_WINEFOX = ITEMS.register("raw_stuffed_winefox", () -> new Item(new Item.Properties()));
    public static final DeferredItem<ConsumableItem> FOX_OFFAL_FRIED_RICE = meal("fox_offal_fried_rice", 10, .6F);
    public static final DeferredItem<ConsumableItem> FOX_CARROT_RICE = meal("fox_carrot_rice", 10, .6F);
    public static final DeferredItem<ConsumableItem> FOX_UTERUS_RICE = meal("fox_uterus_rice", 12, .8F);
    public static final DeferredItem<ConsumableItem> FOX_INTIMATE_RICE = meal("fox_intimate_rice", 12, .8F);
    public static final DeferredItem<ConsumableItem> STUFFED_WINEFOX_HAM_PLATE = meal("stuffed_winefox_ham_plate", 14, .8F);
    public static final DeferredItem<ConsumableItem> STUFFED_WINEFOX_PLATE = meal("stuffed_winefox_plate", 12, .8F);
    /** Every skewer restores seven hunger and seven actual saturation points, then gives its stick back. */
    public static final DeferredItem<ConsumableItem> SEA_LAND_WIND_WINEFOX_LEG_SKEWER = skewer("sea_land_wind_winefox_leg_skewer");
    public static final DeferredItem<ConsumableItem> SEA_LAND_WIND_WINEFOX_ARM_SKEWER = skewer("sea_land_wind_winefox_arm_skewer");
    public static final DeferredItem<ConsumableItem> SEA_LAND_WIND_WINEFOX_SKEWER = skewer("sea_land_wind_winefox_skewer");
    public static final DeferredItem<ConsumableItem> SEA_LAND_WIND_FRUIT_WINEFOX_HOLE_SKEWER = comfortSkewer("sea_land_wind_fruit_winefox_hole_skewer");
    public static final DeferredBlock<StuffedWinefoxBlock> STUFFED_WINEFOX = BLOCKS.register("stuffed_winefox",
            () -> new StuffedWinefoxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> STUFFED_WINEFOX_ITEM = ITEMS.register("stuffed_winefox",
            () -> new BlockItem(STUFFED_WINEFOX.get(), new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StuffedWinefoxBlockEntity>> STUFFED_WINEFOX_BE = ENTITIES.register("stuffed_winefox",
            () -> BlockEntityType.Builder.of(StuffedWinefoxBlockEntity::new, STUFFED_WINEFOX.get()).build(null));
    public static final DeferredBlock<SeaLandWindWinefoxBlock> SEA_LAND_WIND_WINEFOX = BLOCKS.register("sea_land_wind_winefox",
            () -> new SeaLandWindWinefoxBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN)
                    .strength(.5F).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> SEA_LAND_WIND_WINEFOX_ITEM = ITEMS.register("sea_land_wind_winefox",
            () -> new BlockItem(SEA_LAND_WIND_WINEFOX.get(), new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeaLandWindWinefoxBlockEntity>> SEA_LAND_WIND_WINEFOX_BE = ENTITIES.register("sea_land_wind_winefox",
            () -> BlockEntityType.Builder.of(SeaLandWindWinefoxBlockEntity::new, SEA_LAND_WIND_WINEFOX.get()).build(null));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SeaLandWindWinefoxCookingRecipe>> SEA_LAND_WIND_WINEFOX_COOKING =
            RECIPE_SERIALIZERS.register("sea_land_wind_winefox_cooking", SeaLandWindWinefoxCookingRecipe.Serializer::new);

    private static DeferredItem<ConsumableItem> meal(String id, int nutrition, float saturation) {
        return ITEMS.register(id, () -> new ConsumableItem(new Item.Properties().stacksTo(16)
                .craftRemainder(Items.BOWL).food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())));
    }

    private static DeferredItem<ConsumableItem> skewer(String id) {
        return ITEMS.register(id, () -> new ConsumableItem(new Item.Properties().stacksTo(64)
                .craftRemainder(Items.STICK).food(new FoodProperties.Builder().nutrition(7).saturationModifier(.5F).fast().build())));
    }

    private static DeferredItem<ConsumableItem> comfortSkewer(String id) {
        return ITEMS.<ConsumableItem>register(id, () -> new ComfortSkewerItem(new Item.Properties().stacksTo(64)
                .craftRemainder(Items.STICK).food(new FoodProperties.Builder().nutrition(7).saturationModifier(.5F).fast().build())));
    }

    /** Farmer's Delight's consumable item already handles returning the craft remainder after eating. */
    private static final class ComfortSkewerItem extends ConsumableItem {
        private ComfortSkewerItem(Item.Properties properties) {
            super(properties);
        }

        @Override
        public void affectConsumer(ItemStack stack, Level level, LivingEntity consumer) {
            consumer.addEffect(new MobEffectInstance(ModEffects.COMFORT, 20 * 150));
        }
    }

    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
    }
    private FeastContent() {}
}
