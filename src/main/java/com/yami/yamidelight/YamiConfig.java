package com.yami.yamidelight;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Gameplay tuning for the head drop and for how big a mounted head reads, written to
 * {@code config/yamidelight-common.toml}.
 *
 * <p>Everything here is a choice a pack author or a server admin would want to make without a
 * rebuild: whether heads drop at all, only from player kills, how often, and how large the head sits
 * on the wall. Everything about <em>which</em> geometry is drawn stays out of the config, because
 * that is the maid's own model and not a number to tune.
 */
public final class YamiConfig {
    public static final ModConfigSpec SPEC;
    /** Whether a maid killed by a player drops her head at all. */
    public static final ModConfigSpec.BooleanValue DROP_ENABLED;
    /** Whether the killing blow has to come from a player, or any death counts. */
    public static final ModConfigSpec.BooleanValue REQUIRE_PLAYER_KILL;
    /** Chance that an eligible death drops a head, 1.0 = always. */
    public static final ModConfigSpec.DoubleValue DROP_CHANCE;
    /** Extra scale on top of the model's own {@code height_scale}; 1.0 is the size she is in game. */
    public static final ModConfigSpec.DoubleValue HEAD_SCALE;
    /** How far the head floats off the wall, in blocks, after its own back face has been accounted for. */
    public static final ModConfigSpec.DoubleValue HEAD_GAP;
    /** Log once per model that could not be found, together with where the mod looked. */
    public static final ModConfigSpec.BooleanValue LOG_MISSING_MODELS;
    /** Log one line per maid that did or did not give up her head, and why. */
    public static final ModConfigSpec.BooleanValue LOG_DECISIONS;
    /** Animation the test sword plays on the maid before she falls over; the model file is dead.json. */
    public static final ModConfigSpec.ConfigValue<String> TEST_SWORD_ANIMATION;
    /** How long the test sword waits, in ticks, between taking the head and killing the maid. */
    public static final ModConfigSpec.IntValue TEST_SWORD_KILL_DELAY;
    /** Whether a hand edited face, shipped as a mesh in this mod, replaces the model's own parts. */
    public static final ModConfigSpec.BooleanValue EXPRESSION_ENABLED;
    /** Whether this mod's death clip replaces the one a maid model ships with. */
    public static final ModConfigSpec.BooleanValue DEATH_REPLACE_MODEL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("drop");
        DROP_ENABLED = builder
                .comment("Whether a maid using a YSM model drops her head when she is killed.")
                .define("enabled", true);
        REQUIRE_PLAYER_KILL = builder
                .comment("Whether the killing blow has to come from a player.",
                        "false also drops heads from mob kills, falls and explosions.")
                .define("require_player_kill", true);
        DROP_CHANCE = builder
                .comment("Chance that an eligible death drops a head. 1.0 is always, 0.5 is half.")
                .defineInRange("chance", 1.0D, 0.0D, 1.0D);
        builder.pop();
        builder.push("display");
        HEAD_SCALE = builder
                .comment("Extra scale on top of the model's own height_scale.",
                        "1.0 puts the head on the wall at exactly the size it has on the maid,",
                        "which for most models is around a third of a block; raise it for a",
                        "bigger trophy, lower it for a smaller one.")
                .defineInRange("scale", 1.0D, 0.05D, 8.0D);
        HEAD_GAP = builder
                .comment("Gap between the wall and the back of the head, in blocks.",
                        "0.0 means the head's bounding box touches the wall it is mounted on.",
                        "Raise it if a model's hair pokes through the block behind her.")
                .defineInRange("gap", 0.0D, -0.5D, 1.0D);
        LOG_MISSING_MODELS = builder
                .comment("Log once for each YSM model that cannot be found on this client,",
                        "listing the folders that were searched. Models are encrypted .ysm files",
                        "often enough that this is the difference between a bug and an expected miss.")
                .define("log_missing_models", true);
        builder.pop();
        builder.push("debug");
        LOG_DECISIONS = builder
                .comment("Log one line each time a maid is killed or hit, saying whether her head was",
                        "taken and why not when it was not. Small enough to leave on while testing.")
                .define("log_head_decisions", true);
        builder.pop();
        builder.push("test_sword");
        TEST_SWORD_ANIMATION = builder
                .comment("Animation the test sword has the maid play once her head is off, by the name",
                        "the model gives it. It is 'death' both in YSM models and in the animation",
                        "Touhou Little Maid plays while a maid is dying, which is the one this mod",
                        "supplies to maid models that lack it (extract-death-animation.cjs).")
                .define("death_animation", "death");
        TEST_SWORD_KILL_DELAY = builder
                .comment("Ticks between the head coming off and the maid dying. 20 ticks = 1 second,",
                        "which is long enough to watch the death animation play on a maid with no head.",
                        "Minimum is read from the bundled death clip: ceil(seconds * 20) + 10 ticks.",
                        "The current 4.5-second clip needs at least 100 ticks; older smaller values are clamped.")
                .defineInRange("kill_delay_ticks", 100, 0, 400);
        builder.pop();
        builder.push("expression");
        EXPRESSION_ENABLED = builder
                .comment("Whether the hand edited face shipped for a model (import-head-expression.cjs)",
                        "is pasted onto that model's heads, replacing the eyebrows, eyelids and mouth",
                        "it was drawn from. Turn it off to see the model exactly as it ships.")
                .define("enabled", true);
        builder.pop();
        builder.push("death_animation");
        DEATH_REPLACE_MODEL = builder
                .comment("Whether this mod's death animation replaces the one a maid model ships with.",
                        "It has to, for the wine fox models: their own death animation turns the maid",
                        "into a fox, which is what this mod's clip was made to undo. A model whose own",
                        "death animation is wanted back can be excluded by turning this off.")
                .define("replace_model_animation", true);
        builder.pop();
        SPEC = builder.build();
    }

    private YamiConfig() {}

    public static boolean dropEnabled() {
        return DROP_ENABLED.get();
    }

    public static boolean requirePlayerKill() {
        return REQUIRE_PLAYER_KILL.get();
    }

    public static double dropChance() {
        return DROP_CHANCE.get();
    }

    public static float headScale() {
        return HEAD_SCALE.get().floatValue();
    }

    public static float headGap() {
        return HEAD_GAP.get().floatValue();
    }

    public static boolean logMissingModels() {
        return LOG_MISSING_MODELS.get();
    }

    public static boolean logDecisions() {
        return LOG_DECISIONS.get();
    }

    public static String testSwordAnimation() {
        return TEST_SWORD_ANIMATION.get();
    }

    public static int testSwordKillDelay() {
        return TEST_SWORD_KILL_DELAY.get();
    }

    public static boolean expressionEnabled() {
        return EXPRESSION_ENABLED.get();
    }

    public static boolean replaceModelDeath() {
        return DEATH_REPLACE_MODEL.get();
    }
}
