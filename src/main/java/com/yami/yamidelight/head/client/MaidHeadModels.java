package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.AbstractBedrockModel;
import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.mojang.blaze3d.platform.NativeImage;
import com.yami.yamidelight.YamiConfig;
import com.yami.yamidelight.YamiDelight;
import com.yami.yamidelight.head.MaidHeadData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Turns the data on a head into something drawable, once per model, and remembers the failures.
 *
 * <p>Reading a model file, parsing its geometry and uploading its texture all cost real time, and a wall
 * of heads from the same maid would otherwise pay it every frame. The cache is keyed by model and texture
 * together, so two heads from the same model with different textures get their own textures and share
 * nothing but the file lookup. Everything here is dropped when the resource manager reloads, because a
 * texture registered by hand does not survive a reload either.
 *
 * <p>A head whose model cannot be found is not invisible: it is drawn as the plain default head this mod
 * ships, and the miss is logged once with the folders that were searched. A model packed as an encrypted
 * {@code .ysm} file lands here, and so does a head carried over from a server that had a model this
 * client does not.
 */
public final class MaidHeadModels {
    private static final Map<String, Optional<MaidHeadModel>> CACHE = new HashMap<>();
    private static final Set<String> REPORTED = new HashSet<>();
    private static final float FALLBACK_SIZE = 0.5F;
    // In the item preview, Allhead4 ends at Y=7.25 and the main skull bottom is
    // Y=6.35435 (after its 180-degree rotation): overlap by 0.89565 pixels.
    // Transfer only that vertical overlap, not the preview's X/Z or head scale.
    private static final float HEAD_ATTACHMENT_INSET_Y = 0.89565F;
    private static final float HEAD_ATTACHMENT_TOP_Y = 34.0F - HEAD_ATTACHMENT_INSET_Y;
    private static MaidHeadModel fallback;

    private MaidHeadModels() {}

    public static MaidHeadModel get(MaidHeadData data) {
        if (!data.hasModel()) {
            return fallback();
        }
        String key = data.modelId() + '\u0000' + data.textureId() + '\u0000' + data.faceStyle();
        Optional<MaidHeadModel> cached = CACHE.get(key);
        if (cached == null) {
            cached = Optional.ofNullable(load(data, key));
            CACHE.put(key, cached);
        }
        return cached.orElseGet(MaidHeadModels::fallback);
    }

    public static synchronized void invalidate() {
        CACHE.clear();
        REPORTED.clear();
        fallback = null;
    }

    @Nullable
    private static MaidHeadModel load(MaidHeadData data, String key) {
        if (!data.isYsm()) {
            return loadMaidModel(data, key);
        }
        YsmModelLibrary.Entry entry = YsmModelLibrary.find(data);
        if (entry == null) {
            report(key, "no model folder matches " + data.modelId());
            return null;
        }
        byte[] json = entry.modelJson();
        if (json == null) {
            report(key, "the model file is missing in " + entry.describe());
            return null;
        }
        byte[] texture = entry.texture(data.textureId());
        if (texture == null) {
            report(key, "no texture in " + entry.describe());
            return null;
        }
        MaidHeadModel model = build(new String(json, StandardCharsets.UTF_8), entry.animationJson(), texture,
                entry.heightScale(), key, entry.describe(), data.modelId(), data.faceStyle());
        if (model == null) {
            report(key, "the model file has no usable head bone in " + entry.describe());
        }
        return model;
    }

    /**
     * The other family: one of Touhou Little Maid's own models. There is no info file to read - the model
     * id is the file name - and no {@code height_scale} either, because these models are authored at the
     * size a maid is.
     */
    @Nullable
    private static MaidHeadModel loadMaidModel(MaidHeadData data, String key) {
        TlmModelLibrary.Model model = TlmModelLibrary.load(data.modelId());
        if (model == null || model.geometry() == null) {
            report(key, "Touhou Little Maid has no model file for " + data.modelId());
            return null;
        }
        if (model.texture() == null) {
            report(key, "no texture next to " + data.modelId());
            return null;
        }
        MaidHeadModel built = build(new String(model.geometry(), StandardCharsets.UTF_8), null, model.texture(),
                1.0F, key, data.modelId(), data.modelId(), data.faceStyle());
        if (built == null) {
            report(key, "no head bone in " + data.modelId());
        }
        return built;
    }

    @Nullable
    private static MaidHeadModel build(String json, @Nullable byte[] animationJson, byte[] texture,
                                       float heightScale, String key, String origin, @Nullable String modelId,
                                       String faceStyle) {
        BedrockGeometry geometry = BedrockGeometry.parse(json);
        if (geometry == null) {
            return null;
        }
        String headBone = geometry.headBone();
        if (headBone == null) {
            return null;
        }
        AbstractBedrockModel baked;
        try {
            baked = new AbstractBedrockModel(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {};
        } catch (RuntimeException e) {
            YamiDelight.LOGGER.warn("yamidelight: could not bake {}: {}", origin, e.toString());
            return null;
        }
        BedrockPart head = baked.getModelMap().get(headBone);
        if (head == null) {
            return null;
        }
        Map<String, Float> defaultScales = BoneDefaults.scales(animationJson);
        applyDefaultScales(baked, defaultScales);
        List<BedrockPart> chain = new ArrayList<>();
        for (BedrockPart part = head.getParent(); part != null; part = part.getParent()) {
            chain.add(0, part);
        }
        // Measure the skull, not the whole group: see BedrockGeometry.skull.
        String skull = geometry.skull(headBone);
        float[] measured = skull == null ? null : geometry.measureOwn(skull);
        Vector3f centre = measured == null
                ? new Vector3f(0.0F, 0.0F, 0.0F)
                : new Vector3f(measured[0], measured[1], measured[2]);
        Vector3f size = measured == null
                ? new Vector3f(FALLBACK_SIZE, FALLBACK_SIZE, FALLBACK_SIZE)
                : new Vector3f(measured[3], measured[4], measured[5]);
        // A hand edited face replaces the parts it was drawn from. It applies to any model that has all
        // of them - the wine fox variants are the same head with different skins - and never to one that
        // does not, because the mesh is drawn in the coordinates of the model it came from.
        HeadExpression.Mesh expression = faceStyle == null || faceStyle.isBlank()
                ? (modelId == null ? null : HeadExpression.forModel(modelId))
                : HeadExpression.forFaceStyle(faceStyle);
        if (expression != null && !hasAll(baked, List.of("RightEyebrow", "LeftEyebrow",
                "RightEyelid", "LeftEyelid", "RightEyelidBase", "LeftEyelidBase"))) {
            expression = null;
        }
        // The redrawn face ships with its own copy of the texture, which belongs to the model it was
        // drawn from and to no other: a variant keeps its own skin and only borrows the shape.
        boolean exact = expression != null && HeadExpression.isExact(expression, modelId);
        byte[] headTexture = exact ? expression.texture() : texture;

        // What an item head is sized and centred by: everything that is drawn, with the parts hidden by
        // an animation or replaced by the redrawn face left out of the measurement.
        Set<String> hidden = new HashSet<>();
        if (expression != null) {
            hidden.addAll(expression.hide());
        }
        for (Map.Entry<String, Float> scale : defaultScales.entrySet()) {
            if (scale.getValue() != null && scale.getValue() == 0.0F) {
                hidden.add(scale.getKey());
            }
        }
        float[] drawn = geometry.measure(headBone, hidden);
        drawn = AllHeadParts.extendBounds(drawn, "AllHeadA", head, HEAD_ATTACHMENT_TOP_Y);
        Vector3f itemCentre = drawn == null ? centre : new Vector3f(drawn[0], drawn[1], drawn[2]);
        Vector3f itemSize = drawn == null ? size : new Vector3f(drawn[3], drawn[4], drawn[5]);

        // The head bone's pivot, in blocks: the origin of the frame its cubes are drawn in.
        float[] pivot = geometry.pivotOf(headBone);
        Vector3f faceOrigin = pivot == null
                ? new Vector3f(0.0F, 0.0F, 0.0F)
                : new Vector3f(pivot[0] / 16.0F, (24.0F - pivot[1]) / 16.0F, pivot[2] / 16.0F);

        ResourceLocation textureLocation;
        ResourceLocation baseTextureLocation;
        try {
            // Exact model expressions historically replace the whole head texture with their authored
            // sheet.  The roasted feast needs the maid's actual skin instead, so keep that upload too.
            baseTextureLocation = register(key + "#base", texture);
            textureLocation = headTexture == texture ? baseTextureLocation : register(key, headTexture);
        } catch (IOException | RuntimeException e) {
            YamiDelight.LOGGER.warn("yamidelight: could not read the texture of {}: {}", origin, e.toString());
            return null;
        }
        if (expression != null) {
            // The head is drawn from that texture now, so the parts it replaces would be drawn twice.
            for (String bone : expression.hide()) {
                BedrockPart part = baked.getModelMap().get(bone);
                if (part != null) {
                    part.xScale = part.yScale = part.zScale = 0.0F;
                }
            }
        }
        var result = new MaidHeadModel(head, List.copyOf(chain), centre, size,
                itemCentre, itemSize, faceOrigin,
                heightScale * YamiConfig.headScale(), textureLocation, baseTextureLocation, expression);
        result.attachPart("AllHeadA", head, HEAD_ATTACHMENT_TOP_Y, false);
        result.setNamedParts(baked.getModelMap());
        return result;
    }

    private static boolean hasAll(AbstractBedrockModel baked, List<String> bones) {
        for (String bone : bones) {
            if (!baked.getModelMap().containsKey(bone)) {
                return false;
            }
        }
        return !bones.isEmpty();
    }

    /**
     * Puts every bone at the scale it has before anything is switched on, so the parts a model hides by
     * default - the six mouths and eight faces a wine fox parks off her head - stay hidden here too.
     */
    private static void applyDefaultScales(AbstractBedrockModel baked, Map<String, Float> scales) {
        for (Map.Entry<String, Float> scale : scales.entrySet()) {
            BedrockPart part = baked.getModelMap().get(scale.getKey());
            if (part != null && scale.getValue() != null) {
                part.xScale = part.yScale = part.zScale = scale.getValue();
            }
        }
    }

    /**
     * Uploads a texture for one model and returns the name it was registered under. The name is derived
     * from the cache key so a reload that rebuilds the same head reuses the same slot instead of piling
     * up textures.
     */
    private static ResourceLocation register(String key, byte[] png) throws IOException {
        NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID,
                "ysm_head/" + Integer.toHexString(key.hashCode()) + ".png");
        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
        return location;
    }

    /** The plain head used for a head with no model, and for one whose model this client cannot read. */
    public static synchronized MaidHeadModel fallback() {
        if (fallback == null) {
            byte[] json = read(YamiDelight.MODID, "ysm_head_fallback.json");
            byte[] texture = read(YamiDelight.MODID, "textures/entity/winefox_head.png");
            fallback = json == null || texture == null
                    ? null
                    : build(new String(json, StandardCharsets.UTF_8), null, texture, 1.0F, "fallback",
                            "the built in head", null, "");
        }
        return fallback;
    }

    @Nullable
    private static byte[] read(String namespace, String path) {
        return Minecraft.getInstance().getResourceManager()
                .getResource(ResourceLocation.fromNamespaceAndPath(namespace, path))
                .map(MaidHeadModels::bytes)
                .orElse(null);
    }

    @Nullable
    private static byte[] bytes(Resource resource) {
        try (InputStream stream = resource.open()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static void report(String key, String reason) {
        if (!YamiConfig.logMissingModels() || !REPORTED.add(key)) {
            return;
        }
        YamiDelight.LOGGER.info("yamidelight: no model for head {}: {}. Searched: {}",
                key.replace('\u0000', '/'), reason, String.join(", ", YsmModelLibrary.searchRoots()));
    }
}
