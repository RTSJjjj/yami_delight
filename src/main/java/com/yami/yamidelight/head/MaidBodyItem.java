package com.yami.yamidelight.head;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;


public final class MaidBodyItem extends BlockItem {
    public MaidBodyItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof DissectionTableBlock) {
            if (context.getPlayer() == null) return net.minecraft.world.InteractionResult.FAIL;
            return DissectionTableBlock.mount(context.getLevel(), context.getClickedPos(), context.getPlayer(),
                    context.getItemInHand(), new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),
                            context.getClickedFace(), context.getClickedPos(), context.isInside()));
        }
        if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof MeatHookBlock) {
            if (context.getPlayer() == null) return net.minecraft.world.InteractionResult.FAIL;
            return MeatHookBlock.mount(context.getLevel(), context.getClickedPos(), context.getPlayer(),
                    context.getItemInHand(), new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(),
                            context.getClickedFace(), context.getClickedPos(), context.isInside()));
        }
        if (context.getItemInHand().is(MaidHeadContent.MAID_BODY_ITEM.get())) return net.minecraft.world.InteractionResult.FAIL;
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.is(MaidHeadContent.MAID_BODY_ITEM.get())) {
            tooltip.add(Component.translatable("tooltip.yamidelight.winefox_body.table").withStyle(ChatFormatting.GRAY));
        }
        MaidHeadData data = MaidHeadContent.dataOf(stack);
        if (!data.maidName().isBlank()) {
            tooltip.add(Component.translatable("tooltip.yamidelight.winefox_body.owner", data.maidName())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (data.hasModel()) {
            tooltip.add(Component.translatable(data.isYsm()
                            ? "tooltip.yamidelight.winefox_head.model"
                            : "tooltip.yamidelight.winefox_head.tlm_model",
                            data.modelName().isBlank() ? data.modelId() : data.modelName())
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (!data.textureId().isBlank()) {
                tooltip.add(Component.translatable("tooltip.yamidelight.winefox_head.texture", data.textureId())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
