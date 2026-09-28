package com.yami.yamidelight.feast;

import com.mojang.serialization.MapCodec;
import com.yami.yamidelight.head.MaidHeadContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** A normal book-visible 3x3 shaped recipe that carries the ingredient head into the lantern. */
public final class FoxLanternRecipe extends ShapedRecipe {
    private FoxLanternRecipe(ShapedRecipe base) {
        super(base.getGroup(),base.category(),base.pattern,base.getResultItem(RegistryAccess.EMPTY).copy(),base.showNotification());
    }

    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int slot=0;slot<input.size();slot++) {
            ItemStack head=input.getItem(slot);
            if (head.is(MaidHeadContent.EMPTY_WINEFOX_HEAD.get()))
                return FoxHeadFoodData.create(FeastContent.FOX_LANTERN_ITEM.get(),head,registries);
        }
        return ItemStack.EMPTY;
    }
    @Override public RecipeSerializer<?> getSerializer() { return FeastContent.FOX_LANTERN_CRAFTING.get(); }

    public static final class Serializer implements RecipeSerializer<FoxLanternRecipe> {
        private static final ShapedRecipe.Serializer VANILLA = new ShapedRecipe.Serializer();
        private static final MapCodec<FoxLanternRecipe> CODEC = VANILLA.codec().xmap(FoxLanternRecipe::new,recipe->recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf,FoxLanternRecipe> STREAM_CODEC =
                VANILLA.streamCodec().map(FoxLanternRecipe::new,recipe->recipe);
        @Override public MapCodec<FoxLanternRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf,FoxLanternRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
