package com.yami.yamidelight.head.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yami.yamidelight.feast.StuffedWinefoxBlock;
import com.yami.yamidelight.feast.StuffedWinefoxBlockEntity;
import com.yami.yamidelight.head.MaidHeadContent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class StuffedWinefoxRenderer implements BlockEntityRenderer<StuffedWinefoxBlockEntity> {
    private record Stage(List<HeadExpression.Mesh> meshes, Matrix4f headTransform) {}
    private static List<Stage> stages;
    public StuffedWinefoxRenderer(BlockEntityRendererProvider.Context context) {}
    public static void invalidate() { stages = null; }
    private static float[] numbers(JsonArray array) {
        float[] values = new float[array.size()]; for (int i=0;i<values.length;i++) values[i]=array.get(i).getAsFloat(); return values;
    }
    private static List<Stage> stages() {
        if (stages != null) return stages;
        var resource = ResourceLocation.fromNamespaceAndPath("yamidelight", "stuffed_winefox.json");
        try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource).open()) {
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Stage> result = new ArrayList<>();
            for (var element : root.getAsJsonArray("stages")) {
                var stage = element.getAsJsonObject();
                List<HeadExpression.Mesh> meshes = new ArrayList<>();
                for (var value : stage.getAsJsonArray("batches")) {
                    var batch=value.getAsJsonObject(); var source=batch.getAsJsonArray("quads"); int[][] quads=new int[source.size()][8];
                    for(int i=0;i<source.size();i++) for(int j=0;j<8;j++) quads[i][j]=source.get(i).getAsJsonArray().get(j).getAsInt();
                    meshes.add(new HeadExpression.Mesh("stuffed_winefox", List.of(), ResourceLocation.parse(batch.get("texture").getAsString()),
                            new byte[0], numbers(batch.getAsJsonArray("vertices")), numbers(batch.getAsJsonArray("uvs")), quads, new float[]{0,0,0}));
                }
                result.add(new Stage(List.copyOf(meshes), new Matrix4f().set(numbers(stage.getAsJsonArray("headTransform")))));
            }
            if (result.size() != 13) throw new IllegalStateException("Expected full feast plus 12 serving stages");
            stages=List.copyOf(result); return stages;
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read stuffed winefox feast", e); }
    }
    @Override public boolean shouldRenderOffScreen(StuffedWinefoxBlockEntity feast) { return true; }
    @Override public void render(StuffedWinefoxBlockEntity feast, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        try (var collision = StaticHairCollision.begin(feast, pose)) {
            var state=feast.getBlockState();
            if (state.getValue(StuffedWinefoxBlock.PART) != 0) return;
            var stage=stages().get(12-state.getValue(StuffedWinefoxBlock.SERVINGS));
            float yaw=switch(state.getValue(StuffedWinefoxBlock.FACING)) { case EAST -> -90F; case SOUTH -> 180F; case WEST -> 90F; default -> 0F; };
            pose.pushPose();
            pose.translate(.5,0,.5); pose.mulPose(Axis.YP.rotationDegrees(yaw)); pose.translate(-.5,0,-.5);
            for (var mesh:stage.meshes()) mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.textureFile())),light,overlay);
            if (feast.hasHead()) {
                var head=MaidHeadModels.get(MaidHeadContent.dataOf(feast.head()));
                if (head != null) {
                    pose.pushPose(); pose.mulPose(stage.headTransform());
                    head.drawOnFeast(pose,buffers,light,overlay); pose.popPose();
                }
            }
            pose.popPose();
        }
    }
}
