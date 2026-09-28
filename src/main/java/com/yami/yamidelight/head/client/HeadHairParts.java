package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Only articulated hair: never restore BaseHair/scalp, ears, masks or expression geometry. */
final class HeadHairParts {
    private record Branch(BedrockPart root, List<BedrockPart> parents) {}
    private final List<Branch> branches = new ArrayList<>();
    HeadHairParts(BedrockPart head, Map<String,BedrockPart> parts) {
        var movable = java.util.Collections.newSetFromMap(new IdentityHashMap<BedrockPart,Boolean>());
        parts.forEach((name,part)->{
            if (name.matches("(?i)(Long(Left|Right)?Hair2?|(Left|Right)(Side|Long)Hair2?|Bangs|hair[12]?(left|right)2?)")) movable.add(part);
        });
        for (BedrockPart part : movable) {
            List<BedrockPart> parents = new ArrayList<>();
            BedrockPart parent = part.getParent();
            boolean nested = false;
            while (parent != null && parent != head) {
                if (movable.contains(parent)) nested = true;
                parents.add(0,parent); parent = parent.getParent();
            }
            if (parent == head && !nested) branches.add(new Branch(part,List.copyOf(parents)));
        }
    }
    boolean available() { return !branches.isEmpty(); }
    HeadModelGeometry.Scope hide() {
        Map<BedrockPart,Boolean> visibility = new IdentityHashMap<>();
        for (Branch branch : branches) { visibility.put(branch.root,branch.root.visible);branch.root.visible=false; }
        return ()->visibility.forEach((part,visible)->part.visible=visible);
    }
    void draw(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, int light, int overlay) {
        pose.pushPose();pose.scale(-1,-1,1);
        for (Branch branch : branches) {
            pose.pushPose();
            boolean visible = true;
            for (BedrockPart parent : branch.parents) {
                if (!parent.visible || parent.xScale==0 || parent.yScale==0 || parent.zScale==0) { visible=false;break; }
                parent.translateAndRotateAndScale(pose);
            }
            if (visible) branch.root.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,overlay);
            pose.popPose();
        }
        pose.popPose();
    }
}
