package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.AbstractBedrockModel;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.yami.yamidelight.YamiDelight;
import com.yami.yamidelight.head.MaidHeadData;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Independent cache: each body retains its original skin; only missing models use Taisho. */
public final class MaidBodyModels {
    private static final Map<String, Optional<MaidHeadModel>> CACHE = new HashMap<>();
    private static MaidHeadModel fallback;
    private static boolean triedFallback;
    private static MaidHeadModel remainsFallback;
    private static boolean triedRemains;
    private MaidBodyModels() {}

    public static void invalidate() {
        CACHE.clear();
        fallback = null;
        triedFallback = false;
        remainsFallback = null; triedRemains = false;
        MaidFinalPose.invalidate();
    }

    public static MaidHeadModel get(MaidHeadData data) {
        return get(data, false);
    }

    public static MaidHeadModel get(MaidHeadData data, boolean remains) {
        if (data.hasModel()) {
            String key = remains + "/" + data.source() + '\u0000' + data.modelId() + '\u0000' + data.textureId();
            MaidHeadModel found = CACHE.computeIfAbsent(key, ignored -> Optional.ofNullable(load(data, remains))).orElse(null);
            if (found != null) return found;
        }
        if (remains) {
            if (!triedRemains) {
                triedRemains = true;
                try { remainsFallback = build(read("winefox_remains_fallback.json"), read("winefox_remains_defaults.json"),
                        read("textures/entity/winefox_remains.png"), 0.65F, "remains_fallback", true); }
                catch (Exception e) { YamiDelight.LOGGER.warn("yamidelight: cannot load remains", e); }
            }
            return remainsFallback;
        }
        if (!triedFallback) {
            triedFallback = true;
            try {
                fallback = build(read("winefox_body_fallback.json"), null, read("textures/entity/winefox_body.png"),
                        1.0F, "fallback", false);
            } catch (Exception e) {
                YamiDelight.LOGGER.warn("yamidelight: cannot load default body", e);
            }
        }
        return fallback;
    }

    private static MaidHeadModel load(MaidHeadData data, boolean remains) {
        try {
            String key = remains + "/" + data.source() + "/" + data.modelId() + "/" + data.textureId();
            if (data.isYsm()) {
                var entry = YsmModelLibrary.find(data);
                if (entry != null) return build(entry.modelJson(), entry.animationJson(),
                        entry.texture(data.textureId()), entry.heightScale(), key, remains);
            } else {
                var model = TlmModelLibrary.load(data.modelId());
                if (model != null) return build(model.geometry(), model.animation(), model.texture(), model.scale(), key, remains);
            }
        } catch (Exception e) {
            YamiDelight.LOGGER.warn("yamidelight: cannot load body {}: {}", data.modelId(), e.toString());
        }
        return null;
    }

    private static byte[] read(String path) throws java.io.IOException {
        var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, path));
        try (var stream = resource.open()) { return stream.readAllBytes(); }
    }

    private static MaidHeadModel build(byte[] bytes, byte[] animation, byte[] texture, float scale, String key, boolean remains)
            throws java.io.IOException {
        if (bytes == null || texture == null) return null;
        String json = new String(bytes, StandardCharsets.UTF_8);
        BedrockGeometry measured = BedrockGeometry.parse(json);
        if (measured == null) return null;
        JsonObject document = JsonParser.parseString(json).getAsJsonObject();
        JsonObject geometry = null;
        if (document.has("minecraft:geometry")) geometry = document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        else for (var entry : document.entrySet()) {
            if (entry.getKey().startsWith("geometry") && entry.getValue().isJsonObject()
                    && entry.getValue().getAsJsonObject().has("bones")) {
                geometry = entry.getValue().getAsJsonObject();
                break;
            }
        }
        if (geometry == null) return null;
        var hidden = new HashSet<String>();
        String head = measured.headBone();
        // Reuse the authored trophy face for this compatible head rig, including nine-tail variants
        // whose mouth names differ. Remove whole expression branches before baking/final-pose sampling.
        HeadExpression.Mesh expression = null;
        if (remains && head != null && measured.pivotOf("RightEyelid") != null
                && measured.pivotOf("LeftEyelid") != null
                && measured.pivotOf("RightEyebrow") != null && measured.pivotOf("LeftEyebrow") != null) {
            expression = HeadExpression.forModel("geckolib:winefox");
            if (expression != null) {
                hidden.addAll(expression.hide());
                if (measured.pivotOf("Eyes") != null) hidden.add("Eyes");
                if (measured.pivotOf("Mouth") != null) hidden.add("Mouth");
            }
        }
        float[] hookAnchor = measured.pivotOf("AllHead");
        if (hookAnchor == null && head != null) hookAnchor = measured.pivotOf(head);
        // Never return a complete maid when an unfamiliar rig has no identifiable head.
        if (!remains && head == null && !key.equals("fallback")) return null;
        if (!remains && head != null) hidden.add(head);
        var defaults = BoneDefaults.scales(animation);
        JsonArray bones = geometry.getAsJsonArray("bones");
        for (var element : bones) {
            String name = element.getAsJsonObject().get("name").getAsString();
            if (name.toLowerCase(java.util.Locale.ROOT).startsWith("fox")
                    || (!name.equals("AllBody") && defaults.getOrDefault(name, 1F) == 0F)) hidden.add(name);
        }
        var removed = new HashSet<String>();
        for (String name : hidden) removed.addAll(measured.subtree(name));
        JsonArray visible = new JsonArray();
        var names = new HashSet<String>();
        for (var element : bones) {
            var bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            // Replace only AllHead's own element, retaining the bone and its descendants.
            if (!remains && name.equals("AllHead")) bone.add("cubes", new JsonArray());
            if (!removed.contains(name)) { visible.add(bone); names.add(name); }
        }
        String rootName = "yamidelight_body_root";
        while (names.contains(rootName)) rootName += "_";
        for (var element : visible) {
            var bone = element.getAsJsonObject();
            if (!bone.has("parent") || !names.contains(bone.get("parent").getAsString())) bone.addProperty("parent", rootName);
        }
        JsonObject root = new JsonObject();
        root.addProperty("name", rootName);
        root.add("pivot", JsonParser.parseString("[0,24,0]"));
        visible.add(root);
        geometry.add("bones", visible);
        json = document.toString();
        BedrockGeometry bodyGeometry = BedrockGeometry.parse(json);
        float[] bounds = bodyGeometry == null ? null : bodyGeometry.measure(rootName);
        if (bounds == null) return null;
        var baked = new AbstractBedrockModel(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))) {};
        if (!remains) {
            bounds = bodyGeometry.measureBaked(rootName, baked.getModelMap());
            if (bounds == null) return null;
        }
        if (remains) {
            MaidFinalPose.apply(baked.getModelMap());
            bounds = bodyGeometry.measureBaked(rootName, baked.getModelMap());
            if (bounds == null) return null;
        }
        var rootPart = baked.getModelMap().get(rootName);
        if (rootPart == null) return null;
        if (!remains) bounds = AllHeadParts.extendBounds(bounds, "AllHead", baked.getModelMap().get("AllHead"), 30.7F);
        var location = ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID,
                "winefox_body/" + java.util.UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)) + ".png");
        Minecraft.getInstance().getTextureManager().register(location,
                new DynamicTexture(NativeImage.read(new ByteArrayInputStream(texture))));
        var centre = new Vector3f(bounds[0], bounds[1], bounds[2]);
        var size = new Vector3f(bounds[3], bounds[4], bounds[5]);
        // The expression's vertices use the original winefox model frame (MHead pivot y=31.7px).
        // Keep that origin even when the destination rig's head pivot differs.
        var result = new MaidHeadModel(rootPart, List.of(), centre, size, centre, size,
                new Vector3f(0, (24F - 31.7F) / 16F, 0), scale, location, location, expression);
        if (expression != null) {
            String skull = measured.skull(head);
            var anchor = baked.getModelMap().get(skull == null ? head : skull);
            result.attachExpression(anchor, location);
        }
        result.setHookAnchor(hookAnchor);
        result.setTailRoot(baked.getModelMap().get("MTail"));
        if (!remains && baked.getModelMap().containsKey("AllHead")) {
            result.attachPart("AllHead", baked.getModelMap().get("AllHead"), 30.7F, true);
        }
        result.setNamedParts(baked.getModelMap());
        return result;
    }
}
