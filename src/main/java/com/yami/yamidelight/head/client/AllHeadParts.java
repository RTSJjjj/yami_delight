package com.yami.yamidelight.head.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;

/** OBJ elements retain their own per-face materials, independently of the maid's skin. */
public final class AllHeadParts {
    private static Map<String, List<HeadExpression.Mesh>> parts;
    private AllHeadParts() {}
    public static void invalidate() { parts = null; }
    private static float[] numbers(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i=0; i<values.length; i++) values[i] = array.get(i).getAsFloat();
        return values;
    }
    public static List<HeadExpression.Mesh> get(String name) {
        if (parts == null) {
            var loaded = new HashMap<String, List<HeadExpression.Mesh>>();
            try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(
                    ResourceLocation.fromNamespaceAndPath("yamidelight", "allhead_parts.json")).open()) {
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var entry : json.entrySet()) {
                    var batches = new ArrayList<HeadExpression.Mesh>();
                    for (var element : entry.getValue().getAsJsonArray()) {
                        var batch = element.getAsJsonObject();
                        var array = batch.getAsJsonArray("quads");
                        int[][] quads = new int[array.size()][8];
                        for (int i=0; i<quads.length; i++) for (int j=0; j<8; j++) quads[i][j] = array.get(i).getAsJsonArray().get(j).getAsInt();
                        batches.add(new HeadExpression.Mesh(entry.getKey(), List.of(),
                                ResourceLocation.parse(batch.get("texture").getAsString()), new byte[0],
                                numbers(batch.getAsJsonArray("vertices")), numbers(batch.getAsJsonArray("uvs")), quads, new float[]{0,0,0}));
                    }
                    loaded.put(entry.getKey(), List.copyOf(batches));
                }
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read AllHead OBJ parts", e); }
            parts = loaded;
        }
        return parts.getOrDefault(name, List.of());
    }
    public static void render(String name, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (var batch : get(name)) batch.render(pose,
                buffers.getBuffer(RenderType.entityCutoutNoCull(batch.textureFile())), light, overlay);
    }

    static float[] extendBounds(float[] bounds, String name,
            com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart anchor, float referenceY) {
        if (bounds == null || anchor == null) return bounds;
        PoseStack pose = new PoseStack();
        var chain = new ArrayList<com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart>();
        for (var part = anchor; part != null; part = part.getParent()) chain.add(0, part);
        for (var part : chain) part.translateAndRotateAndScale(pose);
        pose.translate(0, -(24F - referenceY) / 16F, 0);
        float[] min = new float[3], max = new float[3];
        for (int i=0; i<3; i++) { min[i]=bounds[i]-bounds[i+3]/2; max[i]=bounds[i]+bounds[i+3]/2; }
        for (var batch : get(name)) for (var quad : batch.quads()) for (int c=0; c<8; c+=2) {
            int v = quad[c]*3;
            var point = new org.joml.Vector3f(batch.vertices()[v], batch.vertices()[v+1], batch.vertices()[v+2]);
            pose.last().pose().transformPosition(point);
            for (int i=0; i<3; i++) { min[i]=Math.min(min[i],point.get(i)); max[i]=Math.max(max[i],point.get(i)); }
        }
        return new float[]{(min[0]+max[0])/2,(min[1]+max[1])/2,(min[2]+max[2])/2,
                max[0]-min[0],max[1]-min[1],max[2]-min[2]};
    }

    /** Capture the animated frame now; draw separately after the maid's original texture pass. */
    public static Runnable prepareLive(EntityMaid maid, ILocationModel location, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (!MaidHeadHider.isHeadless(maid.getId()) || maid.isInvisible()
                || !(location instanceof AnimatedGeoModel model)) return () -> {};
        var anchor = model.bones().get("AllHead");
        if (anchor == null || get("AllHead").isEmpty()) return () -> {};
        var chain = new ArrayList<AnimatedGeoBone>();
        for (GeoBone bone = anchor.geoBone(); bone != null; bone = bone.parent()) {
            var animated = model.bones().get(bone.name());
            if (animated == null || animated.isHidden()) return () -> {};
            chain.add(0, animated);
        }
        PoseStack extra = new PoseStack();
        pose.pushPose();
        try {
            for (var bone : chain) if (RenderUtils.prepMatrixForBone(pose, bone)) return () -> {};
            pose.translate(0, 1.5, 0);
            pose.scale(-1, -1, 1);
            extra.last().pose().set(pose.last().pose());
            extra.last().normal().set(pose.last().normal());
        } finally { pose.popPose(); }
        boolean wasHidden = anchor.cubesAreHidden();
        anchor.setCubesHidden(true);
        return () -> {
            try { render("AllHead", extra, buffers, light, overlay); }
            finally { anchor.setCubesHidden(wasHidden); }
        };
    }
}
