package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yami.yamidelight.YamiDelight;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Rotation deltas and shared block frame extracted from A.bbmodel. */
final class MaidHookPose {
    private static JsonObject settings;
    static void invalidate() { settings = null; }
    private static JsonObject settings() {
        if (settings == null) {
            try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                    ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "winefox_body_hook_pose.json")).open()) {
                settings = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read the hook reference pose", e); }
        }
        return settings;
    }
    static void apply(Map<String, BedrockPart> parts) {
        for (var entry : settings().getAsJsonObject("rotations").entrySet()) {
            var part = parts.get(entry.getKey());
            if (part == null) continue;
            var r = entry.getValue().getAsJsonArray();
            part.xRot += (float)Math.toRadians(r.get(0).getAsDouble());
            part.yRot += (float)Math.toRadians(r.get(1).getAsDouble());
            part.zRot += (float)Math.toRadians(r.get(2).getAsDouble());
        }
    }
    static float offset(int axis) { return settings().getAsJsonArray("reference_offset_pixels").get(axis).getAsFloat(); }
}
