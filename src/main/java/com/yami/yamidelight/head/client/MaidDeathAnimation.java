package com.yami.yamidelight.head.client;

import com.github.tartaricacid.touhoulittlemaid.client.resource.GeckoModelLoader;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.file.AnimationFile;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.resource.GeckoLibCache;
import com.yami.yamidelight.YamiConfig;
import com.yami.yamidelight.YamiDelight;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Gives the maid models the death animation they are missing.
 *
 * <p>Touhou Little Maid already knows what to do with an animation called {@code death}: it registers
 * that name as a state that plays once, gated on {@code maid.isDeadOrDying()}. Her own wine fox models
 * are ports of the Yes Steve Model ones and kept their bone names - {@code Root}, {@code AllBody},
 * {@code UpperBody}, {@code AllHead}, {@code Head} - so a YSM death animation drives them directly. What
 * the models do not ship is the animation itself, and that is the one thing this adds: the extracted
 * {@code death} clip from a YSM file, merged into every maid model that does not define one of its own.
 *
 * <p>The merge goes through Touhou Little Maid's own animation loader, so the animation ends up in the
 * same file the model's controller reads from, and a model that already has a death animation keeps it.
 * Models are loaded when the resource manager reloads and again when a model pack is downloaded while
 * playing, which is why this re-runs whenever the set of loaded models grows rather than only once.
 */
public final class MaidDeathAnimation {
    private static final ResourceLocation SOURCE =
            ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "animation/maid/death.json");
    private static final String NAME = "yamidelight_dead";
    private static int appliedTo = -1;
    private static boolean reported;

    private MaidDeathAnimation() {}

    /** Re-runs the merge when the loaded model set changed since the last look. */
    public static void tick() {
        Map<ResourceLocation, AnimationFile> models = GeckoLibCache.getInstance().getAnimations();
        if (models.size() != appliedTo || models.values().stream()
                .anyMatch(file -> file != null && (!file.animations().containsKey(NAME)
                        || !file.animations().containsKey("yamidelight_hanging")))) {
            appliedTo = models.size();
            apply(models);
        }
    }

    public static void invalidate() {
        appliedTo = -1;
    }

    private static void apply(Map<ResourceLocation, AnimationFile> models) {
        if (models.isEmpty()) {
            return;
        }
        byte[] animation = read();
        if (animation == null) {
            return;
        }
        int added = 0;
        for (Map.Entry<ResourceLocation, AnimationFile> model : models.entrySet()) {
            AnimationFile file = model.getValue();
            if (file == null) {
                continue;
            }
            if (file.animations().containsKey(NAME) && file.animations().containsKey("yamidelight_hanging")) continue;
            try (InputStream stream = new ByteArrayInputStream(animation)) {
                GeckoModelLoader.mergeAnimationFile(stream, file);
                added++;
            } catch (IOException | RuntimeException e) {
                report(e);
                return;
            }
        }
        if (added > 0) {
            YamiDelight.LOGGER.info("yamidelight: gave {} maid models this mod's death animation", added);
        }
    }

    @Nullable
    private static byte[] read() {
        return Minecraft.getInstance().getResourceManager().getResource(SOURCE).map(resource -> {
            try (InputStream stream = resource.open()) {
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseReader(
                        new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                com.google.gson.JsonObject clips = root.getAsJsonObject("animations");
                com.google.gson.JsonElement clip = clips.has("dead") ? clips.get("dead") : clips.get("death");
                com.google.gson.JsonObject renamed = new com.google.gson.JsonObject();
                renamed.add(NAME, clip);
                var hangingResource = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                        ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "animation/maid/death2.json"));
                try (var hangingStream = hangingResource.open()) {
                    var hanging = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(
                            hangingStream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
                            .getAsJsonObject("animations").get("death2");
                    renamed.add("yamidelight_hanging", hanging);
                }
                root.add("animations", renamed);
                return root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                report(e);
                return null;
            }
        }).orElseGet(() -> {
            report(null);
            return null;
        });
    }

    private static void report(@Nullable Exception e) {
        if (!reported) {
            reported = true;
            YamiDelight.LOGGER.warn("yamidelight: could not hand the death animation to Touhou Little Maid "
                    + "({}); maids will fall over without playing it", e == null ? "resource missing" : e);
        }
    }
}
