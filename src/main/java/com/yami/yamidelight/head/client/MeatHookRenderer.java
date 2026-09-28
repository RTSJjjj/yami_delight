package com.yami.yamidelight.head.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.yami.yamidelight.head.MeatHookBlock;
import com.yami.yamidelight.head.MeatHookBlockEntity;
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

/** The OBJ hook is a normal block model; only its stored body needs this dynamic renderer. */
public final class MeatHookRenderer implements BlockEntityRenderer<MeatHookBlockEntity> {
    private record WinefoxBatch(HeadExpression.Mesh mesh, int minStep, int removeAt) {}
    private static List<WinefoxBatch> WINEFOX;
    public MeatHookRenderer(BlockEntityRendererProvider.Context context) {}
    public static void invalidate() { WINEFOX = null; }
    private static float[] numbers(JsonArray array) {
        float[] result=new float[array.size()];for(int i=0;i<result.length;i++)result[i]=array.get(i).getAsFloat();return result;
    }
    /** The authored winefox_1 body and anatomy mesh; the placed block retains the original hook OBJ. */
    private static List<WinefoxBatch> winefox() {
        if (WINEFOX != null) return WINEFOX;
        var file=ResourceLocation.fromNamespaceAndPath("yamidelight", "hook_winefox.json");
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).open()) {
            var json=JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<WinefoxBatch> result=new ArrayList<>();
            for(var value:json.getAsJsonArray("batches")) {
                var batch=value.getAsJsonObject();var source=batch.getAsJsonArray("quads");int[][] quads=new int[source.size()][8];
                for(int i=0;i<source.size();i++)for(int j=0;j<8;j++)quads[i][j]=source.get(i).getAsJsonArray().get(j).getAsInt();
                var mesh=new HeadExpression.Mesh("hook_winefox", List.of(), ResourceLocation.parse(batch.get("texture").getAsString()),
                        new byte[0], numbers(batch.getAsJsonArray("vertices")), numbers(batch.getAsJsonArray("uvs")), quads, new float[]{0,0,0});
                result.add(new WinefoxBatch(mesh, batch.has("minStep") ? batch.get("minStep").getAsInt() : 0,
                        batch.has("removeAt") ? batch.get("removeAt").getAsInt() : 0));
            }
            WINEFOX=List.copyOf(result);return WINEFOX;
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read the hanging winefox mesh", e); }
    }
    @Override public boolean shouldRenderOffScreen(MeatHookBlockEntity hook) { return true; }
    @Override public void render(MeatHookBlockEntity hook, float partialTick, PoseStack pose,
                                  MultiBufferSource buffers, int light, int overlay) {
        try (var collision = StaticHairCollision.begin(hook, pose)) {
            if (!hook.hasBody()) return;
            if (hook.usesWinefoxMesh()) {
                renderWinefox(hook, pose, buffers, light, overlay);
                return;
            }
            var model = MaidBodyModels.get(hook.data(), false);
            if (model == null) return;
            float yaw = switch (hook.getBlockState().getValue(MeatHookBlock.FACING)) {
                case EAST -> -90F;
                case SOUTH -> 180F;
                case WEST -> 90F;
                default -> 0F;
            };
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.translate(-0.5, 0, -0.5);
            model.drawOnHook(pose, buffers, light, overlay,
                    MaidHookPose.offset(0), MaidHookPose.offset(1), MaidHookPose.offset(2));
            pose.popPose();
        }
    }
    private static void renderWinefox(MeatHookBlockEntity hook, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float yaw = switch (hook.getBlockState().getValue(MeatHookBlock.FACING)) {
            case EAST -> -90F;
            case SOUTH -> 180F;
            case WEST -> 90F;
            default -> 0F;
        };
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-.5, 0, -.5);
        int step=hook.interactionData().getInt(com.yami.yamidelight.head.DissectionTableBlockEntity.DISSECTION_STEP);
        for (var batch : winefox()) {
            if (step<batch.minStep()) continue;
            if (batch.removeAt()>0 && step>=batch.removeAt()) continue;
            var mesh=batch.mesh();
            mesh.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.textureFile())), light, overlay);
        }
        pose.popPose();
    }
}
