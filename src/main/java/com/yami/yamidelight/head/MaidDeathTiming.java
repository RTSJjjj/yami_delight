package com.yami.yamidelight.head;

import com.google.gson.JsonParser;
import com.yami.yamidelight.YamiDelight;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Server-safe timing read from the same bundled clip that the client plays. */
public final class MaidDeathTiming {
    private static final int ANIMATION_TICKS = readTicks();
    private MaidDeathTiming() {}
    public static int animationTicks() { return ANIMATION_TICKS; }
    public static int minimumKillDelay() { return ANIMATION_TICKS + 10; }
    private static final int HANGING_TICKS = readHangingTicks();
    public static int hangingTicks() { return HANGING_TICKS; }
    private static int readHangingTicks() {
        try (var stream = MaidDeathTiming.class.getResourceAsStream("/assets/yamidelight/animation/maid/death2.json")) {
            var clip = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations").getAsJsonObject("death2");
            double seconds = clip.get("animation_length").getAsDouble();
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 3600) throw new IllegalArgumentException();
            return (int)Math.ceil(seconds * 20);
        } catch (Exception e) {
            YamiDelight.LOGGER.error("yamidelight: cannot read death2 timing; using 180 ticks", e);
            return 180;
        }
    }
    private static int readTicks() {
        try (var stream = MaidDeathTiming.class.getResourceAsStream(
                "/assets/yamidelight/animation/maid/death.json")) {
            if (stream == null) throw new IllegalStateException("Missing death animation");
            var clips = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            var clip = clips.getAsJsonObject(clips.has("dead") ? "dead" : "death");
            double seconds = clip.get("animation_length").getAsDouble();
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 3600)
                throw new IllegalArgumentException("Invalid animation length: " + seconds);
            return (int) Math.ceil(seconds * 20.0D);
        } catch (Exception e) {
            YamiDelight.LOGGER.error("yamidelight: cannot read death timing; using 90 ticks", e);
            return 90;
        }
    }
}
