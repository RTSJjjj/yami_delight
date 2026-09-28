package com.yami.yamidelight.head.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yami.yamidelight.feast.FeastContent;
import com.yami.yamidelight.feast.FoxHeadFoodBlock;
import com.yami.yamidelight.feast.FoxHeadFoodBlockEntity;
import com.yami.yamidelight.feast.FoxHeadFoodData;
import com.yami.yamidelight.head.DissectionTableBlockEntity;
import com.yami.yamidelight.head.MaidHeadContent;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;

public final class FoxHeadFoodRenderer implements BlockEntityRenderer<FoxHeadFoodBlockEntity> {
    private record Batch(HeadExpression.Mesh mesh, boolean skin, boolean skull, boolean hair) {}
    private record Scene(List<Batch> batches, Matrix4f head, Matrix4f inverseHead, float floor) {}
    private static List<Scene> scenes;
    public FoxHeadFoodRenderer(BlockEntityRendererProvider.Context context) {}
    public static void invalidate() { scenes = null; }
    private static float[] floats(com.google.gson.JsonArray array) {
        float[] out = new float[array.size()]; for (int i=0;i<out.length;i++) out[i]=array.get(i).getAsFloat(); return out;
    }
    private static List<Scene> scenes() {
        if (scenes != null) return scenes;
        var resource = ResourceLocation.fromNamespaceAndPath("yamidelight", "fox_head_food.json");
        try (var stream = Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource).open()) {
            var root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Scene> result = new ArrayList<>();
            for (var value : root.getAsJsonArray("scenes")) {
                var scene = value.getAsJsonObject(); List<Batch> batches = new ArrayList<>();
                for (var b : scene.getAsJsonArray("batches")) {
                    var batch = b.getAsJsonObject();
                    var faces = batch.getAsJsonArray("quads"); int[][] quads = new int[faces.size()][8];
                    for (int i=0;i<faces.size();i++) for (int j=0;j<8;j++) quads[i][j]=faces.get(i).getAsJsonArray().get(j).getAsInt();
                    var mesh = new HeadExpression.Mesh("head_food", List.of(), ResourceLocation.parse(batch.get("texture").getAsString()),
                            new byte[0], floats(batch.getAsJsonArray("vertices")), floats(batch.getAsJsonArray("uvs")), quads, new float[]{0,0,0});
                    StaticHairCollision.register(mesh, batch);
                    batches.add(new Batch(mesh, batch.get("skin").getAsBoolean(), batch.get("skull").getAsBoolean(),
                            batch.has("hairMesh")&&batch.get("hairMesh").getAsBoolean()));
                }
                float floor=Float.POSITIVE_INFINITY;
                for (Batch batch:batches) for (int i=1;i<batch.mesh.vertices().length;i+=3) {
                    floor=Math.min(floor,batch.mesh.vertices()[i]);
                }
                Matrix4f headTransform=new Matrix4f().set(floats(scene.getAsJsonArray("headTransform")));
                result.add(new Scene(List.copyOf(batches),headTransform,new Matrix4f(headTransform).invert(),floor));
            }
            if (result.size()!=7) throw new IllegalStateException("Expected five food stages, bowl and lantern");
            scenes=List.copyOf(result); return scenes;
        } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot load fox head foods", ex); }
    }
    private static void draw(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        boolean lantern=stack.is(FeastContent.FOX_LANTERN_ITEM.get());
        int index=lantern?6:stack.is(FeastContent.FOX_BRAIN_BOWL_ITEM.get())?5:FoxHeadFoodData.stage(stack);
        Scene scene=scenes().get(index);
        var client=Minecraft.getInstance();
        ItemStack saved=client.level==null?stack:FoxHeadFoodData.sourceHead(stack,client.level.registryAccess());
        int headStage=DissectionTableBlockEntity.headDissectionStage(saved);
        var savedData=MaidHeadContent.dataOf(saved);
        var head=savedData.hasModel()?MaidHeadModels.get(savedData):null;
        // Authored geometry is authoritative, including deleted hair cubes. Inherit the skin,
        // never paste the original head's complete hair back over the open bowl.
        float low=scene.floor;
        pose.pushPose();
        if (Float.isFinite(low)) pose.translate(0,-low,0);
        for (Batch batch:scene.batches) {
            if (HeadHairPlacement.active() && batch.hair) continue;
            ResourceLocation texture=batch.mesh.textureFile();
            if (batch.skin && head!=null && !(lantern&&batch.skull)) texture=head.baseTexture();
            if (!lantern&&batch.skull&&headStage>=4)
                texture=ResourceLocation.fromNamespaceAndPath("yamidelight","textures/entity/table/head_stage4_0.png");
            batch.mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,overlay);
        }
        if (HeadHairPlacement.active()) {
            pose.pushPose();pose.mulPose(scene.head);
            HeadHairPlacement.draw(head,pose,buffers,head==null
                    ?ResourceLocation.fromNamespaceAndPath("yamidelight","textures/entity/head_food/lantern_0.png")
                    :head.baseTexture(),light,overlay);
            pose.popPose();
        }
        if (!lantern&&head!=null&&headStage<5) {
            pose.pushPose();pose.mulPose(scene.head);
            head.drawExpressionOnTable(pose,buffers,light,overlay,headStage<4,headStage<5);
            pose.popPose();
        }
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(FoxHeadFoodBlockEntity food) { return true; }
    /** Shared laid-out strands, in MHead-local space. Scalp, ears, mouth and mask are not included. */
    static void drawGroundHair(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, int light, int overlay) {
        Scene lantern=scenes().get(6);
        pose.pushPose();pose.mulPose(lantern.inverseHead);
        for (Batch batch:lantern.batches) if (batch.hair)
            batch.mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,overlay);
        pose.popPose();
    }
    @Override public void render(FoxHeadFoodBlockEntity food, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        try (var hairstyle = HeadHairPlacement.begin(food,
                food.getBlockState().getValue(FoxHeadFoodBlock.ATTACH_FACE)==net.minecraft.world.level.block.state.properties.AttachFace.FLOOR,true)) {
            pose.pushPose();pose.translate(.5,0,.5);
            pose.mulPose(Axis.YP.rotationDegrees(switch(food.getBlockState().getValue(FoxHeadFoodBlock.FACING)) {case EAST->-90;case SOUTH->180;case WEST->90;default->0;}));
            pose.translate(-.5,0,-.5);draw(food.item(),pose,buffers,light,overlay);pose.popPose();
        }
    }
    public static final IClientItemExtensions ITEM_EXTENSION = new IClientItemExtensions() {
        private BlockEntityWithoutLevelRenderer renderer;
        @Override public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer==null) renderer=new BlockEntityWithoutLevelRenderer(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels()) {
                @Override public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
                    draw(stack,pose,buffers,light,overlay);
                }
            };
            return renderer;
        }
    };
}
