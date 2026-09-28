package com.yami.yamidelight.head;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidPickupEvent;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidTombstoneEvent;
import com.yami.yamidelight.head.mixin.MaidDropAccessor;
import com.yami.yamidelight.YamiConfig;
import com.yami.yamidelight.YamiDelight;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Every way a maid loses her head.
 *
 * <p>The ordinary one is death: a maid wearing a YSM model who is killed by a player leaves her head in
 * the drop list, which keeps it inside the normal drop pipeline. Dying is also hooked separately as a
 * second chance, because another mod can cancel the drop event and a head is not something to lose to
 * an ordering accident - whichever path runs first sets the flag in {@link MaidHeads}, so the maid can
 * still only ever give one head.
 *
 * <p>The test sword cancels direct damage, takes the head, then delays death until the clip finishes.
 */
@EventBusSubscriber(modid = YamiDelight.MODID)
public final class MaidHeadEvents {
    /** Persist the countdown so unloading a chunk cannot leave an invulnerable headless maid. */
    private static final String DEADLINE = "YamidelightDeathDeadline";
    private static final String OLD_INVULNERABLE = "YamidelightOldInvulnerable";
    private static final String OLD_NO_AI = "YamidelightOldNoAi";

    private MaidHeadEvents() {}

    /**
     * The test sword: one hit takes the head, the maid plays the model's death animation, and a moment
     * later she falls over. The hit itself deals no damage, so what kills her is her own missing head,
     * and a model that changes form at low health never gets the chance to.
     *
     * <p>The attack event fires on both sides - the client predicts the swing and the server resolves
     * it - and everything below this line is server work: the head is a real item, the maid is a real
     * entity, and a client is not allowed to send the packet that tells other clients her head is gone.
     */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof EntityMaid maid)) {
            return;
        }
        Player player = event.getEntity();
        if (!player.getMainHandItem().is(MaidHeadContent.TEST_SWORD.get())) {
            return;
        }
        // A test tool has no business hurting anyone: what kills her is the missing head, below.
        event.setCanceled(true);
        if (player.level().isClientSide) return;
        String reason = MaidHeads.refusal(maid);
        if (reason != null || !MaidHeads.dropHere(maid)) {
            player.displayClientMessage(Component.translatable("message.yamidelight.winefox_head."
                    + (reason == null ? "nothing" : reason)).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // Gecko uses our main-controller override; YSM retains its own roulette interface.
        MaidDeathSounds.playOnce(maid);
        if (maid.isYsmModel()) maid.playRouletteAnim(YamiConfig.testSwordAnimation());
        int delay = Math.max(MaidDeathTiming.minimumKillDelay(), YamiConfig.testSwordKillDelay());
        var state = maid.getPersistentData();
        state.putLong(DEADLINE, maid.level().getGameTime() + delay);
        state.putBoolean(OLD_INVULNERABLE, maid.isInvulnerable());
        state.putBoolean(OLD_NO_AI, maid.isNoAi());
        maid.getNavigation().stop();
        maid.setTarget(null);
        maid.setNoAi(true);
        maid.setInvulnerable(true);
        player.displayClientMessage(Component.translatable("message.yamidelight.winefox_head.taken",
                maid.getName()).withStyle(ChatFormatting.GRAY), true);
    }

    /** Counts down the maids still standing with no head on, and drops them when their time is up. */
    @SubscribeEvent
    public static void onMaidTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof EntityMaid maid) || maid.level().isClientSide) return;
        var state = maid.getPersistentData();
        if (!state.contains(DEADLINE)) return;
        if (maid.isDeadOrDying() || maid.level().getGameTime() >= state.getLong(DEADLINE)) {
            maid.setInvulnerable(state.getBoolean(OLD_INVULNERABLE));
            maid.setNoAi(state.getBoolean(OLD_NO_AI));
            state.remove(DEADLINE);
            state.remove(OLD_INVULNERABLE);
            state.remove(OLD_NO_AI);
            // Run the death/loot callbacks, then remove in this same server tick.
            // A second vanilla death phase can reset the Gecko pose before removal.
            MaidBodies.dropOnce(maid);
            if (!maid.isDeadOrDying()) maid.kill();
            maid.discard();
        }
    }

    /** Also reject direct pickup calls that bypass the AI's pickup toggle. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onPickupItem(MaidPickupEvent.ItemResultPre event) {
        blockPickup(event);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onPickupArrow(MaidPickupEvent.ArrowResult event) {
        blockPickup(event);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onPickupExperience(MaidPickupEvent.ExperienceResult event) {
        blockPickup(event);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onPickupPowerPoint(MaidPickupEvent.PowerPointResult event) {
        blockPickup(event);
    }

    // NeoForge rejects subscriptions to abstract events. Only the concrete
    // pre-pickup events above are listeners; the shared base is a helper parameter.
    private static void blockPickup(MaidPickupEvent event) {
        if (MaidHeads.hasTaken(event.getMaid()) || MaidHanging.active(event.getMaid())) {
            event.setCanPickup(false);
            event.setCanceled(true);
        }
    }

    /** TLM has already built the real film and moved equipment into this temporary tombstone. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void onTombstone(MaidTombstoneEvent event) {
        EntityMaid maid = event.getMaid();
        if (maid.level().isClientSide || (!MaidHeads.hasTaken(maid) && !MaidHanging.active(maid))) return;
        MaidBodies.dropOnce(maid);
        event.setCanceled(true);
        // Cancelling skips TLM's own assignment. Set it here so remove(KILLED)
        // cannot run dropEquipment again and manufacture a second film.
        ((MaidDropAccessor) maid).yami$setAlreadyDropped(true);
        var items = event.getTombstone().getItems();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.extractItem(slot, Integer.MAX_VALUE, false);
            if (stack.isEmpty()) continue;
            if (MaidHanging.active(maid)) {
                var type = com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent.MAID_INFO.get();
                var info = stack.get(type);
                if (info != null) {
                    var tag = info.copyTag();
                    tag.getCompound("NeoForgeData").remove(MaidHanging.STATE);
                    tag.getCompound("NeoForgeData").remove("YamidelightHangingCandidate");
                    stack.set(type, net.minecraft.world.item.component.CustomData.of(tag));
                }
            }
            ItemEntity drop = new ItemEntity(maid.level(), maid.getX(), maid.getY() + 0.15D, maid.getZ(), stack);
            drop.setDefaultPickUpDelay();
            maid.level().addFreshEntity(drop);
        }
    }

    /** Killed by a player: her head goes into the ordinary drop list. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!YamiConfig.dropEnabled() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide) {
            return;
        }
        if (MaidHeads.refusal(maid) != null) {
            return;
        }
        if (!killedByPlayer(event.getSource().getEntity())) {
            decision(maid, "no head: the killing blow was not from a player");
            return;
        }
        if (!passesChance(maid)) {
            decision(maid, "drop chance");
            return;
        }
        ItemStack head = MaidHeads.take(maid);
        if (head == null) {
            return;
        }
        ItemEntity drop = new ItemEntity(maid.level(), maid.getX(),
                maid.getY() + maid.getBbHeight() * 0.5D, maid.getZ(), head);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
        decision(maid, "head dropped with the rest of her loot");
    }

    /**
     * Dying hooked a second time. If another mod cancelled {@link LivingDropsEvent}, or the maid died in
     * a way that never reaches it, this is what still gets the head onto the ground.
     */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!YamiConfig.dropEnabled() || !(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide) {
            return;
        }
        if (MaidHeads.refusal(maid) != null || !killedByPlayer(event.getSource().getEntity())
                || !passesChance(maid)) {
            return;
        }
        if (MaidHeads.dropHere(maid)) {
            decision(maid, "head dropped on death, after the loot list passed on it");
        }
    }

    /** Someone started seeing a maid who lost her head before they arrived. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof EntityMaid maid && MaidHeads.hasTaken(maid)
                && event.getEntity() instanceof ServerPlayer player) {
            MaidHeadNetwork.sendTaken(player, maid);
        }
    }

    private static boolean killedByPlayer(net.minecraft.world.entity.Entity killer) {
        return !YamiConfig.requirePlayerKill() || killer instanceof Player;
    }

    private static boolean passesChance(EntityMaid maid) {
        double chance = YamiConfig.dropChance();
        return chance > 0.0D && (chance >= 1.0D || maid.getRandom().nextDouble() < chance);
    }

    /** One line per maid that did not drop a head, so "why is there no head" is answered by the log. */
    private static void decision(EntityMaid maid, String what) {
        if (!YamiConfig.logDecisions()) {
            return;
        }
        YamiDelight.LOGGER.info("yamidelight: {} for maid {} (ysm model: {}, taken: {})",
                what, maid.getName().getString(), maid.getYsmModelId(), MaidHeads.hasTaken(maid));
    }
}
