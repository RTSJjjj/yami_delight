package com.yami.yamidelight.feast;

import com.yami.yamidelight.head.MaidHeadContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** The brain bowl's two unordered ingredients, with the complete head carried into the result. */
public final class FoxHeadCraftingRecipe extends CustomRecipe {
    public FoxHeadCraftingRecipe(CraftingBookCategory category) { super(category); }
    @Override public boolean matches(CraftingInput input, Level level) {
        int heads = 0, other = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(MaidHeadContent.EMPTY_WINEFOX_HEAD.get())) heads++;
            else if (stack.is(Items.BOWL)) other++;
            else return false;
        }
        return heads == 1 && other == 1;
    }
    @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        for (int slot = 0; slot < input.size(); slot++) if (input.getItem(slot).is(MaidHeadContent.EMPTY_WINEFOX_HEAD.get()))
            return FoxHeadFoodData.create(FeastContent.FOX_BRAIN_BOWL_ITEM.get(), input.getItem(slot), registries);
        return ItemStack.EMPTY;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() {
        return FeastContent.FOX_BRAIN_BOWL_CRAFTING.get();
    }
}
