package com.yami.yamidelight.head;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yami.yamidelight.YamiDelight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** A separate death sequence: a suspended maid keeps her head and becomes posed remains. */
@EventBusSubscriber(modid = YamiDelight.MODID)
public final class MaidHanging {
    public static final String STATE = "YamidelightHanging";
    private static final String CANDIDATE = "YamidelightHangingCandidate";
    private MaidHanging() {}

    public static boolean engaged(Entity entity) { return entity.getPersistentData().contains(STATE); }
    public static boolean falling(Entity entity) { return engaged(entity) && state(entity).getBoolean("falling"); }
    public static boolean active(Entity entity) { return engaged(entity) && !falling(entity); }
    public static CompoundTag state(Entity entity) { return entity.getPersistentData().getCompound(STATE); }

    private static boolean finished(EntityMaid maid) {
        var s = state(maid);
        return s.getBoolean("finished") || maid.level().getGameTime() - s.getLong("start") >= MaidDeathTiming.hangingTicks();
    }

    private static boolean suspended(EntityMaid maid) {
        Entity holder = maid.getLeashHolder();
        return holder != null && holder.isAlive() && holder.level() == maid.level()
                && holder.getY() > maid.getY() + 2.0 && maid.distanceTo(holder) <= 10.0
                && !maid.onGround() && !maid.isPassenger() && !maid.isInWater() && !maid.isInLava()
                && maid.level().noCollision(maid, maid.getBoundingBox().move(0, -0.01, 0));
    }

    private static boolean leashBroken(EntityMaid maid) {
        // A saved leash may still be resolving its holder after chunk load; let vanilla resolve it.
        var leash = maid.getLeashData();
        return leash == null || (leash.leashHolder != null && !leash.leashHolder.isAlive());
    }

    @SubscribeEvent
    public static void beforeTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) return;
        if (maid.level().isClientSide) {
            if (active(maid)) freeze(maid);
            return;
        }
        if (falling(maid)) {
            if (!maid.isAlive() || !suspended(maid)) { cancel(maid); return; }
            // NoAI disables vanilla travel, so advance the normal gravity curve ourselves.
            // Entity.move still resolves collisions; the mixin clips displacement at exactly one block.
            maid.setNoGravity(true);
            double speed = state(maid).getDouble("fall_speed");
            speed = (speed + 0.08) * 0.98;
            state(maid).putDouble("fall_speed", speed);
            maid.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -speed, 0));
            maid.setDeltaMovement(Vec3.ZERO);
            maid.fallDistance = 0;
            return;
        }
        if (active(maid)) {
            if (!maid.isAlive() || (!finished(maid) && !suspended(maid))) { cancel(maid); return; }
            if (finished(maid) && leashBroken(maid)) { finishIfReady(maid); return; }
            freeze(maid);
            return;
        }
        if (!maid.isAlive() || MaidHeads.hasTaken(maid) || !suspended(maid)) {
            maid.getPersistentData().remove(CANDIDATE);
            return;
        }
        // First let normal gravity lower the maid one block, with leash elasticity suppressed.
        MaidHeadData appearance = MaidHeads.dataOf(maid);
        if (appearance == null) return;
        CompoundTag s = new CompoundTag();
        s.putBoolean("falling", true);
        s.putDouble("fall_target_y", maid.getY() - 1.0);
        s.putDouble("x", maid.getX()); s.putDouble("y", maid.getY()); s.putDouble("z", maid.getZ());
        s.putFloat("yaw", maid.getYRot());
        s.putBoolean("no_ai", maid.isNoAi()); s.putBoolean("no_gravity", maid.isNoGravity());
        s.putBoolean("invulnerable", maid.isInvulnerable());
        s.putBoolean("pickup", maid.isPickup()); s.putBoolean("loot", maid.canPickUpLoot());
        MaidHeadData.CODEC.encodeStart(NbtOps.INSTANCE, appearance).result().ifPresent(tag -> s.put("appearance", tag));
        maid.getPersistentData().put(STATE, s);
        maid.getPersistentData().remove(CANDIDATE);
        maid.setNoAi(true); maid.setInvulnerable(true);
        maid.setPickup(false); maid.setCanPickUpLoot(false);
        maid.getNavigation().stop();
        maid.setNoGravity(true);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.fallDistance = 0;
        MaidHangingNetwork.broadcast(maid, true);
    }

    private static void beginAnimation(EntityMaid maid) {
        var s = state(maid);
        s.putBoolean("falling", false);
        s.putLong("start", maid.level().getGameTime());
        s.putDouble("x", maid.getX()); s.putDouble("y", maid.getY()); s.putDouble("z", maid.getZ());
        freeze(maid);
        MaidHangingNetwork.broadcast(maid, true);
        MaidDeathSounds.playHangingOnce(maid);
        if (maid.isYsmModel()) maid.playRouletteAnim("death2.new");
    }

    @SubscribeEvent
    public static void afterTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof EntityMaid fallingMaid && falling(fallingMaid)) {
            if (fallingMaid.level().isClientSide) return;
            fallingMaid.fallDistance = 0;
            // Test support BEFORE starting the animation, including landing exactly at one block.
            if (!suspended(fallingMaid)) { cancel(fallingMaid); return; }
            if (fallingMaid.getY() <= state(fallingMaid).getDouble("fall_target_y") + 1.0E-6) beginAnimation(fallingMaid);
            return;
        }
        if (!(event.getEntity() instanceof EntityMaid maid) || !active(maid)) return;
        freeze(maid);
        if (maid.level().isClientSide) return;
        if (finished(maid)) {
            state(maid).putBoolean("finished", true);
            if (leashBroken(maid)) finishIfReady(maid);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof EntityMaid maid) || !engaged(maid)) return;
        event.setCanceled(true);
        if (!event.getEntity().isSpectator()) finishIfReady(maid);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof EntityMaid maid && engaged(maid)
                && !state(maid).getBoolean("finishing")) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interactSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!(event.getTarget() instanceof EntityMaid maid) || !engaged(maid)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(maid.level().isClientSide));
        if (!event.getEntity().isSpectator()) finishIfReady(maid);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof EntityMaid maid) || !engaged(maid)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(maid.level().isClientSide));
        if (!event.getEntity().isSpectator()) finishIfReady(maid);
    }

    private static void finishIfReady(EntityMaid maid) {
        if (maid.level().isClientSide || !active(maid) || !maid.isAlive() || maid.isRemoved() || !finished(maid)) return;
        var s = state(maid);
        // Guard duplicate hands/packets, keeping the hanging marker throughout death/drop callbacks.
        if (s.getBoolean("finishing")) return;
        s.putBoolean("finishing", true);
        dropRemains(maid);
        restore(maid, s);
        // Keep STATE through death callbacks so the original head/drop rules cannot run.
        maid.kill();
        maid.discard();
    }

    public static void freeze(EntityMaid maid) {
        var s = state(maid);
        maid.setNoGravity(true);
        maid.setDeltaMovement(Vec3.ZERO);
        maid.setPos(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
        maid.fallDistance = 0;
        maid.setYRot(s.getFloat("yaw"));
        maid.yBodyRot = maid.yBodyRotO = s.getFloat("yaw");
        if (maid.level().isClientSide) {
            // Discard in-flight movement interpolation and its previous-frame render position.
            maid.lerpTo(maid.getX(), maid.getY(), maid.getZ(), maid.getYRot(), maid.getXRot(), 0);
            maid.xo = maid.xOld = maid.getX();
            maid.yo = maid.yOld = maid.getY();
            maid.zo = maid.zOld = maid.getZ();
        }
    }

    private static void restore(EntityMaid maid, CompoundTag s) {
        maid.setNoAi(s.getBoolean("no_ai")); maid.setNoGravity(s.getBoolean("no_gravity"));
        maid.setInvulnerable(s.getBoolean("invulnerable"));
        maid.setPickup(s.getBoolean("pickup")); maid.setCanPickUpLoot(s.getBoolean("loot"));
        maid.setDeltaMovement(Vec3.ZERO); maid.fallDistance = 0;
    }

    private static void cancel(EntityMaid maid) {
        restore(maid, state(maid));
        MaidDeathSounds.resetAfterHangingCancellation(maid);
        MaidHangingNetwork.broadcast(maid, false);
        maid.getPersistentData().remove(STATE);
        maid.getPersistentData().remove(CANDIDATE);
    }

    private static void dropRemains(EntityMaid maid) {
        var s = state(maid);
        if (s.getBoolean("dropped")) return;
        var appearance = MaidHeadData.CODEC.parse(NbtOps.INSTANCE, s.getCompound("appearance"))
                .result().orElse(MaidHeadData.EMPTY);
        var item = new ItemEntity(maid.level(), maid.getX(), maid.getY(), maid.getZ(), MaidHeadContent.remains(appearance));
        item.setDefaultPickUpDelay();
        if (maid.level().addFreshEntity(item)) s.putBoolean("dropped", true);
    }

    @SubscribeEvent
    public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof EntityMaid maid && engaged(maid)
                && event.getEntity() instanceof ServerPlayer player) MaidHangingNetwork.send(player, maid);
    }
}
