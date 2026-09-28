package com.yami.yamidelight.head;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;

/**
 * The wooden sword this mod hands out for testing: one hit takes a maid's head, plays her model's death
 * animation, and a moment later she falls over.
 *
 * <p>It deliberately deals no damage - the hit is cancelled and the head is what kills her - so a model
 * that changes form at low health can be tested without ever reaching that state.
 */
public final class MaidHeadTestSword extends SwordItem {
    public MaidHeadTestSword(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.yamidelight.test_sword.take").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.yamidelight.test_sword.harmless").withStyle(ChatFormatting.DARK_GRAY));
    }
}
