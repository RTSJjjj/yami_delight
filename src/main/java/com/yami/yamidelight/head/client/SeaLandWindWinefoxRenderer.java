package com.yami.yamidelight.head.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yami.yamidelight.feast.SeaLandWindWinefoxBlock;
import com.yami.yamidelight.feast.SeaLandWindWinefoxBlockEntity;
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

/** Renders the twelve baked feast stages plus the cooked-in winefox head and its authored replacement face. */
public final class SeaLandWindWinefoxRenderer implements BlockEntityRenderer<SeaLandWindWinefoxBlockEntity> {
    private record Stage(List<HeadExpression.Mesh> meshes, Matrix4f headTransform) {}

    private static List<Stage> stages;
    private static List<HeadExpression.Mesh> faceMeshes;

    public SeaLandWindWinefoxRenderer(BlockEntityRendererProvider.Context context) {}

    public static void invalidate() {
        stages = null;
        faceMeshes = null;
    }

    private static float[] numbers(JsonArray array) {
        float[] values = new float[array.size()];
        for (int index = 0; index < values.length; index++) values[index] = array.get(index).getAsFloat();
        return values;
    }

    private static HeadExpression.Mesh mesh(com.google.gson.JsonObject batch) {
        JsonArray source = batch.getAsJsonArray("quads");
        int[][] quads = new int[source.size()][8];
        for (int index = 0; index < source.size(); index++) {
            for (int corner = 0; corner < 8; corner++) quads[index][corner] = source.get(index).getAsJsonArray().get(corner).getAsInt();
        }
        return new HeadExpression.Mesh("sea_land_wind_winefox", List.of(), ResourceLocation.parse(batch.get("texture").getAsString()),
                new byte[0], numbers(batch.getAsJsonArray("vertices")), numbers(batch.getAsJsonArray("uvs")), quads, new float[] {0, 0, 0});
    }

    private static void load() {
        if (stages != null) {
            return;
        }
        ResourceLocation resource = ResourceLocation.fromNamespaceAndPath("yamidelight", "sea_land_wind_winefox.json");
        try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource).open()) {
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Stage> loadedStages = new ArrayList<>();
            for (var value : root.getAsJsonArray("stages")) {
                var stage = value.getAsJsonObject();
                List<HeadExpression.Mesh> meshes = new ArrayList<>();
                for (var batch : stage.getAsJsonArray("batches")) meshes.add(mesh(batch.getAsJsonObject()));
                loadedStages.add(new Stage(List.copyOf(meshes), new Matrix4f().set(numbers(stage.getAsJsonArray("headTransform")))));
            }
            if (loadedStages.size() != 12) {
                throw new IllegalStateException("Expected stage_0 through stage_11");
            }
            List<HeadExpression.Mesh> loadedFace = new ArrayList<>();
            for (var batch : root.getAsJsonArray("faceBatches")) loadedFace.add(mesh(batch.getAsJsonObject()));
            if (loadedFace.isEmpty()) {
                throw new IllegalStateException("Expected AllHead3 Eyes/Mouth batches");
            }
            stages = List.copyOf(loadedStages);
            faceMeshes = List.copyOf(loadedFace);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read Sea Land Wind winefox feast", error);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(SeaLandWindWinefoxBlockEntity feast) {
        return true;
    }

    @Override
    public void render(SeaLandWindWinefoxBlockEntity feast, float tick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        try (var collision = StaticHairCollision.begin(feast, pose)) {
            var state = feast.getBlockState();
            if (state.getValue(SeaLandWindWinefoxBlock.PART) != 0) {
                return;
            }
            load();
            Stage stage = stages.get(11 - state.getValue(SeaLandWindWinefoxBlock.SERVINGS));
            float yaw = switch (state.getValue(SeaLandWindWinefoxBlock.FACING)) {
                case EAST -> -90F;
                case SOUTH -> 180F;
                case WEST -> 90F;
                default -> 0F;
            };
            pose.pushPose();
            pose.translate(.5, 0, .5);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-.5, 0, -.5);
            for (HeadExpression.Mesh mesh : stage.meshes()) {
                mesh.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.textureFile())), light, overlay);
            }
            if (feast.hasHead()) {
                MaidHeadModel head = MaidHeadModels.get(MaidHeadContent.dataOf(feast.head()));
                if (head != null) {
                    // The copied MHead2 frame positions the live winefox head.  Its own expression and the
                    // extra AllHead attachment are suppressed; AllHead3's authored Eyes/Mouth is drawn below.
                    pose.pushPose();
                    pose.mulPose(stage.headTransform());
                    head.drawOnFeast(pose, buffers, light, overlay, true);
                    pose.popPose();
                    for (HeadExpression.Mesh face : faceMeshes) {
                        face.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(head.baseTexture())), light, overlay);
                    }
                }
            }
            pose.popPose();
        }
    }
}
