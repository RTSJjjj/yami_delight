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
import org.jetbrains.annotations.Nullable;


/** Persistent appearance and extension data for a mounted body. */
public class MaidBodyBlockEntity extends BlockEntity {
    private static final String DATA_TAG = "Body";
    private MaidHeadData data = MaidHeadData.EMPTY;
    private CompoundTag interactionData = new CompoundTag();

    
    /** Edit the copy, then call setInteractionData to save and synchronize it. */
    public CompoundTag interactionData() { return interactionData.copy(); }

    public void setInteractionData(CompoundTag tag) {
        interactionData = tag.copy();
        setData(data);
    }

    
    public net.minecraft.world.item.ItemStack asItem() {
        var stack = isRemains() ? MaidHeadContent.remains(data) : MaidHeadContent.body(data);
        if (!interactionData.isEmpty()) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(interactionData));
        return stack;
    }

    public void loadItem(net.minecraft.world.item.ItemStack stack) {
        interactionData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        setData(MaidHeadContent.dataOf(stack));
    }

    public MaidBodyBlockEntity(BlockPos pos, BlockState state) {
        this(MaidHeadContent.MAID_BODY_BE.get(), pos, state);
    }

    protected MaidBodyBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public MaidHeadData data() {
        return data;
    }

    public boolean isRemains() { return getBlockState().is(MaidHeadContent.MAID_REMAINS.get()); }

    public void setData(MaidHeadData data) {
        this.data = data == null ? MaidHeadData.EMPTY : data;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("InteractionData", interactionData.copy());
        MaidHeadData.CODEC.encodeStart(NbtOps.INSTANCE, data)
                .result()
                .ifPresent(encoded -> tag.put(DATA_TAG, encoded));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        interactionData = tag.getCompound("InteractionData").copy();
        data = MaidHeadData.EMPTY;
        if (tag.contains(DATA_TAG, Tag.TAG_COMPOUND)) {
            data = MaidHeadData.CODEC.parse(NbtOps.INSTANCE, tag.getCompound(DATA_TAG))
                    .result()
                    .orElse(MaidHeadData.EMPTY);
        }
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
