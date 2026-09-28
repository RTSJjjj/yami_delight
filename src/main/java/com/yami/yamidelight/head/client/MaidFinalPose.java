package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yami.yamidelight.YamiDelight;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Evaluates the literal channels in death2 at its end, including held channels that end before 9s. */
public final class MaidFinalPose {
    private static JsonObject clip;
    private MaidFinalPose() {}
    static void invalidate() { clip = null; }

    static void apply(Map<String, BedrockPart> parts) throws java.io.IOException {
        load();
        double end = clip.get("animation_length").getAsDouble();
        for (var entry : clip.getAsJsonObject("bones").entrySet()) {
            BedrockPart part = parts.get(entry.getKey());
            if (part == null) continue;
            var channels = entry.getValue().getAsJsonObject();
            float[] rotation = value(channels.get("rotation"), end, 0F);
            float[] position = value(channels.get("position"), end, 0F);
            float[] scale = value(channels.get("scale"), end, 1F);
            part.xRot += (float)Math.toRadians(rotation[0]);
            part.yRot += (float)Math.toRadians(rotation[1]);
            part.zRot += (float)Math.toRadians(rotation[2]);
            part.x += position[0]; part.y -= position[1]; part.z += position[2];
            part.xScale = scale[0]; part.yScale = scale[1]; part.zScale = scale[2];
        }
    }

    private static void load() throws java.io.IOException {
        if (clip == null) {
            var resource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                    ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "animation/maid/death2.json"));
            try (var stream = resource.open()) {
                clip = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animations").getAsJsonObject("death2");
            }
        }
    }

    private static boolean reported;
    /** Apply after the model's own look/parallel animations; late tracking keeps the correct phase. */
    public static void applyLive(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid,
            com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel location, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack pose) {
        if (!com.yami.yamidelight.head.MaidHanging.engaged(maid)
                || !(location instanceof com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel model)) return;
        try {
            load();
            // Suppress idle ear/blink/look channels after parallel animation evaluation.
            // Authored death2 channels below can still deliberately pose these bones.
            for (String name : java.util.List.of("Left_ear", "Right_ear", "LeftEar", "RightEar",
                    "LeftEyebrow", "RightEyebrow", "LeftEyelid", "RightEyelid",
                    "LeftEyelidBase", "RightEyelidBase", "LeftEyePublic", "RightEyePublic",
                    "LeftEyeDot", "RightEyeDot")) {
                var bone = model.bones().get(name);
                if (bone == null) continue;
                var initial = bone.getInitialSnapshot();
                bone.setRotation(initial.rotationValueX, initial.rotationValueY, initial.rotationValueZ);
                bone.setPosition(0, 0, 0);
                if (name.equals("LeftEyelid") || name.equals("RightEyelid")) bone.setScale(1, 1, 1);
            }
            if (!com.yami.yamidelight.head.MaidHanging.active(maid)) return;
            double end = clip.get("animation_length").getAsDouble();
            double elapsed = Math.max(0,
                    (maid.level().getGameTime() - com.yami.yamidelight.head.MaidHanging.state(maid).getLong("client_start") + partialTick) / 20.0);
            double t = Math.min(end, elapsed);
            for (var entry : clip.getAsJsonObject("bones").entrySet()) {
                var bone = model.bones().get(entry.getKey());
                if (bone == null) continue;
                var initial = bone.getInitialSnapshot();
                var channels = entry.getValue().getAsJsonObject();
                var r = value(channels.get("rotation"), t, 0F);
                var p = value(channels.get("position"), t, 0F);
                var s = value(channels.get("scale"), t, 1F);
                // Matches TLM RotationValue: X/Y flipped, Z unchanged, all in radians.
                bone.setRotation(initial.rotationValueX - (float)Math.toRadians(r[0]),
                        initial.rotationValueY - (float)Math.toRadians(r[1]),
                        initial.rotationValueZ + (float)Math.toRadians(r[2]));
                bone.setPosition(p[0], p[1], p[2]);
                bone.setScale(s[0], s[1], s[2]);
            }
            // Tail2 is not overridden by death2: retain the model's evaluated tail motion
            // and reuse its lateral yaw for both legs instead of running a separate oscillator.
            // Recomputed on top of the freshly evaluated pose, so rotations cannot accumulate.
            if (elapsed > end) {
                double held = elapsed - end;
                double fade = Math.min(1.0, held / 1.5);
                fade = fade * fade * (3.0 - 2.0 * fade);
                var tail = model.bones().get("Tail2");
                if (tail != null) {
                    float yaw = tail.getRotationY() - tail.getInitialSnapshot().rotationValueY;
                    // Smoothly limit unusual model poses to a gentle +/-2 degrees without clipping.
                    float swing = (float)(Math.toRadians(2.0) * Math.tanh(yaw / Math.toRadians(20.0)) * fade);
                    MaidHangingSway.sample(maid, swing * 0.6F);
                    for (String name : java.util.List.of("LeftLeg", "RightLeg")) {
                        var bone = model.bones().get(name);
                        if (bone != null && clip.getAsJsonObject("bones").has(name)) bone.addRotationZ(swing);
                    }
                }
            }
            MaidHangingSway.apply(maid, pose);
            var body = model.bones().get("AllBody"); var fox = model.bones().get("FOX");
            if (body != null) body.setScale(1F, 1F, 1F);
            if (fox != null) fox.setScale(0F, 0F, 0F);
        } catch (Exception e) {
            if (!reported) { reported = true; YamiDelight.LOGGER.warn("yamidelight: cannot apply death2 pose", e); }
        }
    }

    private static float[] value(JsonElement channel, double time, float fallback) {
        if (channel == null) return new float[] {fallback, fallback, fallback};
        if (channel.isJsonObject()) {
            JsonObject object = channel.getAsJsonObject();
            if (object.has("post")) return value(object.get("post"), time, fallback);
            if (object.has("pre")) return value(object.get("pre"), time, fallback);
            double latest = -Double.MAX_VALUE;
            double nextTime = Double.MAX_VALUE;
            JsonElement selected = null;
            JsonElement next = null;
            for (var entry : object.entrySet()) {
                double t = Double.parseDouble(entry.getKey());
                if (t <= time && t > latest) { latest = t; selected = entry.getValue(); }
                if (t > time && t < nextTime) { nextTime = t; next = entry.getValue(); }
            }
            if (selected == null) return value(next, time, fallback);
            float[] start = value(selected, time, fallback);
            if (next == null) return start;
            float[] finish = value(next, time, fallback);
            float blend = (float)((time - latest) / (nextTime - latest));
            for (int axis = 0; axis < 3; axis++) start[axis] += (finish[axis] - start[axis]) * blend;
            return start;
        }
        if (channel.isJsonArray()) {
            var v = channel.getAsJsonArray();
            return new float[] {v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat()};
        }
        float scalar = channel.getAsFloat();
        return new float[] {scalar, scalar, scalar};
    }
}
