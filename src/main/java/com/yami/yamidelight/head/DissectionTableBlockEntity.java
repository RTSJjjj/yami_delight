package com.yami.yamidelight.head;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Stores the original item (including all components), plus the existing interaction API. */
public final class DissectionTableBlockEntity extends MaidBodyBlockEntity {
    public static final String TAIL_REMOVED = "YamidelightTailRemoved";
    public static final String DISSECTION_STEP = "YamidelightDissectionStep";
    /** Persisted on the returned body item so pose A survives taking it off the table. */
    public static final String FRONT_FACING = "YamidelightFrontFacing";
    /** Persisted stage for a head taken off the table; 2+ is a ground-only dissected head. */
    public static final String HEAD_DISSECTION_STAGE = "YamidelightHeadDissectionStage";
    /** C's ten removable groups finish at this step; from here the body cannot be retrieved. */
    public static final int AXE_START_STEP = 12;
    public static final int FINAL_STEP = 17;
    private ItemStack stored = ItemStack.EMPTY;
    /** A head uses the same table but has an independent, non-body dissection sequence. */
    private ItemStack storedHead = ItemStack.EMPTY;
    private int headStage;
    private boolean cutting;
    private boolean feetAtFacing;
    private boolean flipped;
    public DissectionTableBlockEntity(BlockPos pos, BlockState state) {
        super(MaidHeadContent.DISSECTION_TABLE_BE.get(), pos, state);
    }
    public boolean occupied() { return !stored.isEmpty() || !storedHead.isEmpty(); }
    public boolean headMounted() { return !storedHead.isEmpty(); }
    public int headStage() { return headStage; }
    public ItemStack mountedHead() { return storedHead; }
    @Override public boolean isRemains() { return stored.is(MaidHeadContent.MAID_REMAINS_ITEM.get()); }
    public boolean isProcessedMaid() { return stored.is(MaidHeadContent.PROCESSED_MAID.get()); }
    public boolean tailRemoved() { return interactionData().getBoolean(TAIL_REMOVED); }
    public int dissectionStep() { return interactionData().getInt(DISSECTION_STEP); }
    public boolean dissectionLocked() { return dissectionStep() >= AXE_START_STEP; }
    /** C after its uterus has been removed, before the first axe cut. */
    public boolean readyForProcessedMaid() { return dissectionStep() == AXE_START_STEP; }
    public boolean readyForFinalRemoval() { return dissectionStep() >= FINAL_STEP; }
    public boolean cutting() { return cutting; }
    public void beginCut() { cutting = true; setChanged(); }
    public boolean feetAtFacing() { return feetAtFacing; }
    public boolean flipped() { return flipped; }
    /** Turns the tail-removed body to its front side. This transition is intentionally one-way. */
    public boolean flipToFront() {
        if (!occupied() || !tailRemoved() || cutting || flipped) return false;
        flipped = true;
        var interaction = interactionData();
        interaction.putBoolean(FRONT_FACING, true);
        setInteractionData(interaction);
        return true;
    }
    public void advanceDissection() {
        var data = interactionData();
        data.putInt(DISSECTION_STEP, dissectionStep() + 1);
        setInteractionData(data);
    }
    public void mount(ItemStack stack, net.minecraft.world.entity.player.Player player) {
        var facing = getBlockState().getValue(DissectionTableBlock.FACING);
        double along = (player.getX()-worldPosition.getX()-.5)*facing.getStepX()
                +(player.getZ()-worldPosition.getZ()-.5)*facing.getStepZ();
        // Exactly beside the middle: use the direction the player is approaching from.
        if (Math.abs(along)<1.0E-6) along = -player.getLookAngle().x*facing.getStepX()-player.getLookAngle().z*facing.getStepZ();
        feetAtFacing = along>0;
        cutting = false;
        storedHead = ItemStack.EMPTY;
        headStage = 0;
        stored = stack.copyWithCount(1);
        loadItem(stored);
        // Once a body has entered B or a later stage it must resume in its front-facing table pose.
        flipped = frontFacing(stack) || dissectionStep() > 0;
    }
    @Override public ItemStack asItem() {
        if (headMounted()) return storedHead.copy();
        if (!occupied()) return ItemStack.EMPTY;
        ItemStack result = stored.copy();
        var data = interactionData();
        // Breaking the table during the two-tick visual delay must not undo a completed cut.
        if (cutting) data.putBoolean(TAIL_REMOVED, true);
        if (flipped) data.putBoolean(FRONT_FACING, true);
        else data.remove(FRONT_FACING);
        result.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(data));
        return result;
    }
    public ItemStack take() {
        ItemStack result = asItem();
        cutting = false;
        stored = ItemStack.EMPTY;
        flipped = false;
        feetAtFacing = false;
        loadItem(ItemStack.EMPTY);
        return result;
    }
    public void mountHead(ItemStack stack) {
        stored = ItemStack.EMPTY;
        cutting = false;
        flipped = false;
        feetAtFacing = false;
        headStage = headDissectionStage(stack);
        storedHead = stack.copyWithCount(1);
        loadItem(storedHead);
        setChanged();
    }
    public void advanceHeadStage() {
        if (!headMounted()) return;
        headStage++;
        // The stage is renderer state, so send the same BE update packet used for the stored head data.
        setData(data());
    }
    public ItemStack takeMountedHead() {
        if (!headMounted()) return ItemStack.EMPTY;
        ItemStack result = storedHead.copy();
        int stage = headStage;
        storedHead = ItemStack.EMPTY;
        headStage = 0;
        loadItem(ItemStack.EMPTY);
        setChanged();
        if (stage >= 3) result = MaidHeadContent.emptyHead(MaidHeadContent.dataOf(result), stage);
        else if (stage >= 1) setHeadDissectionStage(result, stage);
        return result;
    }
    /** Returns the original data-bearing head block, explicitly named as the finished empty skull. */
    public ItemStack takeEmptyHead() {
        ItemStack result = takeMountedHead();
        String maidName = MaidHeadContent.dataOf(result).maidName();
        result = MaidHeadContent.emptyHead(MaidHeadContent.dataOf(result), 5);
        result.set(DataComponents.CUSTOM_NAME, Component.literal((maidName.isBlank() ? "酒狐" : maidName) + "的空头颅"));
        return result;
    }
    private static void setHeadDissectionStage(ItemStack stack, int stage) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        data.putInt(HEAD_DISSECTION_STAGE, Math.clamp(stage, 1, 5));
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
    }
    public static int headDissectionStage(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        // Accept the one earlier final-skull tag so existing completed heads do not revert visually.
        int legacy = data.getInt("YamidelightEmptyHeadStage");
        return Math.clamp(Math.max(data.getInt(HEAD_DISSECTION_STAGE), legacy), 0, 5);
    }
    public static boolean isDissectedHead(ItemStack stack) { return headDissectionStage(stack) >= 2; }
    public static boolean isEmptyHead(ItemStack stack) {
        return headDissectionStage(stack) >= 3;
    }
    public void removeTail() {
        cutting = false;
        var data = interactionData();
        data.putBoolean(TAIL_REMOVED, true);
        setInteractionData(data);
    }
    /** Converts a tail-free full remains item to the unflipped headless pose; the next empty-hand use selects A. */
    public ItemStack removeHead() {
        if (!occupied() || !isRemains()) return ItemStack.EMPTY;
        ItemStack head = MaidHeadContent.head(data());
        var data = interactionData();
        // The tail state is already earned by the prior shear.  Leave the new headless body unflipped
        // so its one normal empty-hand flip switches directly from jiepotai to authored jiepotaiA.
        data.putBoolean(TAIL_REMOVED, true);
        data.remove(FRONT_FACING);
        data.remove(DISSECTION_STEP);
        stored = MaidHeadContent.body(this.data());
        stored.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(data));
        cutting = false;
        flipped = false;
        loadItem(stored);
        return head;
    }
    /** The final yield replaces the stored body; no body item is returned. */
    public void finishDissection() {
        cutting = false;
        stored = ItemStack.EMPTY;
        storedHead = ItemStack.EMPTY;
        headStage = 0;
        flipped = false;
        feetAtFacing = false;
        loadItem(ItemStack.EMPTY);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (cutting && level != null && !level.isClientSide) level.scheduleTick(worldPosition, getBlockState().getBlock(), 2);
    }
    @Override public boolean triggerEvent(int id, int count) {
        if (id == 1) {
            if (level != null && level.isClientSide) com.yami.yamidelight.head.client.DissectionTableRenderer.cutParticles(this, count);
            return true;
        }
        return super.triggerEvent(id, count);
    }
    public static boolean tailRemoved(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean(TAIL_REMOVED);
    }
    public static int dissectionStep(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getInt(DISSECTION_STEP);
    }
    public static boolean frontFacing(ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean(FRONT_FACING);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Cutting", cutting);
        tag.putBoolean("FeetAtFacing", feetAtFacing);
        tag.putBoolean("Flipped", flipped);
        if (!stored.isEmpty()) tag.put("StoredBody", stored.save(registries));
        if (!storedHead.isEmpty()) tag.put("StoredHead", storedHead.save(registries));
        tag.putInt("HeadStage", headStage);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cutting = tag.getBoolean("Cutting");
        feetAtFacing = tag.getBoolean("FeetAtFacing");
        flipped = tag.getBoolean("Flipped");
        stored = tag.contains("StoredBody") ? ItemStack.parseOptional(registries, tag.getCompound("StoredBody")) : ItemStack.EMPTY;
        storedHead = tag.contains("StoredHead") ? ItemStack.parseOptional(registries, tag.getCompound("StoredHead")) : ItemStack.EMPTY;
        headStage = storedHead.isEmpty() ? 0 : Math.clamp(tag.getInt("HeadStage"), 0, 5);
    }
}
