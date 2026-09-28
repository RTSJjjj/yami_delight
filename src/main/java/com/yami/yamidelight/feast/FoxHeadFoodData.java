package com.yami.yamidelight.feast;

import com.yami.yamidelight.head.MaidHeadContent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** One complete source head survives crafting, serving, placement and recovery. */
public final class FoxHeadFoodData {
    private static final String HEAD = "FoxFoodHead";
    private static final String STAGE = "FoxFoodStage";
    private FoxHeadFoodData() {}

    public static ItemStack create(Item item, ItemStack source, HolderLookup.Provider registries) {
        ItemStack result = new ItemStack(item);
        ItemStack head = sourceHead(source, registries);
        CompoundTag tag = new CompoundTag();
        tag.put(HEAD, head.save(registries));
        result.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        result.set(MaidHeadContent.MAID_HEAD_DATA.get(), MaidHeadContent.dataOf(head));
        return result;
    }

    public static ItemStack sourceHead(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains(HEAD)) {
            ItemStack saved = ItemStack.parseOptional(registries, tag.getCompound(HEAD));
            if (!saved.isEmpty()) return saved;
        }
        if (stack.is(MaidHeadContent.EMPTY_WINEFOX_HEAD.get())) return stack.copyWithCount(1);
        ItemStack fallback = new ItemStack(MaidHeadContent.EMPTY_WINEFOX_HEAD.get());
        fallback.set(MaidHeadContent.MAID_HEAD_DATA.get(), MaidHeadContent.dataOf(stack));
        return fallback;
    }

    public static int stage(ItemStack stack) {
        return Math.clamp(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(STAGE), 0, 4);
    }

    public static void stage(ItemStack stack, int stage) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(STAGE, Math.clamp(stage, 0, 4));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
