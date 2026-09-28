package com.yami.yamidelight.head.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * The scale a model's bones have in its default state, taken from the model's own animations.
 *
 * <p>This is what keeps a cut out head clean. YSM models hide the parts that are not in use by scaling
 * them to nothing in the animation that is always running - a wine fox ships six mouth shapes and
 * eight face expressions, each parked somewhere off the head and switched on with
 * {@code scale: "(v.exp==6)?1:0"}. Play no animation and all of them are drawn at once, floating
 * around the head.
 *
 * <p>Only the scale channel is read, and only from expressions simple enough to answer without a
 * Molang engine: a literal number, or the {@code (v.name == n) ? a : b} switch with number branches.
 * Every variable is taken to be at its default of zero, which is exactly the question being asked -
 * "what is visible before anything is switched on". A bone whose scale cannot be answered this way is
 * left alone and stays visible, and anything guarded by a Touhou Little Maid variable is left visible
 * too, because those variables are about being a maid rather than about the model's own state.
 */
final class BoneDefaults {
    /** {@code (v.exp==6)?1:0} and its mirrors: the switch YSM models hide parts with. */
    private static final Pattern SWITCH = Pattern.compile(
            "^\\(\\s*v\\.([A-Za-z0-9_.]+)\\s*(==|!=)\\s*(-?\\d+(?:\\.\\d+)?)\\s*\\)\\s*\\?\\s*"
                    + "(-?\\d+(?:\\.\\d+)?)\\s*:\\s*(-?\\d+(?:\\.\\d+)?)$");
    private static final float VISIBLE = 1.0F;

    private BoneDefaults() {}

    /**
     * Scale per bone across every animation in one file. The smallest value wins: two parallel
     * animations can both touch a bone, and a bone that anything hides is a bone the player never sees.
     * An unreadable file or expression is simply absent from the result.
     */
    static Map<String, Float> scales(@Nullable byte[] animationJson) {
        Map<String, Float> scales = new HashMap<>();
        if (animationJson == null) {
            return scales;
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(new String(animationJson, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return scales;
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return scales;
        }
        JsonElement animations = root.get("animations");
        if (animations == null || !animations.isJsonObject()) {
            return scales;
        }
        for (Entry<String, JsonElement> animation : animations.getAsJsonObject().entrySet()) {
            if (!animation.getValue().isJsonObject()) {
                continue;
            }
            JsonElement bones = animation.getValue().getAsJsonObject().get("bones");
            if (bones == null || !bones.isJsonObject()) {
                continue;
            }
            for (Entry<String, JsonElement> bone : bones.getAsJsonObject().entrySet()) {
                if (!bone.getValue().isJsonObject()) {
                    continue;
                }
                JsonElement scale = bone.getValue().getAsJsonObject().get("scale");
                Float value = scale == null ? null : firstValue(scale);
                if (value != null) {
                    scales.merge(bone.getKey(), value, Math::min);
                }
            }
        }
        return scales;
    }

    /** The value at the start of a channel: either the value itself or its earliest keyframe. */
    @Nullable
    private static Float firstValue(JsonElement channel) {
        Float plain = evaluate(channel);
        if (plain != null) {
            return plain;
        }
        if (!channel.isJsonObject()) {
            return null;
        }
        // {"0.0": {"post": 1.0, "pre": ...}} - the first keyframe is the pose at time zero.
        List<String> times = new ArrayList<>(channel.getAsJsonObject().keySet());
        times.sort(BoneDefaults::compareTimes);
        for (String time : times) {
            JsonElement keyframe = channel.getAsJsonObject().get(time);
            if (keyframe == null || !keyframe.isJsonObject()) {
                continue;
            }
            JsonObject frame = keyframe.getAsJsonObject();
            for (String field : List.of("post", "pre")) {
                JsonElement value = frame.get(field);
                if (value == null) {
                    continue;
                }
                Float evaluated = evaluate(value);
                if (evaluated != null) {
                    return evaluated;
                }
            }
        }
        return null;
    }

    private static int compareTimes(String left, String right) {
        try {
            return Double.compare(Double.parseDouble(left), Double.parseDouble(right));
        } catch (NumberFormatException e) {
            return left.compareTo(right);
        }
    }

    /** A literal number, or the switch pattern above evaluated with every variable at zero. */
    @Nullable
    private static Float evaluate(JsonElement value) {
        if (value == null) {
            return null;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return value.getAsFloat();
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            return null;
        }
        String expression = value.getAsString().trim();
        Float literal = literal(expression);
        if (literal != null) {
            return literal;
        }
        Matcher matcher = SWITCH.matcher(expression);
        if (!matcher.matches()) {
            return null;
        }
        String variable = matcher.group(1).toLowerCase(Locale.ROOT);
        if (variable.startsWith("tlm") || variable.startsWith("maid")) {
            return VISIBLE;
        }
        Float whenTrue = literal(matcher.group(4));
        Float whenFalse = literal(matcher.group(5));
        if (whenTrue == null || whenFalse == null) {
            return null;
        }
        double compared = Double.parseDouble(matcher.group(3));
        boolean matches = matcher.group(2).equals("==") ? compared == 0.0D : compared != 0.0D;
        return matches ? whenTrue : whenFalse;
    }

    @Nullable
    private static Float literal(String text) {
        try {
            if (text.endsWith("f") || text.endsWith("F")) {
                text = text.substring(0, text.length() - 1);
            }
            return Float.parseFloat(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
