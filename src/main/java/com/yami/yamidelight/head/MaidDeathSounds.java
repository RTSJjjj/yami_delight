package com.yami.yamidelight.head;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yami.yamidelight.YamiDelight;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Broadcast once at the start of the custom death sequence, or on ordinary death. */
@EventBusSubscriber(modid = YamiDelight.MODID)
public final class MaidDeathSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, YamiDelight.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = SOUNDS.register("winefox.death",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "winefox.death")));
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH2 = SOUNDS.register("winefox.death2",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "winefox.death2")));
    // Transient: do not serialize a played flag into the resurrection film.
    private static final Map<EntityMaid, Boolean> PLAYED = new WeakHashMap<>();
    private MaidDeathSounds() {}
    public static void init(IEventBus bus) { SOUNDS.register(bus); }

    public static void playOnce(EntityMaid maid) {
        playOnce(maid, DEATH.get());
    }

    public static void playHangingOnce(EntityMaid maid) {
        playOnce(maid, DEATH2.get());
    }

    public static void resetAfterHangingCancellation(EntityMaid maid) { PLAYED.remove(maid); }

    private static void playOnce(EntityMaid maid, SoundEvent sound) {
        if (maid.level().isClientSide || maid.isSilent() || PLAYED.putIfAbsent(maid, true) != null) return;
        // A position-based sound continues to its end even when the entity is removed.
        maid.level().playSound(null, maid.getX(), maid.getY(), maid.getZ(),
                sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        // Hanging already sounded at animation start; also skip after loading an ongoing sequence.
        if (event.getEntity() instanceof EntityMaid maid && !MaidHanging.active(maid)) playOnce(maid);
    }
}
