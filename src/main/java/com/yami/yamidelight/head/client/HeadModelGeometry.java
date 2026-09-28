package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3f;

/** One-time visible-geometry measurement for grounding saved heads. No animation or world queries. */
final class HeadModelGeometry {
    private static final Vector3f[] NORMALS = {new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
    interface Scope extends AutoCloseable { @Override void close(); }
    private HeadModelGeometry() {}

    static List<Vector3f> collectVisible(BedrockPart part) {
        Collector collector = new Collector();
        collect(part, new PoseStack(), collector, false);
        return List.copyOf(collector.points);
    }
    static List<net.minecraft.world.phys.AABB> collectBoxes(BedrockPart part) {
        var boxes = new ArrayList<net.minecraft.world.phys.AABB>();
        collectBoxes(part,new PoseStack(),boxes,false);
        return List.copyOf(boxes);
    }
    private static void collectBoxes(BedrockPart part, PoseStack pose, List<net.minecraft.world.phys.AABB> boxes, boolean transform) {
        if (!part.visible || part.xScale == 0 || part.yScale == 0 || part.zScale == 0) return;
        pose.pushPose();
        if (transform) part.translateAndRotateAndScale(pose);
        for (var cube : part.cubes) {
            Collector collector = new Collector();
            cube.compile(pose.last(),NORMALS,collector,0,0,1,1,1,1);
            if (collector.points.isEmpty()) continue;
            Vector3f low = new Vector3f(Float.POSITIVE_INFINITY), high = new Vector3f(Float.NEGATIVE_INFINITY);
            for (Vector3f point : collector.points) { low.min(point); high.max(point); }
            boxes.add(new net.minecraft.world.phys.AABB(low.x,low.y,low.z,high.x,high.y,high.z));
        }
        for (var child : part.children) collectBoxes(child,pose,boxes,true);
        pose.popPose();
    }
    private static void collect(BedrockPart part, PoseStack pose, Collector collector, boolean transform) {
        if (!part.visible || part.xScale == 0 || part.yScale == 0 || part.zScale == 0) return;
        pose.pushPose();
        if (transform) part.translateAndRotateAndScale(pose);
        renderCubes(part,pose,collector,0,0);
        for (var child : part.children) collect(child, pose, collector, true);
        pose.popPose();
    }
    static void renderCubes(BedrockPart part, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        if (part.cubes.isEmpty()) return;
        NORMALS[0].set(0,-1,0);NORMALS[1].set(0,1,0);NORMALS[2].set(0,0,-1);
        NORMALS[3].set(0,0,1);NORMALS[4].set(-1,0,0);NORMALS[5].set(1,0,0);
        for (Vector3f normal : NORMALS) pose.last().normal().transform(normal);
        for (var cube : part.cubes) cube.compile(pose.last(), NORMALS, buffer, light, overlay, 1, 1, 1, 1);
    }
    private static final class Collector implements VertexConsumer {
        final List<Vector3f> points = new ArrayList<>();
        @Override public VertexConsumer addVertex(float x,float y,float z) { points.add(new Vector3f(x,y,z)); return this; }
        @Override public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        @Override public VertexConsumer setUv(float u,float v) { return this; }
        @Override public VertexConsumer setUv1(int u,int v) { return this; }
        @Override public VertexConsumer setUv2(int u,int v) { return this; }
        @Override public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
}
