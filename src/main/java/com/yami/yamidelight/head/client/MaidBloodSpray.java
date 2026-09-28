package com.yami.yamidelight.head.client;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Client-only droplets emitted from the actual AllHead cube, after animation evaluation. */
public final class MaidBloodSpray {
    public static final int DURATION_TICKS = com.yami.yamidelight.head.MaidDeathTiming.animationTicks();
    public static final int BURST_COUNT = 50;
    public static final int PARTICLES_PER_TICK = 15;
    public static final float SPEED = 0.22F;
    private static final Map<Integer, Spray> SPRAYS = new HashMap<>();
    private static long budgetTick = Long.MIN_VALUE;
    private static int budget;
    private static final class Spray {
        long start, last = Long.MIN_VALUE;
        Matrix4f entry;
        Spray(long start) { this.start = start; }
    }
    private MaidBloodSpray() {}
    public static void begin(int id) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) SPRAYS.putIfAbsent(id, new Spray(level.getGameTime()));
    }
    public static void clear() { SPRAYS.clear(); budgetTick = Long.MIN_VALUE; }
    public static void tick() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { clear(); return; }
        SPRAYS.entrySet().removeIf(e -> level.getGameTime() - e.getValue().start >= DURATION_TICKS);
    }
    public static void capture(EntityMaid maid, PoseStack pose) {
        Spray spray = SPRAYS.get(maid.getId());
        if (spray != null) spray.entry = new Matrix4f(pose.last().pose());
    }
    public static void render(EntityMaid maid, PoseStack rendered, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Spray spray = SPRAYS.get(maid.getId());
        if (level == null || spray == null || spray.entry == null || mc.isPaused()
                || maid.isDeadOrDying() || !MaidHeadHider.isHeadless(maid.getId())) return;
        long now = level.getGameTime();
        if (now - spray.start >= DURATION_TICKS || spray.last == now
                || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(maid.position()) > 48 * 48) return;
        GeckoMaidEntity<?> gecko = maid.getData(GeckoMaidEntity.TYPE);
        if (gecko == null || gecko.getCurrentModel() == null) return;
        var model = gecko.getCurrentModel();
        AnimatedGeoBone source = model.bones().get("AllHead");
        // Only its own cube, never a group pivot or the head/hair descendants.
        if (source == null || source.geoBone().cubes().getCubeCount() == 0) return;
        var mesh = source.geoBone().cubes();
        var chain = new ArrayList<AnimatedGeoBone>();
        for (GeoBone bone = source.geoBone(); bone != null; bone = bone.parent()) {
            AnimatedGeoBone animated = model.bones().get(bone.name());
            if (animated == null) return;
            chain.add(0, animated);
        }
        PoseStack local = new PoseStack();
        // Remove the incoming camera/view transform; retain the renderer's actual
        // body yaw, model scale and animation transforms. No guessed neck height.
        local.mulPose(new Matrix4f(spray.entry).invert().mul(rendered.last().pose()));
        // The utility returns true for a fully zero-scaled (invisible) bone.
        for (AnimatedGeoBone bone : chain) if (RenderUtils.prepMatrixForBone(local, bone)) return;
        Matrix4f matrix = local.last().pose();
        Vector3f upward = new Vector3f(mesh.dy(0));
        matrix.transformDirection(upward);
        if (upward.lengthSquared() < 1.0E-10F) return;
        upward.normalize();
        int count = spray.last == Long.MIN_VALUE ? BURST_COUNT : PARTICLES_PER_TICK;
        spray.last = now;
        if (budgetTick != now) { budgetTick = now; budget = 256; }
        count = Math.min(count, budget);
        budget -= count;
        double x = Mth.lerp(partialTick, maid.xOld, maid.getX());
        double y = Mth.lerp(partialTick, maid.yOld, maid.getY());
        double z = Mth.lerp(partialTick, maid.zOld, maid.getZ());
        for (int i = 0; i < count; i++) {
            // Small patch on the first AllHead element's top face.
            Vector3f point = new Vector3f(mesh.position(0))
                    .fma(0.25F + level.random.nextFloat() * 0.5F, mesh.dx(0))
                    .add(mesh.dy(0))
                    .fma(0.25F + level.random.nextFloat() * 0.5F, mesh.dz(0));
            matrix.transformPosition(point);
            Vector3f velocity = new Vector3f((float) level.random.nextGaussian(),
                    (float) level.random.nextGaussian(), (float) level.random.nextGaussian()).mul(0.085F)
                    .fma(SPEED * (0.7F + level.random.nextFloat()), upward);
            mc.particleEngine.add(new BloodDrop(level, x + point.x, y + point.y, z + point.z, velocity));
        }
    }
    public static void smallBurst(net.minecraft.world.phys.Vec3 point, int count) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (int i=0; i<Math.max(3, Math.min(4,count)); i++) {
            var random = mc.level.random;
            var velocity = new Vector3f((random.nextFloat()-.5F)*.12F,
                    .1F+random.nextFloat()*.1F,(random.nextFloat()-.5F)*.12F);
            mc.particleEngine.add(new BloodDrop(mc.level,point.x,point.y,point.z,velocity));
        }
    }
    private static final class BloodDrop extends TerrainParticle {
        BloodDrop(ClientLevel level, double x, double y, double z, Vector3f velocity) {
            super(level, x, y, z, 0, 0, 0, Blocks.WHITE_CONCRETE.defaultBlockState());
            // Reuse the block atlas with a dark red tint; no external texture dependency.
            setColor(0.48F + random.nextFloat() * 0.35F, 0.005F, 0.015F);
            xd = velocity.x; yd = velocity.y; zd = velocity.z;
            gravity = 1.15F;
            friction = 0.94F;
            lifetime = 20 + random.nextInt(16);
            quadSize = 0.025F + random.nextFloat() * 0.04F;
        }
    }
}
