package com.yami.yamidelight.head;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Holds {model id, texture id, model name, maid name} for one mounted head.
 *
 * <p>This has to reach the client, because the client is the only side that can read the model file and
 * draw it, so the data is written into both the save and the update tag. The update packet is what makes
 * the head appear the moment it is placed rather than after the next chunk reload.
 */
public final class MaidHeadBlockEntity extends BlockEntity {
    private static final String DATA_TAG = "Head";
    private static final String DISSECTION_STAGE_TAG = "DissectionStage";
    private MaidHeadData data = MaidHeadData.EMPTY;
    private int dissectionStage;

    public MaidHeadBlockEntity(BlockPos pos, BlockState state) {
        super(MaidHeadContent.MAID_HEAD_BE.get(), pos, state);
    }

    public MaidHeadData data() {
        return data;
    }
    public int dissectionStage() { return dissectionStage; }
    public boolean hasDissectionStage() { return dissectionStage >= 1; }
    public boolean isDissectedHead() { return dissectionStage >= 2; }
    public boolean isEmptyHead() { return dissectionStage >= 3; }

    public ItemStack asItem() {
        ItemStack result = isEmptyHead() ? MaidHeadContent.emptyHead(data, dissectionStage) : MaidHeadContent.head(data);
        if (hasDissectionStage()) {
            var custom = result.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
            custom.putInt(DissectionTableBlockEntity.HEAD_DISSECTION_STAGE, dissectionStage);
            result.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                    net.minecraft.world.item.component.CustomData.of(custom));
        }
        return result;
    }

    public void setData(MaidHeadData data) {
        this.data = data == null ? MaidHeadData.EMPTY : data;
        dissectionStage = 0;
        sync();
    }

    public void setItem(ItemStack stack) {
        data = MaidHeadContent.dataOf(stack);
        dissectionStage = DissectionTableBlockEntity.headDissectionStage(stack);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        MaidHeadData.CODEC.encodeStart(NbtOps.INSTANCE, data)
                .result()
                .ifPresent(encoded -> tag.put(DATA_TAG, encoded));
        tag.putInt(DISSECTION_STAGE_TAG, dissectionStage);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(DATA_TAG, Tag.TAG_COMPOUND)) {
            data = MaidHeadData.CODEC.parse(NbtOps.INSTANCE, tag.getCompound(DATA_TAG))
                    .result()
                    .orElse(MaidHeadData.EMPTY);
        }
        dissectionStage = tag.contains(DISSECTION_STAGE_TAG)
                ? Math.clamp(tag.getInt(DISSECTION_STAGE_TAG), 0, 5)
                : (tag.getBoolean("EmptyHead") ? 5 : 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
