package com.yami.yamidelight.feast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FoxHeadFoodBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    public FoxHeadFoodBlockEntity(BlockPos pos, BlockState state) {
        super(FeastContent.FOX_HEAD_FOOD_BE.get(), pos, state);
    }
    public ItemStack item() { return item.copy(); }
    public void setItem(ItemStack stack) {
        item = stack.copyWithCount(1);
        changed();
    }
    public void discard() { item = ItemStack.EMPTY; setChanged(); }
    public void advance() { FoxHeadFoodData.stage(item, FoxHeadFoodData.stage(item) + 1); changed(); }
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!item.isEmpty()) tag.put("Item", item.save(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        item = ItemStack.parseOptional(registries, tag.getCompound("Item"));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
