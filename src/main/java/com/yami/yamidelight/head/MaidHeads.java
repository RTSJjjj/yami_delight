package com.yami.yamidelight.head;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything about taking the one head a maid has.
 *
 * <p>A maid only gets to lose her head once, and that has to survive a relog, so the fact is kept in her
 * persistent data rather than in a field of the item or the block. Any code path that wants a head -
 * the test sword, a kill, a future right click - goes through {@link #take}, which is the only place
 * that flag is written.
 *
 * <p>There are two families of model to take a head from, and both end up in the same item. Yes Steve
 * Model keeps its models in its own config folder and marks the maid with {@code IsYsmModel}; Touhou
 * Little Maid has its own model packs, which are the same Bedrock format - in fact twenty of them were
 * ported straight from YSM and still call the head joint {@code MHead}, while the rest call it
 * {@code head}. The source travels with the head so the client knows which set of files to read.
 */
public final class MaidHeads {
    /** Key in the maid's persistent data: her head has been taken. */
    public static final String TAKEN_TAG = "YamidelightHeadTaken";

    private MaidHeads() {}

    public static boolean hasTaken(EntityMaid maid) {
        return maid.getPersistentData().getBoolean(TAKEN_TAG);
    }

    /**
     * Why there is nothing to take, as a translation key suffix, or null when there is a head to take.
     * The reasons are worded for a player reading the action bar mid-test.
     */
    @Nullable
    public static String refusal(EntityMaid maid) {
        if (MaidHanging.engaged(maid)) return "already_taken";
        if (hasTaken(maid)) {
            return "already_taken";
        }
        String modelId = maid.isYsmModel() ? maid.getYsmModelId() : maid.getModelId();
        if (modelId == null || modelId.isBlank()) {
            return "no_model";
        }
        return null;
    }

    /** What the head should remember about her, or null when there is no model to read it from. */
    @Nullable
    public static MaidHeadData dataOf(EntityMaid maid) {
        String maidName = maid.getName().getString();
        if (maid.isYsmModel()) {
            String modelId = maid.getYsmModelId();
            if (modelId == null || modelId.isBlank()) {
                return null;
            }
            String texture = maid.getYsmModelTexture();
            String modelName = maid.getYsmModelName() == null ? "" : maid.getYsmModelName().getString();
            return new MaidHeadData(MaidHeadData.SOURCE_YSM, modelId, texture == null ? "" : texture,
                    modelName, maidName);
        }
        // Her own model, named "namespace:model". Touhou Little Maid models carry no second texture:
        // a pack may list extra textures, but nothing on the maid picks between them, so the model's
        // own textures/entity/<model>.png is the one she is wearing.
        String modelId = maid.getModelId();
        return modelId == null || modelId.isBlank()
                ? null
                : new MaidHeadData(MaidHeadData.SOURCE_TLM, modelId, "", "", maidName);
    }

    /**
     * Spends her head and returns it as an item, or null when she has none left to give. Callers that
     * put the head somewhere have to do it themselves; {@link #dropHere} is the common case.
     */
    @Nullable
    public static ItemStack take(EntityMaid maid) {
        if (refusal(maid) != null) {
            return null;
        }
        MaidHeadData data = dataOf(maid);
        if (data == null) {
            return null;
        }
        maid.getPersistentData().putBoolean(TAKEN_TAG, true);
        MaidBodies.remember(maid, data);
        // Disable before creating the head item, not after it has already spawned.
        maid.setPickup(false);
        maid.setCanPickUpLoot(false);
        // The head is gone from the model now, and that has to show on everyone's screen, not just the
        // one that took it. Only a server can say so; on a client this would be a packet to nowhere.
        if (!maid.level().isClientSide) {
            MaidHeadNetwork.broadcastTaken(maid);
        }
        return MaidHeadContent.head(data);
    }

    /** Takes her head and drops it where she stands. False when there was nothing to take. */
    public static boolean dropHere(EntityMaid maid) {
        ItemStack head = take(maid);
        if (head == null) {
            return false;
        }
        ItemEntity item = new ItemEntity(maid.level(), maid.getX(), maid.getY() + maid.getBbHeight() * 0.5D,
                maid.getZ(), head);
        item.setDefaultPickUpDelay();
        maid.level().addFreshEntity(item);
        return true;
    }
}
