package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/**
 * One maid's {@code MHead}, ready to draw: the baked model, the bone to start from, the bones above it
 * that have to be replayed to reach it, and where its middle is.
 *
 * <p>The head keeps the coordinates it has in the model. Nothing is rebuilt, re-scaled per cube or
 * re-parented: the root-to-{@code MHead} chain of rotations is replayed exactly as the maid's own
 * renderer replays it, so hair, ears, ribbons and anything else hanging off the head stay where the
 * model's author put them relative to the skull. The only transform added on top is one placement
 * transform around the finished group, which is what puts its centre on the anchor and, for a wall
 * mount, turns its face outwards.
 */
public final class MaidHeadModel {
    /** Size a head is normalised to when it is drawn as an item, matching a vanilla head cube. */
    private static final float ITEM_HEAD_SIZE = 1.0F;
    /**
     * Where the head's middle sits as an item: a quarter block below the middle of the square an item
     * pretends to occupy, which is where a vanilla head item sits.
     */
    private static final float ITEM_CENTRE_Y = -0.25F;

    private final BedrockPart head;
    private final List<BedrockPart> chain;
    /** The skull: what a mounted head is placed and spaced by. */
    private final Vector3f centre;
    private final Vector3f size;
    /** Everything that is actually drawn: what an item head is sized and centred by. */
    private final Vector3f itemCentre;
    private final Vector3f itemSize;
    /** The head bone's pivot in blocks: the frame a pasted face has to be moved into. */
    private final Vector3f faceOrigin;
    private final float modelScale;
    private final ResourceLocation texture;
    /** The maid's own skin before an optional hand-painted expression swaps the visible sheet. */
    private final ResourceLocation baseTexture;
    /** A hand edited face pasted onto this head, or null when the model keeps its own. */
    private final HeadExpression.Mesh expression;
    private BedrockPart expressionAnchor;
    private List<BedrockPart> expressionChain = List.of();
    private ResourceLocation expressionTexture;
    private String extraPart;
    private List<BedrockPart> extraChain = List.of();
    private float extraOriginY;
    private BedrockPart tailRoot;
    private StaticHairRig staticHair;
    private HeadHairParts fixedHair;
    void setNamedParts(java.util.Map<String, BedrockPart> parts) {
        staticHair = new StaticHairRig(head, parts);
        fixedHair = new HeadHairParts(head, parts);
    }
    boolean drawDefaultHair(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, int light, int overlay) {
        if (fixedHair==null || !fixedHair.available()) return false;
        fixedHair.draw(pose,buffers,texture,light,overlay);return true;
    }
    private void renderHead(PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        if (HeadHairPlacement.active()) head.render(pose,buffer,light,overlay);
        else if (staticHair != null && StaticHairCollision.active()) staticHair.render(pose,buffer,light,overlay);
        else head.render(pose, buffer, light, overlay);
    }
    void setTailRoot(BedrockPart part) { tailRoot = part; }
    public void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, boolean asItem, boolean tailRemoved) {
        if (!tailRemoved || tailRoot == null) { draw(pose,buffers,light,overlay,asItem); return; }
        float x=tailRoot.xScale,y=tailRoot.yScale,z=tailRoot.zScale;
        try {
            tailRoot.xScale=tailRoot.yScale=tailRoot.zScale=0F;
            draw(pose,buffers,light,overlay,asItem);
        } finally { tailRoot.xScale=x;tailRoot.yScale=y;tailRoot.zScale=z; }
    }

    void attachPart(String name, BedrockPart anchor, float referencePivotY, boolean fromRoot) {
        extraPart = name;
        extraOriginY = (24F - referencePivotY) / 16F;
        var bones = new java.util.ArrayList<BedrockPart>();
        bones.add(anchor);
        if (fromRoot) for (var parent = anchor.getParent(); parent != null; parent = parent.getParent()) bones.add(0, parent);
        extraChain = List.copyOf(bones);
    }

    private void drawExtra(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (extraPart == null) return;
        pose.pushPose();
        for (var bone : extraChain) bone.translateAndRotateAndScale(pose);
        pose.translate(0, -extraOriginY, 0);
        AllHeadParts.render(extraPart, pose, buffers, light, overlay);
        pose.popPose();
    }

    /** A full body renders from its root, but its pasted face follows the posed head. */
    void attachExpression(BedrockPart anchor, ResourceLocation faceTexture) {
        expressionAnchor = anchor;
        expressionTexture = faceTexture;
        var parents = new java.util.ArrayList<BedrockPart>();
        for (var part = anchor.getParent(); part != null; part = part.getParent()) parents.add(0, part);
        expressionChain = List.copyOf(parents);
    }
    private Vector3f hookAnchor = new Vector3f(0, (24F - 30.7F) / 16F, 0);

    /** Draw in the reference feast's MHead2 frame, without inheriting the source body's world pose. */
    void drawOnFeast(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        drawOnFeast(pose, buffers, light, overlay, false);
    }

    /**
     * Draws a feast head with an optional authored face replacement.  The replacement path deliberately
     * omits both the head's extra AllHead attachment and its normal expression; the feast supplies its
     * own Eyes/Mouth mesh after this call.
     */
    void drawOnFeast(PoseStack pose, MultiBufferSource buffers, int light, int overlay, boolean replaceFace) {
        float x=head.x, y=head.y, z=head.z, rx=head.xRot, ry=head.yRot, rz=head.zRot;
        pose.pushPose();
        pose.scale(-1F, -1F, 1F);
        try {
            // Children and cubes are already relative to MHead. Replacing only this transform keeps
            // the inserted model/skin and its hidden expression parts, while using AllHead3's pose.
            head.x=head.y=head.z=head.xRot=head.yRot=head.zRot=0F;
            ResourceLocation feastTexture = replaceFace ? baseTexture : texture;
            renderHead(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(feastTexture)), light, overlay);
            if (!replaceFace) {
                drawExtra(pose, buffers, light, overlay);
            }
            if (!replaceFace && expression != null) {
                pose.pushPose();
                pose.translate(-faceOrigin.x, -faceOrigin.y, -faceOrigin.z);
                expression.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay);
                pose.popPose();
            }
        } finally {
            head.x=x; head.y=y; head.z=z; head.xRot=rx; head.yRot=ry; head.zRot=rz;
            pose.popPose();
        }
    }

    /** Draws only the saved maid expression in an authored static-head MHead frame. */
    void drawExpressionOnTable(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               boolean showEyes, boolean showMouth) {
        if (expression == null) return;
        pose.pushPose();
        pose.scale(-1F, -1F, 1F);
        pose.translate(-faceOrigin.x, -faceOrigin.y, -faceOrigin.z);
        expression.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay,
                showEyes, showMouth);
        pose.popPose();
    }

    void setHookAnchor(float[] pivot) {
        if (pivot != null) hookAnchor = new Vector3f(pivot[0] / 16F, (24F - pivot[1]) / 16F, pivot[2] / 16F);
    }

    /** Shared authored frame, without per-item centering or entity render scale. */
    void drawOnHook(PoseStack pose, MultiBufferSource buffers, int light, int overlay, float x, float y, float z) {
        pose.pushPose();
        pose.translate(x / 16F, (24F + y) / 16F, z / 16F);
        pose.scale(-1F, -1F, 1F);
        // Align other rigs by their neck while the reference rig retains its exact A coordinates.
        pose.translate(-hookAnchor.x, (24F - 30.7F) / 16F - hookAnchor.y, -hookAnchor.z);
        for (BedrockPart part : chain) part.translateAndRotateAndScale(pose);
        renderHead(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay);
        drawExtra(pose, buffers, light, overlay);
        pose.popPose();
    }

    MaidHeadModel(BedrockPart head, List<BedrockPart> chain, Vector3f centre, Vector3f size,
                  Vector3f itemCentre, Vector3f itemSize, Vector3f faceOrigin,
                  float modelScale, ResourceLocation texture, ResourceLocation baseTexture,
                  HeadExpression.Mesh expression) {
        this.head = head;
        this.chain = chain;
        this.centre = centre;
        this.size = size;
        this.itemCentre = itemCentre;
        this.itemSize = itemSize;
        this.faceOrigin = faceOrigin;
        this.modelScale = modelScale;
        this.texture = texture;
        this.baseTexture = baseTexture;
        this.expression = expression;
    }

    /** The size the head is drawn at on a wall, on top of the one block unit the model is measured in. */
    public float blockScale() {
        return modelScale;
    }

    /** Bounding box of the drawn head in blocks, before {@link #blockScale()}. */
    public Vector3f size() {
        return size;
    }

    public ResourceLocation texture() {
        return texture;
    }

    /** The original model skin, used where an authored mesh must inherit the held head's material. */
    public ResourceLocation baseTexture() {
        return baseTexture;
    }

    /**
     * Draws the head with its middle at the current origin, facing -Z.
     *
     * <p>Both paths flip the model on X and Y first, which is not decoration: the geometry comes out of
     * a maid model, and entity models are authored upside down and mirrored - every entity renderer
     * undoes that with {@code scale(-1, -1, 1)}. Leaving it out is what makes a head hang upside down
     * and back to front, in the inventory and on a wall alike.
     *
     * @param asItem a held or displayed item rather than a mounted head: the head is scaled to a vanilla
     *               head cube and placed where a vanilla head item sits, which is the space the display
     *               transforms in the item model were written for
     */
    public void draw(PoseStack pose, MultiBufferSource buffers, int light, int overlay, boolean asItem) {
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        pose.pushPose();
        if (asItem) {
            // Sized and centred on everything the head draws, hair included: a trophy has to fit the slot
            // it is shown in, and a maid's fringe can be twice the size of her skull.
            float longest = Math.max(itemSize.x, Math.max(itemSize.y, itemSize.z));
            float itemScale = longest <= 1.0E-4F ? ITEM_HEAD_SIZE : ITEM_HEAD_SIZE / longest;
            pose.translate(0.0F, ITEM_CENTRE_Y, 0.0F);
            pose.scale(itemScale, itemScale, itemScale);
            // A vanilla head item is drawn facing the viewer; 180 degrees is the yaw of a model that
            // looks out of the screen, and it is the one number the item path shares with vanilla.
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        } else {
            pose.scale(modelScale, modelScale, modelScale);
        }
        pose.scale(-1.0F, -1.0F, 1.0F);
        Vector3f middle = asItem ? itemCentre : centre;
        pose.translate(-middle.x, -middle.y, -middle.z);
        for (BedrockPart part : chain) {
            part.translateAndRotateAndScale(pose);
        }
        if (!asItem && HeadHairPlacement.ground() && fixedHair!=null && fixedHair.available()) {
            try (var hidden=fixedHair.hide()) { head.render(pose,vertices,light,overlay); }
            pose.pushPose();head.translateAndRotateAndScale(pose);pose.scale(-1,-1,1);
            HeadHairPlacement.draw(this,pose,buffers,baseTexture,light,overlay);pose.popPose();
        } else renderHead(pose, vertices, light, overlay);
        drawExtra(pose, buffers, light, overlay);
        if (expression != null) {
            // The edited face is in the model's own coordinates, while the head's cubes are drawn in the
            // head bone's frame - a frame whose origin sits at the bone's pivot. Moving the face by that
            // pivot is what puts it on the face instead of a head height above it.
            VertexConsumer face = buffers.getBuffer(RenderType.entityCutoutNoCull(
                    expressionTexture == null ? texture : expressionTexture));
            pose.pushPose();
            for (var part : expressionChain) part.translateAndRotateAndScale(pose);
            (expressionAnchor == null ? head : expressionAnchor).translateAndRotateAndScale(pose);
            pose.translate(-faceOrigin.x, -faceOrigin.y, -faceOrigin.z);
            expression.render(pose, face, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }

}
