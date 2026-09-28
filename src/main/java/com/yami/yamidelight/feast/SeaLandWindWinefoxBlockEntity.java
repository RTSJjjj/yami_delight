package com.yami.yamidelight.feast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The primary half stores the exact cooked-in head, including its permanent feast face style. */
public final class SeaLandWindWinefoxBlockEntity extends BlockEntity {
    private ItemStack head = ItemStack.EMPTY;

    public SeaLandWindWinefoxBlockEntity(BlockPos pos, BlockState state) {
        super(FeastContent.SEA_LAND_WIND_WINEFOX_BE.get(), pos, state);
    }

    public boolean hasHead() {
        return !head.isEmpty();
    }

    public ItemStack head() {
        return head.copy();
    }

    public void setHead(ItemStack stack) {
        head = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Creative left-click destruction must not manufacture the cooked-in head. */
    public void discardHead() {
        head = ItemStack.EMPTY;
        setChanged();
    }

    public ItemStack takeHead() {
        ItemStack result = head;
        head = ItemStack.EMPTY;
        setChanged();
        return result;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!head.isEmpty()) {
            tag.put("Head", head.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        head = tag.contains("Head") ? ItemStack.parseOptional(registries, tag.getCompound("Head")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
