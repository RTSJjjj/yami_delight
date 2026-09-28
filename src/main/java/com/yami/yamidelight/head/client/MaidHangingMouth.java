package com.yami.yamidelight.head.client;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.render.built.GeoBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.yami.yamidelight.head.MaidHanging;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Mouth-only overlay for the live death2 sequence. Hidden flags are restored after each render. */
public final class MaidHangingMouth {
    private MaidHangingMouth() {}
    public static Runnable render(EntityMaid maid, ILocationModel location, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, ResourceLocation texture) {
        if (!MaidHanging.active(maid) || maid.isInvisible() || !(location instanceof AnimatedGeoModel model)) return () -> {};
        var mesh = HeadExpression.forModel("yamidelight:death2_mouth");
        var anchor = model.bones().get("Head");
        if (anchor == null) anchor = model.bones().get("MHead");
        if (mesh == null || anchor == null || !model.bones().containsKey("RightEyelid")
                || !model.bones().containsKey("LeftEyelid")) return () -> {};
        var chain = new ArrayList<AnimatedGeoBone>();
        for (GeoBone bone = anchor.geoBone(); bone != null; bone = bone.parent()) {
            var animated = model.bones().get(bone.name());
            if (animated == null || animated.isHidden()) return () -> {};
            chain.add(0, animated);
        }
        PoseStack mouthPose = new PoseStack();
        pose.pushPose();
        try {
            for (var bone : chain) if (RenderUtils.prepMatrixForBone(pose, bone)) return () -> {};
            // Geo matrices rotate around absolute pivots. Convert the imported Bedrock-down frame
            // back to Geo's X-mirrored/Y-up frame before applying those animated matrices.
            pose.translate(0, 1.5, 0);
            pose.scale(-1, -1, 1);
            mouthPose.last().pose().set(pose.last().pose());
            mouthPose.last().normal().set(pose.last().normal());
        } finally { pose.popPose(); }
        List<Runnable> restore = new ArrayList<>();
        for (String name : mesh.hide()) {
            var bone = model.bones().get(name);
            if (bone == null) continue;
            boolean hidden = bone.isHidden(), children = bone.childBonesAreHiddenToo();
            restore.add(() -> bone.setHidden(hidden, children));
            bone.setHidden(true, true);
        }
        return () -> {
            try {
                // Draw after the maid's base pass, so changing buffers cannot invalidate its consumer.
                mesh.render(mouthPose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture)), light, overlay);
            } finally { restore.forEach(Runnable::run); }
        };
    }
}
