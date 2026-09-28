package com.yami.yamidelight.head;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** The hook owns its body. No second block is placed in the space occupied by the hanging mesh. */
public final class MeatHookBlockEntity extends MaidBodyBlockEntity {
    private boolean occupied;
    /** Keep the exact item type as well as its components: processed maids must not turn back into normal bodies. */
    private ItemStack stored = ItemStack.EMPTY;
    public MeatHookBlockEntity(BlockPos pos, BlockState state) {
        super(MaidHeadContent.MEAT_HOOK_BE.get(), pos, state);
    }
    public boolean hasBody() { return occupied; }
    /** B and every C stage use the fixed winefox_1 scene; other occupants retain the dynamic body renderer. */
    public boolean usesWinefoxMesh() {
        int step = interactionData().getInt(DissectionTableBlockEntity.DISSECTION_STEP);
        return occupied && (stored.is(MaidHeadContent.MAID_BODY_ITEM.get()) || stored.is(MaidHeadContent.PROCESSED_MAID.get()))
                && step >= 1 && step <= DissectionTableBlockEntity.AXE_START_STEP;
    }
    public void mount(ItemStack stack) {
        stored = stack.copyWithCount(1);
        occupied = true;
        loadItem(stored);
        syncOccupiedState();
    }
    @Override public ItemStack asItem() {
        if (!occupied) return ItemStack.EMPTY;
        ItemStack result = stored.copy();
        result.set(MaidHeadContent.MAID_HEAD_DATA.get(), data());
        result.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(interactionData()));
        return result;
    }
    public ItemStack takeBody() {
        if (!occupied) return ItemStack.EMPTY;
        ItemStack item = asItem();
        occupied = false;
        stored = ItemStack.EMPTY;
        loadItem(ItemStack.EMPTY);
        syncOccupiedState();
        return item;
    }
    @Override public void onLoad() {
        super.onLoad();
        syncOccupiedState();
    }
    private void syncOccupiedState() {
        if (level == null || level.isClientSide || !(getBlockState().getBlock() instanceof MeatHookBlock)) return;
        // Both variants intentionally retain the normal block-model hook below the dynamic body.
        boolean referenceMesh = usesWinefoxMesh();
        if (getBlockState().getValue(MeatHookBlock.OCCUPIED) != referenceMesh) {
            level.setBlock(worldPosition, getBlockState().setValue(MeatHookBlock.OCCUPIED, referenceMesh), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Occupied", occupied);
        if (!stored.isEmpty()) tag.put("StoredBody", asItem().save(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        occupied = tag.getBoolean("Occupied");
        // Old worlds saved only the inherited data.  Recover those hooks as the original body item.
        stored = tag.contains("StoredBody") ? ItemStack.parseOptional(registries, tag.getCompound("StoredBody"))
                : (occupied ? super.asItem() : ItemStack.EMPTY);
    }
}
