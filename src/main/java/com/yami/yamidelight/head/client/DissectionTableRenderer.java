package com.yami.yamidelight.head.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yami.yamidelight.head.DissectionTableBlock;
import com.yami.yamidelight.head.DissectionTableBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Authored table poses, with independent materials and C's removable organ groups. */
public final class DissectionTableRenderer implements BlockEntityRenderer<DissectionTableBlockEntity> {
    private record Batch(HeadExpression.Mesh mesh, boolean skin, boolean headMaterial, boolean tail, int removeAt, boolean hair) {}
    private record Model(List<Batch> batches, Vec3 tailOrigin, int feetSign, float flipPivotY, Matrix4f headTransform) {}
    private static final String[] MODEL_NAMES = {
            "table_body", "table_remains", "table_front", "table_cut", "table_organs",
            "table_d", "table_e", "table_e2", "table_f", "table_g",
            "table_head_stage0", "table_head_stage1", "table_head_stage2", "table_head_stage3", "table_head_stage4"
    };
    private static final Model[] MODELS = new Model[MODEL_NAMES.length];
    public DissectionTableRenderer(BlockEntityRendererProvider.Context context) {}
    public static void invalidate() { java.util.Arrays.fill(MODELS, null); }
    private static float[] numbers(JsonArray a) {
        float[] result=new float[a.size()];for(int i=0;i<result.length;i++)result[i]=a.get(i).getAsFloat();return result;
    }
    private static Model load(int index) {
        if (MODELS[index] != null) return MODELS[index];
        var file=ResourceLocation.fromNamespaceAndPath("yamidelight",MODEL_NAMES[index]+".json");
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).open()){
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            List<Batch> list=new ArrayList<>();
            for(var v:json.getAsJsonArray("batches")){
                var b=v.getAsJsonObject();var qs=b.getAsJsonArray("quads");int[][] quads=new int[qs.size()][8];
                for(int i=0;i<qs.size();i++)for(int j=0;j<8;j++)quads[i][j]=qs.get(i).getAsJsonArray().get(j).getAsInt();
                var mesh=new HeadExpression.Mesh("table",List.of(),ResourceLocation.parse(b.get("texture").getAsString()),new byte[0],
                        numbers(b.getAsJsonArray("vertices")),numbers(b.getAsJsonArray("uvs")),quads,new float[]{0,0,0});
                StaticHairCollision.register(mesh, b);
                list.add(new Batch(mesh,b.get("skin").getAsBoolean(),b.has("headMaterial")&&b.get("headMaterial").getAsBoolean(),b.get("tail").getAsBoolean(),
                        b.has("removeAt") ? b.get("removeAt").getAsInt() : 0,b.has("hairMesh")&&b.get("hairMesh").getAsBoolean()));
            }
            float[] t=numbers(json.getAsJsonArray("tailOrigin"));
            Matrix4f headTransform=json.has("headTransform")
                    ?new Matrix4f().set(numbers(json.getAsJsonArray("headTransform"))) :new Matrix4f();
            var result=new Model(List.copyOf(list),new Vec3(t[0],t[1],t[2]),
                    json.get("feetSign").getAsInt(),json.get("flipPivotY").getAsFloat(),headTransform);
            MODELS[index]=result;
            return result;
        }catch(java.io.IOException e){throw new IllegalStateException("Cannot read table pose",e);}
    }
    private static Model model(DissectionTableBlockEntity table) {
        if (table.headMounted()) return load(10 + Math.min(table.headStage(), 4));
        if (table.isRemains()) return load(1);
        if (!table.tailRemoved() || !table.flipped()) return load(0);
        return switch (table.dissectionStep()) {
            case 0 -> load(2);       // jiepotaiA: front-facing headless body
            case 1 -> load(3);       // jiepotaiB
            case 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 -> load(4); // jiepotaiC
            case 13 -> load(5);      // jiepotaiD
            case 14 -> load(6);      // jiepotaiE
            case 15 -> load(7);      // jiepotaiE2
            case 16 -> load(8);      // jiepotaiF
            default -> load(9);      // jiepotaiG, until the final right-click removes it
        };
    }
    /** Returns the authored pose used by a body stack in an inventory, hand, or frame. */
    private static Model itemModel(ItemStack stack) {
        if (!stack.is(com.yami.yamidelight.head.MaidHeadContent.MAID_BODY_ITEM.get())) return null;
        if (!DissectionTableBlockEntity.tailRemoved(stack)
                || !(DissectionTableBlockEntity.frontFacing(stack)
                || DissectionTableBlockEntity.dissectionStep(stack) > 0)) return null;
        return switch (DissectionTableBlockEntity.dissectionStep(stack)) {
            case 0 -> load(2);
            case 1 -> load(3);
            case 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12 -> load(4);
            case 13 -> load(5);
            case 14 -> load(6);
            case 15 -> load(7);
            case 16 -> load(8);
            default -> load(9);
        };
    }
    /** Draws an A--G body-item state.  A null pose falls back to the normal body item renderer. */
    public static boolean renderItemPose(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Model reference = itemModel(stack);
        if (reference == null) return false;
        var original=com.yami.yamidelight.head.MaidHeadContent.dataOf(stack).modelId().toLowerCase(java.util.Locale.ROOT).contains("winefox")
                ?MaidBodyModels.get(com.yami.yamidelight.head.MaidHeadContent.dataOf(stack), stack.is(com.yami.yamidelight.head.MaidHeadContent.MAID_REMAINS_ITEM.get())):null;
        pose.pushPose();
        // Table references span three cells.  Centre and reduce them for the normal item render space.
        pose.translate(.2F, .2F, -.05F);
        pose.scale(.6F, .6F, .6F);
        int removedOrgans=Math.max(0, DissectionTableBlockEntity.dissectionStep(stack)-2);
        for(var batch:reference.batches){
            if(batch.tail&&DissectionTableBlockEntity.tailRemoved(stack))continue;
            if(batch.removeAt>0&&removedOrgans>=batch.removeAt)continue;
            var texture=batch.skin&&original!=null?original.texture():batch.mesh.textureFile();
            batch.mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,overlay);
        }
        pose.popPose();
        return true;
    }
    private static void placement(DissectionTableBlockEntity table, Model reference, PoseStack pose) {
        float direction = yaw(table) + (table.feetAtFacing()?180F:0F) + (reference.feetSign<0?180F:0F);
        pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(direction));
        pose.translate(-.5,0,-.5);
    }
    private static float yaw(DissectionTableBlockEntity table) {
        return switch(table.getBlockState().getValue(DissectionTableBlock.FACING)){
            case EAST -> -90F;case SOUTH -> 180F;case WEST -> 90F;default -> 0F;
        };
    }
    private static ResourceLocation headStageTexture(Batch batch, int stage, MaidHeadModel head) {
        boolean authoredHeadMaterial=batch.headMaterial&&stage>=4;
        return batch.skin&&!authoredHeadMaterial&&head!=null ? head.baseTexture() : batch.mesh.textureFile();
    }
    /** Lowest authored vertex of a head stage.  Table poses keep this offset; floor blocks remove it. */
    private static float floorOffset(Model reference) {
        float low=Float.POSITIVE_INFINITY;
        for (Batch batch:reference.batches) {
            float[] vertices=batch.mesh.vertices();
            for (int index=1;index<vertices.length;index+=3) low=Math.min(low,vertices[index]);
        }
        return Float.isFinite(low) ? low : 0.0F;
    }
    /** Taken STAGE2+ heads retain their authored operation state instead of reverting to a whole-head icon. */
    public static boolean renderDissectedHeadItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int stage=DissectionTableBlockEntity.headDissectionStage(stack);
        if (stage < 2) return false;
        renderDissectedHead(com.yami.yamidelight.head.MaidHeadContent.dataOf(stack), stage, pose, buffers, light, overlay, true);
        return true;
    }
    /** A placed STAGE2+ head uses its corresponding static stage mesh, always in the ground pose. */
    public static void renderDissectedHeadBlock(com.yami.yamidelight.head.MaidHeadData data, int stage, PoseStack pose,
                                                MultiBufferSource buffers, int light, int overlay) {
        renderDissectedHead(data, stage, pose, buffers, light, overlay, false);
    }
    private static void renderDissectedHead(com.yami.yamidelight.head.MaidHeadData data, int stage, PoseStack pose,
                                            MultiBufferSource buffers, int light, int overlay, boolean item) {
        Model reference=load(10+Math.min(stage,4));
        MaidHeadModel head=MaidHeadModels.get(data);
        pose.pushPose();
        if (item) {
            // The authored table mesh is centred by its MHead frame before fitting it in an item slot.
            pose.translate(.5F,.75F,.5F);
            pose.scale(.78F,.78F,.78F);
            pose.translate(-reference.headTransform().m30(),-reference.headTransform().m31(),-reference.headTransform().m32());
        } else {
            // Z_STAGE references are table-space and start above the tabletop.  A placed head is a
            // ground object, so remove the authored empty space below its lowest visible vertex.
            pose.translate(0.0F,-floorOffset(reference),0.0F);
        }
        for (Batch batch:reference.batches) {
            if (!item && HeadHairPlacement.active() && batch.hair) continue;
            batch.mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(headStageTexture(batch,stage,head))),light,overlay);
        }
        if (!item && HeadHairPlacement.active()) {
            pose.pushPose();pose.mulPose(reference.headTransform());
            HeadHairPlacement.draw(head,pose,buffers,head==null
                    ?ResourceLocation.fromNamespaceAndPath("yamidelight","textures/entity/table/head_stage0_0.png")
                    :head.baseTexture(),light,overlay);
            pose.popPose();
        }
        if (head != null && stage < 5) {
            pose.pushPose();
            pose.mulPose(reference.headTransform());
            head.drawExpressionOnTable(pose,buffers,light,overlay,stage<4,stage<5);
            pose.popPose();
        }
        pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(DissectionTableBlockEntity table){return true;}
    @Override public void render(DissectionTableBlockEntity table,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        try (var collision = StaticHairCollision.begin(table, pose)) {
            if(!table.occupied())return;
            Model reference=model(table);
            if (table.headMounted()) {
                pose.pushPose();
                placement(table,reference,pose);
                MaidHeadModel head=MaidHeadModels.get(com.yami.yamidelight.head.MaidHeadContent.dataOf(table.mountedHead()));
                for(var batch:reference.batches) {
                    // Hair and ordinary head geometry inherit the actual maid's saved skin.  Only the
                    // STAGE4 direct Head material is intentionally authored by that stage; exposed brain
                    // mesh also stays on the anatomy sheet instead of borrowing hair UVs.
                    batch.mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(
                            headStageTexture(batch,table.headStage(),head))),light,overlay);
                }
                if(head!=null&&table.headStage()<5){
                    pose.pushPose();
                    pose.mulPose(reference.headTransform());
                    // The Z stages deliberately omit eyes and mouth.  Stage 4 has harvested the eyes,
                    // and stage 5 also removes the mouth, while the incoming maid expression supplies
                    // the still-present facial detail in the authored MHead frame.
                    head.drawExpressionOnTable(pose,buffers,light,overlay,table.headStage()<4,table.headStage()<5);
                    pose.popPose();
                }
                pose.popPose();
                return;
            }
            // Reference poses use the winefox UV layout. Compatible skins retain their own texture.
            var original=table.data().modelId().toLowerCase(java.util.Locale.ROOT).contains("winefox")
                    ?MaidBodyModels.get(table.data(),table.isRemains()):null;
            pose.pushPose();placement(table,reference,pose);
            int removedOrgans=Math.max(0, table.dissectionStep()-2);
            for(var batch:reference.batches){
                if(batch.tail&&table.tailRemoved())continue;
                if(batch.removeAt>0&&removedOrgans>=batch.removeAt)continue;
                var texture=batch.skin&&original!=null?original.texture():batch.mesh.textureFile();
                batch.mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,overlay);
            }
            pose.popPose();
        }
    }
    public static void cutParticles(DissectionTableBlockEntity table,int count){
        if(!table.occupied())return;
        Model reference=model(table);
        var pose=new PoseStack();placement(table,reference,pose);
        var transformed=pose.last().pose().transformPosition(new org.joml.Vector3f(
                (float)reference.tailOrigin.x,(float)reference.tailOrigin.y,(float)reference.tailOrigin.z));
        Vec3 p=new Vec3(transformed.x,transformed.y,transformed.z);
        MaidBloodSpray.smallBurst(p.add(table.getBlockPos().getX(),table.getBlockPos().getY(),table.getBlockPos().getZ()),count);
    }
}
