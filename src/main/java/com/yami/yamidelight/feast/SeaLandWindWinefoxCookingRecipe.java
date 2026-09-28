package com.yami.yamidelight.feast;

import com.mojang.serialization.MapCodec;
import com.yami.yamidelight.head.MaidHeadContent;
import com.yami.yamidelight.head.MaidHeadData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import vectorwing.farmersdelight.client.recipebook.CookingPotRecipeBookTab;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;

/**
 * A normal Farmer's Delight cooking-pot recipe with one extra result component: the exact winefox head
 * used as an ingredient.  It keeps FD's {@link #getType()} and unordered ingredient matcher, so the pot
 * handles it as an ordinary meal while the placed feast can still restore that specific head later.
 */
public final class SeaLandWindWinefoxCookingRecipe extends CookingPotRecipe {
    /** Saved on the block item so renames and any future head components survive the cooking round trip. */
    public static final String STORED_HEAD_TAG = "SeaLandWindHead";
    public SeaLandWindWinefoxCookingRecipe(String group, CookingPotRecipeBookTab tab,
                                           NonNullList<Ingredient> ingredients, ItemStack result,
                                           ItemStack container, float experience, int cookTime) {
        super(group, tab, ingredients, result, container, experience, cookTime);
    }

    private static SeaLandWindWinefoxCookingRecipe fromBase(CookingPotRecipe recipe) {
        return new SeaLandWindWinefoxCookingRecipe(recipe.getGroup(), recipe.getRecipeBookTab(),
                NonNullList.copyOf(recipe.getIngredients()), recipe.getResultItem(RegistryAccess.EMPTY).copy(),
                recipe.getContainerOverride().copy(), recipe.getExperience(), recipe.getCookTime());
    }

    private static CookingPotRecipe toBase(SeaLandWindWinefoxCookingRecipe recipe) {
        return recipe;
    }

    @Override
    public boolean matches(RecipeWrapper input, Level level) {
        if (!super.matches(input, level)) {
            return false;
        }
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (MaidHeadContent.isWinefoxHead(input.getItem(slot))) {
                return true;
            }
        }
        // The authored AllHead3 mesh only matches the winefox head topology.  A blank or unrelated
        // trophy may satisfy Ingredient.of(item), but must never enter this recipe and render wrongly.
        return false;
    }

    @Override
    public ItemStack assemble(RecipeWrapper input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack ingredient = input.getItem(slot);
            if (MaidHeadContent.isWinefoxHead(ingredient)) {
                MaidHeadData head = MaidHeadContent.dataOf(ingredient)
                        .withFaceStyle(MaidHeadData.FACE_STYLE_SEA_LAND_WIND);
                ItemStack storedHead = ingredient.copyWithCount(1);
                storedHead.set(MaidHeadContent.MAID_HEAD_DATA.get(), head);
                CompoundTag custom = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                custom.put(STORED_HEAD_TAG, storedHead.save(registries));
                result.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                result.set(MaidHeadContent.MAID_HEAD_DATA.get(), head);
                break;
            }
        }
        return result;
    }

    /** Restores the complete head stack carried by a cooked feast item, with a data-only fallback for old stacks. */
    public static ItemStack storedHead(ItemStack feast, HolderLookup.Provider registries) {
        CompoundTag custom = feast.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (custom.contains(STORED_HEAD_TAG)) {
            return ItemStack.parseOptional(registries, custom.getCompound(STORED_HEAD_TAG));
        }
        MaidHeadData data = MaidHeadContent.dataOf(feast);
        return MaidHeadData.EMPTY.equals(data) ? ItemStack.EMPTY : MaidHeadContent.head(data);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return FeastContent.SEA_LAND_WIND_WINEFOX_COOKING.get();
    }

    /** Reuses FD's exact JSON and network codec, then upgrades the deserialised base recipe to this subtype. */
    public static final class Serializer implements RecipeSerializer<SeaLandWindWinefoxCookingRecipe> {
        private static final CookingPotRecipe.Serializer FARMERS_DELIGHT = new CookingPotRecipe.Serializer();
        private static final MapCodec<SeaLandWindWinefoxCookingRecipe> CODEC = FARMERS_DELIGHT.codec()
                .xmap(SeaLandWindWinefoxCookingRecipe::fromBase, SeaLandWindWinefoxCookingRecipe::toBase);
        private static final StreamCodec<RegistryFriendlyByteBuf, SeaLandWindWinefoxCookingRecipe> STREAM_CODEC = FARMERS_DELIGHT.streamCodec()
                .map(SeaLandWindWinefoxCookingRecipe::fromBase, SeaLandWindWinefoxCookingRecipe::toBase);

        @Override
        public MapCodec<SeaLandWindWinefoxCookingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, SeaLandWindWinefoxCookingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
