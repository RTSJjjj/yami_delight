package com.yami.yamidelight.head;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What a dropped head remembers about the maid it came from.
 *
 * <p>{@code modelId} and {@code textureId} are Touhou Little Maid's own YSM fields, copied verbatim, so
 * the client can look the model up the same way YSM itself does. {@code modelName} is the display name
 * YSM showed for that model and is the fallback key when the id alone does not match anything on disk,
 * which happens when a pack ships the same model under a different folder. {@code maidName} is only
 * ever used for the tooltip.
 */
public record MaidHeadData(String source, String modelId, String textureId, String modelName, String maidName,
                           String faceStyle) {
    /** A model from Yes Steve Model, found under {@code config/yes_steve_model}. */
    public static final String SOURCE_YSM = "ysm";
    /** One of Touhou Little Maid's own models, found under {@code tlm_custom_pack}. */
    public static final String SOURCE_TLM = "tlm";
    /** The expression baked into the Sea Land Wind roasted winefox feast. */
    public static final String FACE_STYLE_SEA_LAND_WIND = "sea_land_wind";
    public static final MaidHeadData EMPTY = new MaidHeadData(SOURCE_YSM, "", "", "", "", "");

    /**
     * Kept for existing head producers and old integrations.  The sixth field is optional in the
     * persistent codec too, so heads saved before feast expressions were added remain unchanged.
     */
    public MaidHeadData(String source, String modelId, String textureId, String modelName, String maidName) {
        this(source, modelId, textureId, modelName, maidName, "");
    }

    public static final Codec<MaidHeadData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("source", SOURCE_YSM).forGetter(MaidHeadData::source),
            Codec.STRING.optionalFieldOf("model_id", "").forGetter(MaidHeadData::modelId),
            Codec.STRING.optionalFieldOf("texture_id", "").forGetter(MaidHeadData::textureId),
            Codec.STRING.optionalFieldOf("model_name", "").forGetter(MaidHeadData::modelName),
            Codec.STRING.optionalFieldOf("maid_name", "").forGetter(MaidHeadData::maidName),
            Codec.STRING.optionalFieldOf("face_style", "").forGetter(MaidHeadData::faceStyle)
    ).apply(instance, MaidHeadData::new));

    public static final StreamCodec<ByteBuf, MaidHeadData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MaidHeadData::source,
            ByteBufCodecs.STRING_UTF8, MaidHeadData::modelId,
            ByteBufCodecs.STRING_UTF8, MaidHeadData::textureId,
            ByteBufCodecs.STRING_UTF8, MaidHeadData::modelName,
            ByteBufCodecs.STRING_UTF8, MaidHeadData::maidName,
            ByteBufCodecs.STRING_UTF8, MaidHeadData::faceStyle,
            MaidHeadData::new);

    /** Whether this head knows which YSM model it came from; a head without one draws the fallback. */
    public boolean hasModel() {
        return !modelId.isBlank();
    }

    public MaidHeadData withMaidName(String name) {
        return new MaidHeadData(source, modelId, textureId, modelName, name, faceStyle);
    }

    /** Returns a copy that permanently selects one authored feast face. */
    public MaidHeadData withFaceStyle(String style) {
        return new MaidHeadData(source, modelId, textureId, modelName, maidName, style == null ? "" : style);
    }

    /** True when the model comes from Yes Steve Model rather than from Touhou Little Maid itself. */
    public boolean isYsm() {
        return !SOURCE_TLM.equals(source);
    }
}
