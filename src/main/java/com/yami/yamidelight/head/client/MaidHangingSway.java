package com.yami.yamidelight.head.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yami.yamidelight.head.MaidDeathTiming;
import com.yami.yamidelight.head.MaidHanging;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** A shared visual pendulum transform for the model and its lower leash endpoint. */
public final class MaidHangingSway {
    private static final Map<EntityMaid, Frame> FRAMES = new WeakHashMap<>();
    private static final class Frame {
        float nextAngle;
        double yaw;
        Matrix4f entry;
        Matrix4f swing;
        boolean rendering;
    }
    private MaidHangingSway() {}

    public static void begin(EntityMaid maid, PoseStack pose, float partialTick) {
        var holder = maid.getLeashHolder();
        if (!MaidHanging.active(maid) || holder == null || !holder.isAlive()
                || maid.level().getGameTime() - MaidHanging.state(maid).getLong("client_start")
                    + partialTick <= MaidDeathTiming.hangingTicks()) {
            FRAMES.remove(maid);
            return;
        }
        Frame frame = FRAMES.computeIfAbsent(maid, ignored -> new Frame());
        // TLM draws the leash before evaluating this frame's animation. Use the
        // last sampled tail angle for BOTH passes, never different frame phases.
        frame.entry = new Matrix4f(pose.last().pose());
        frame.yaw = Math.toRadians(Mth.lerp(partialTick, maid.yBodyRot, maid.yBodyRotO)) + Math.PI / 2;
        Vec3 pivot = holder.getRopeHoldPosition(partialTick).subtract(maid.getPosition(partialTick));
        float axisX = (float)Math.cos(frame.yaw), axisZ = (float)Math.sin(frame.yaw);
        frame.swing = new Matrix4f().translation((float)pivot.x, (float)pivot.y, (float)pivot.z)
                .rotate(frame.nextAngle, axisX, 0F, axisZ)
                .translate((float)-pivot.x, (float)-pivot.y, (float)-pivot.z);
        frame.rendering = true;
    }

    public static void sample(EntityMaid maid, float angle) {
        Frame frame = FRAMES.get(maid);
        if (frame != null) frame.nextAngle = angle;
    }

    public static void apply(EntityMaid maid, PoseStack pose) {
        Frame frame = FRAMES.get(maid);
        if (frame == null || !frame.rendering) return;
        Matrix4f current = new Matrix4f(pose.last().pose());
        // Insert the world-relative swing before model yaw/scale, retaining all
        // authored transforms and moving mouth/particle renderers with the body.
        Matrix4f delta = new Matrix4f(current).invert().mul(frame.entry).mul(frame.swing)
                .mul(new Matrix4f(frame.entry).invert()).mul(current);
        pose.mulPose(delta);
    }

    public static Vec3 leashOffset(EntityMaid maid, Vec3 offset) {
        Frame frame = FRAMES.get(maid);
        if (frame == null || !frame.rendering) return offset;
        double c = Math.cos(frame.yaw), s = Math.sin(frame.yaw);
        Vector3f point = new Vector3f((float)(c * offset.z + s * offset.x),
                (float)offset.y, (float)(s * offset.z - c * offset.x));
        frame.swing.transformPosition(point);
        return new Vec3(s * point.x - c * point.z, point.y, c * point.x + s * point.z);
    }

    public static void end(EntityMaid maid) {
        Frame frame = FRAMES.get(maid);
        if (frame != null) frame.rendering = false;
    }
}
