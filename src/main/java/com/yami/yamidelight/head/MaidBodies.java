package com.yami.yamidelight.head;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.item.ItemEntity;

/** Saves appearance at extraction time; one body per headless maid even across chunk reloads. */
public final class MaidBodies {
    private static final String APPEARANCE = "YamidelightBodyAppearance";
    private static final String DROPPED = "YamidelightBodyDropped";
    private MaidBodies() {}

    public static void remember(EntityMaid maid, MaidHeadData data) {
        MaidHeadData.CODEC.encodeStart(NbtOps.INSTANCE, data).result()
                .ifPresent(tag -> maid.getPersistentData().put(APPEARANCE, tag));
    }

    public static void dropOnce(EntityMaid maid) {
        if (maid.level().isClientSide || !MaidHeads.hasTaken(maid)
                || maid.getPersistentData().getBoolean(DROPPED)) return;
        var state = maid.getPersistentData();
        MaidHeadData data = state.contains(APPEARANCE)
                ? MaidHeadData.CODEC.parse(NbtOps.INSTANCE, state.get(APPEARANCE)).result().orElse(null) : null;
        if (data == null) data = MaidHeads.dataOf(maid);
        if (data == null) return;
        ItemEntity item = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.15, maid.getZ(),
                MaidHeadContent.body(data));
        item.setDefaultPickUpDelay();
        if (maid.level().addFreshEntity(item)) state.putBoolean(DROPPED, true);
    }
}
