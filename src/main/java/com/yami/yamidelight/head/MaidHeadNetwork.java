package com.yami.yamidelight.head;

import com.yami.yamidelight.YamiDelight;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Tells clients which maids have already lost their head, because that is the only way a client can
 * know: the flag lives in the maid's persistent data, which is saved but never synced.
 *
 * <p>Two moments need this. The one that matters is the moment the head is taken, when the message goes
 * to everyone who can see her so the head vanishes on every screen at once. The other is a player
 * starting to track a maid who lost her head earlier, otherwise walking away and coming back would put
 * the head back on.
 */
public final class MaidHeadNetwork {
    private MaidHeadNetwork() {}

    public record HeadTaken(int entityId, boolean fresh) implements CustomPacketPayload {
        public static final Type<HeadTaken> TYPE = new Type<>(
                ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "head_taken"));
        public static final StreamCodec<ByteBuf, HeadTaken> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HeadTaken::entityId,
                ByteBufCodecs.BOOL, HeadTaken::fresh, HeadTaken::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToClient(HeadTaken.TYPE, HeadTaken.STREAM_CODEC, (payload, context) ->
                // Client only: a dedicated server never receives this, so the client class is never
                // touched there. See MaidHeadHider.
                context.enqueueWork(() -> {
                    com.yami.yamidelight.head.client.MaidHeadHider.mark(payload.entityId());
                    if (payload.fresh()) com.yami.yamidelight.head.client.MaidBloodSpray.begin(payload.entityId());
                }));
    }

    /** Tells everyone who can see her that her head is gone. */
    public static void broadcastTaken(Entity maid) {
        if (maid.level() == null || maid.level().isClientSide) {
            return;
        }
        PacketDistributor.sendToPlayersTrackingEntity(maid, new HeadTaken(maid.getId(), true));
    }

    /** Tells one player, used when they start tracking a maid who lost her head earlier. */
    public static void sendTaken(ServerPlayer player, Entity maid) {
        PacketDistributor.sendToPlayer(player, new HeadTaken(maid.getId(), false));
    }
}
