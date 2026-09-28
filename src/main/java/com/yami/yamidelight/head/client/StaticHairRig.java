package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/** Only terminal secondary joints may bend. Hair roots, bangs and single-segment strands stay fixed. */
final class StaticHairRig {
    private record Node(BedrockPart part, StaticHairCollision.Strand strand, List<Node> children, boolean movable) {}
    private final Node root;

    StaticHairRig(BedrockPart head, Map<String, BedrockPart> names) {
        Set<BedrockPart> hair = Collections.newSetFromMap(new IdentityHashMap<>());
        names.forEach((name, part) -> {
            if (name.matches("(?i)(Long(Left|Right)?Hair2?|(Left|Right)(Side|Long)Hair2?|Bangs|hair[12]?(left|right)2?)")) hair.add(part);
        });
        root = node(head, hair, false);
    }
    private static Node node(BedrockPart part, Set<BedrockPart> hair, boolean inStrand) {
        boolean isHair = hair.contains(part);
        boolean tip = inStrand && isHair && !hasHairChild(part,hair);
        List<Node> children = new ArrayList<>();
        for (BedrockPart child : part.children) children.add(node(child, hair, inStrand || isHair));
        StaticHairCollision.Strand strand = null;
        if (tip) {
            List<AABB> pieces = HeadModelGeometry.collectBoxes(part);
            if (!pieces.isEmpty()) {
                AABB bounds = pieces.getFirst();
                for (AABB piece : pieces) bounds = bounds.minmax(piece);
                strand = new StaticHairCollision.Strand(bounds,new Vector3f(),pieces);
            }
        }
        return new Node(part, strand, List.copyOf(children), strand != null || children.stream().anyMatch(Node::movable));
    }
    private static boolean hasHairChild(BedrockPart part, Set<BedrockPart> hair) {
        for (BedrockPart child : part.children) if (hair.contains(child) || hasHairChild(child,hair)) return true;
        return false;
    }
    void render(PoseStack pose, VertexConsumer buffer, int light, int overlay) { render(root,pose,buffer,light,overlay); }
    private static void render(Node node, PoseStack pose, VertexConsumer buffer, int light, int overlay) {
        BedrockPart part = node.part;
        if (!node.movable || !StaticHairCollision.active()) { part.render(pose,buffer,light,overlay); return; }
        int collapsed = (Math.abs(part.xScale)<1.0E-5F?1:0)+(Math.abs(part.yScale)<1.0E-5F?1:0)+(Math.abs(part.zScale)<1.0E-5F?1:0);
        if (!part.visible || collapsed >= 2) return;
        int lit = part.illuminated ? 15728880 : light;
        pose.pushPose();
        part.translateAndRotateAndScale(pose);
        if (node.strand != null) StaticHairCollision.apply(node, node.strand, pose);
        HeadModelGeometry.renderCubes(part,pose,buffer,lit,overlay);
        for (Node child : node.children) render(child,pose,buffer,lit,overlay);
        pose.popPose();
    }
}
