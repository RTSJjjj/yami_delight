package com.yami.yamidelight.head;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/** Register common-side handlers at startup. PASS delegates to the next handler. */
public final class MaidBodyInteractions {
    public record Context(MaidBodyBlockEntity body, Player player, InteractionHand hand, BlockHitResult hit) {
        public ItemStack heldItem() { return player.getItemInHand(hand); }
        public boolean clientSide() { return player.level().isClientSide; }
    }
    @FunctionalInterface
    public interface Handler { InteractionResult interact(Context context); }
    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();
    private MaidBodyInteractions() {}

    /** Return SUCCESS on the client for prediction; only mutate state on the server. */
    public static void register(Handler handler) { HANDLERS.add(java.util.Objects.requireNonNull(handler)); }

    public static InteractionResult interact(Context context) {
        for (Handler handler : HANDLERS) {
            InteractionResult result = handler.interact(context);
            if (result != InteractionResult.PASS) return result;
        }
        return InteractionResult.PASS;
    }
}
