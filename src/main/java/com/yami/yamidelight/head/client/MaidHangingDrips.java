package com.yami.yamidelight.head.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yami.yamidelight.YamiDelight;
import com.yami.yamidelight.head.MaidHanging;
import com.yami.yamidelight.head.MaidDeathTiming;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** UpBody1's authored lower surface, animated with UpBody; velocity stays vertical in world space. */
public final class MaidHangingDrips {
    private static final Map<EntityMaid, Emission> EMITTERS = new WeakHashMap<>();
    private static JsonObject settings;
    private static long budgetTick = Long.MIN_VALUE;
    private static int budget;
    private static final class Emission {
        Matrix4f entry;
        long tick = Long.MIN_VALUE;
        double credit;
        long nextIdleDrop = Long.MIN_VALUE;
    }
    private MaidHangingDrips() {}
    public static void clear() { EMITTERS.clear(); settings = null; budgetTick = Long.MIN_VALUE; }
    public static void capture(EntityMaid maid, PoseStack pose) {
        if (!MaidHanging.active(maid)) { EMITTERS.remove(maid); return; }
        EMITTERS.computeIfAbsent(maid, ignored -> new Emission()).entry = new Matrix4f(pose.last().pose());
    }
    private static JsonObject settings() {
        if (settings == null) {
            try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                    ResourceLocation.fromNamespaceAndPath(YamiDelight.MODID, "death2_drips.json")).open()) {
                settings = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load death2 drip marker", e); }
        }
        return settings;
    }
    public static void render(EntityMaid maid, ILocationModel location, PoseStack rendered, float partialTick) {
        var mc = Minecraft.getInstance();
        var emitter = EMITTERS.get(maid);
        if (!MaidHanging.active(maid) || maid.isDeadOrDying() || maid.isInvisible() || mc.isPaused()
                || mc.level == null || emitter == null || emitter.entry == null
                || !(location instanceof AnimatedGeoModel model)) return;
        long now = mc.level.getGameTime();
        if (emitter.tick == now || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(maid.position()) > 48 * 48) return;
        var config = settings();
        double age = now - MaidHanging.state(maid).getLong("client_start");
        double start = config.get("start_seconds").getAsDouble() * 20;
        double end = MaidDeathTiming.hangingTicks();
        if (age < start || end <= start) return;
        var source = model.bones().get(config.get("bone").getAsString());
        if (source == null) return;
        var chain = new ArrayList<AnimatedGeoBone>();
        for (GeoBone bone = source.geoBone(); bone != null; bone = bone.parent()) {
            var animated = model.bones().get(bone.name());
            if (animated == null || animated.isHidden()) return;
            chain.add(0, animated);
        }
        PoseStack local = new PoseStack();
        local.mulPose(new Matrix4f(emitter.entry).invert().mul(rendered.last().pose()));
        for (var bone : chain) if (RenderUtils.prepMatrixForBone(local, bone)) return;
        emitter.tick = now;
        int count;
        if (age >= end) {
            // Held final pose: occasional isolated drops, never a replay of the animation burst.
            int minimum = config.has("hold_min_interval_ticks") ? config.get("hold_min_interval_ticks").getAsInt() : 40;
            int maximum = config.has("hold_max_interval_ticks") ? config.get("hold_max_interval_ticks").getAsInt() : 100;
            minimum = Math.max(1, minimum); maximum = Math.max(minimum, maximum);
            if (emitter.nextIdleDrop == Long.MIN_VALUE) {
                emitter.credit = 0;
                emitter.nextIdleDrop = now + minimum + mc.level.random.nextInt(maximum - minimum + 1);
            }
            if (now < emitter.nextIdleDrop) return;
            count = 1 + mc.level.random.nextInt(2);
            emitter.nextIdleDrop = now + minimum + mc.level.random.nextInt(maximum - minimum + 1);
        } else {
            double seconds = (age - start) / 20.0;
            double decay = config.has("decay_per_second") ? config.get("decay_per_second").getAsDouble() : 0.7;
            double period = config.has("pulse_period_seconds") ? config.get("pulse_period_seconds").getAsDouble() : 1.0;
            // Nonnegative sine pulses under an exponential envelope; start at the first crest.
            double pulse = (1 + Math.sin(2 * Math.PI * seconds / Math.max(0.05, period) + Math.PI / 2)) * 0.5;
            double rate = config.get("particles_per_tick").getAsDouble() * Math.exp(-Math.max(0, decay) * seconds) * pulse;
            emitter.credit += rate;
            count = (int)emitter.credit;
            emitter.credit -= count;
        }
        if (budgetTick != now) { budgetTick = now; budget = 64; }
        count = Math.min(count, budget); budget -= count;
        var from = config.getAsJsonArray("from"); var to = config.getAsJsonArray("to");
        var color = config.getAsJsonArray("color");
        double x = Mth.lerp(partialTick, maid.xOld, maid.getX());
        double y = Mth.lerp(partialTick, maid.yOld, maid.getY());
        double z = Mth.lerp(partialTick, maid.zOld, maid.getZ());
        for (int i = 0; i < count; i++) {
            var point = new Vector3f(
                    Mth.lerp(mc.level.random.nextFloat(), from.get(0).getAsFloat(), to.get(0).getAsFloat()) / 16F,
                    from.get(1).getAsFloat() / 16F,
                    Mth.lerp(mc.level.random.nextFloat(), from.get(2).getAsFloat(), to.get(2).getAsFloat()) / 16F);
            local.last().pose().transformPosition(point);
            mc.particleEngine.add(new Drop(mc.level, x + point.x, y + point.y, z + point.z,
                    20, config.get("alpha").getAsFloat(),
                    color.get(0).getAsFloat(), color.get(1).getAsFloat(), color.get(2).getAsFloat()));
        }
    }
    private static final class Drop extends TextureSheetParticle {
        private final float initialAlpha;
        Drop(ClientLevel level, double x, double y, double z, int duration, float opacity, float r, float g, float b) {
            super(level, x, y, z);
            var atlas = (TextureAtlas)Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
            setSprite(atlas.getSprite(ResourceLocation.withDefaultNamespace("drip_fall")));
            setColor(r, g, b);
            initialAlpha = opacity; alpha = opacity;
            xd = zd = 0; yd = -0.08;
            gravity = 0.55F; friction = 1F;
            // DripParticle inherits this exact SingleQuadParticle size distribution.
            quadSize = 0.1F * (random.nextFloat() * 0.5F + 0.5F) * 2.0F;
            setSize(0.01F, 0.01F);
            lifetime = duration;
        }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
        @Override public void tick() {
            xd = zd = 0;
            super.tick();
            // Preserve the final few drops across the clip boundary; fade only near their own end.
            alpha = initialAlpha * Math.min(1F, Math.max(0F, (lifetime - age) / 6F));
            if (onGround) remove();
        }
    }
}
