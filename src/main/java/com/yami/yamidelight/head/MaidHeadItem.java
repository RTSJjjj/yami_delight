package com.yami.yamidelight.head;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/**
 * The item form of a mounted head.
 *
 * <p>The tooltip names the model, the texture and whose head it is, because a shelf of heads from the
 * same model but different textures is otherwise impossible to tell apart without placing each one.
 */
public final class MaidHeadItem extends BlockItem {
    public MaidHeadItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof DissectionTableBlock) {
            if (context.getPlayer() == null) return net.minecraft.world.InteractionResult.FAIL;
            return DissectionTableBlock.mountHead(context.getLevel(), context.getClickedPos(), context.getPlayer(), context.getItemInHand(),
                    new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()));
        }
        if (context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof com.yami.yamidelight.feast.StuffedWinefoxBlock feast) {
            if (context.getPlayer() == null) return net.minecraft.world.InteractionResult.FAIL;
            return feast.useHead(context.getLevel(), context.getClickedPos(), context.getPlayer(), context.getHand(),
                    new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()));
        }
        // A head opened from STAGE2 onward rests on the ground in its authored dissection pose; it
        // cannot become a wall-mounted trophy even when the player clicks a block side.
        if (DissectionTableBlockEntity.isDissectedHead(context.getItemInHand())
                && context.getClickedFace().getAxis().isHorizontal()) {
            return net.minecraft.world.InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        MaidHeadData data = MaidHeadContent.dataOf(stack);
        if (!data.maidName().isBlank()) {
            tooltip.add(Component.translatable("tooltip.yamidelight.winefox_head.owner", data.maidName())
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
