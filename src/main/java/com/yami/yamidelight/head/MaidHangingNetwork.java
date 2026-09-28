package com.yami.yamidelight.head;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yami.yamidelight.YamiDelight;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class MaidHangingNetwork {
    private MaidHangingNetwork() {}
    public record Sync(int id, CompoundTag state) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "hanging"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::id, ByteBufCodecs.COMPOUND_TAG, Sync::state, Sync::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Sync.TYPE, Sync.CODEC, (payload, context) -> context.enqueueWork(() -> {
            var entity = context.player().level().getEntity(payload.id());
            if (!(entity instanceof EntityMaid maid)) return;
            if (payload.state().isEmpty()) {
                boolean gravity = MaidHanging.state(maid).getBoolean("no_gravity");
                maid.getPersistentData().remove(MaidHanging.STATE);
                maid.setNoGravity(gravity);
            } else {
                payload.state().putLong("client_start", maid.level().getGameTime() - payload.state().getInt("skip_ticks"));
                maid.getPersistentData().put(MaidHanging.STATE, payload.state());
                if (MaidHanging.active(maid)) MaidHanging.freeze(maid);
                else maid.setNoGravity(true);
            }
        }));
    }
    private static Sync packet(EntityMaid maid, boolean active) {
        var s = active ? MaidHanging.state(maid).copy() : new CompoundTag();
        if (active && !MaidHanging.falling(maid)) s.putInt("skip_ticks", (int)Math.min(MaidDeathTiming.hangingTicks(),
                Math.max(0, maid.level().getGameTime() - s.getLong("start"))));
        return new Sync(maid.getId(), s);
    }
    public static void broadcast(EntityMaid maid, boolean active) { PacketDistributor.sendToPlayersTrackingEntity(maid, packet(maid, active)); }
    public static void send(ServerPlayer player, EntityMaid maid) { PacketDistributor.sendToPlayer(player, packet(maid, true)); }
}
