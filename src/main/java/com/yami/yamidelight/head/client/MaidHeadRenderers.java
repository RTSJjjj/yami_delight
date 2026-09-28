package com.yami.yamidelight.head.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yami.yamidelight.YamiConfig;
import com.yami.yamidelight.head.MaidHeadBlock;
import com.yami.yamidelight.head.MaidHeadBlockEntity;
import com.yami.yamidelight.head.MaidHeadContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * Draws a mounted head and the same head as an item.
 *
 * <p>Both go through {@link MaidHeadModel#draw}, so a head in the hand and the same head on a wall cannot
 * drift apart, and both are handed the model the head's own data resolves to rather than a shared one.
 *
 * <p>The mount transform is the whole of the placement rule. A wall mount is moved to the centre of the
 * face it hangs on, turned so the head's face looks away from the wall, and then pushed out by half its
 * own depth plus the configured gap - the half depth comes from the model itself, so a maid with a long
 * fringe still has her hair clear of the wall instead of buried in it.  A floor head now keeps its
 * authored Z_STAGE0 upright frame instead of borrowing an item-frame-like lying pose.
 */
public final class MaidHeadRenderers {
    private static final float GROUND_Y_OFFSET = 2.0F / 16.0F;
    private MaidHeadRenderers() {}

    /** Both native and baked stage heads face -Z before the saved placement yaw. */
    private static void groundPose(MaidHeadBlockEntity head, PoseStack pose) {
        if (head.getBlockState().getValue(MaidHeadBlock.FACING) != Direction.UP) return;
        Direction direction = head.getBlockState().getValue(MaidHeadBlock.HORIZONTAL_FACING);
        pose.translate(0.5F, GROUND_Y_OFFSET, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - direction.toYRot()));
        pose.translate(-0.5F, 0.0F, -0.5F);
    }

    public static final class BlockRenderer implements BlockEntityRenderer<MaidHeadBlockEntity> {
        public BlockRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public void render(MaidHeadBlockEntity head, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
            boolean winefox=!head.data().hasModel()||head.data().modelId().toLowerCase(java.util.Locale.ROOT).contains("winefox");
            try (var hairstyle = HeadHairPlacement.begin(head,head.getBlockState().getValue(MaidHeadBlock.FACING)==Direction.UP,winefox);
                 var collision = StaticHairCollision.begin(head,pose)) {
                if (head.isDissectedHead()) {
                    pose.pushPose();
                    groundPose(head, pose);
                    DissectionTableRenderer.renderDissectedHeadBlock(head.data(), head.dissectionStage(), pose, buffers, light, overlay);
                    pose.popPose();
                    return;
                }
                MaidHeadModel model = MaidHeadModels.get(head.data());
                if (model == null) {
                    return;
                }
                Direction facing = head.getBlockState().getValue(MaidHeadBlock.FACING);
                pose.pushPose();
                groundPose(head, pose);
                // Out of the wall first, in the block's own axes, then the pose: this keeps the distance the
                // head stands off the wall independent of which way it ends up facing.
                float gap = model.size().z * model.blockScale() * 0.5F + YamiConfig.headGap();
                pose.translate(
                        0.5F - facing.getStepX() * (0.5F - gap),
                        0.5F - facing.getStepY() * (0.5F - gap),
                        0.5F - facing.getStepZ() * (0.5F - gap));
                if (facing == Direction.UP) {
                    // groundPose already applies the saved yaw and two-pixel lift; keep the head upright.
                } else if (facing == Direction.DOWN) {
                    pose.mulPose(Axis.ZP.rotationDegrees(-90.0F));
                    pose.mulPose(Axis.YP.rotationDegrees(270.0F));
                } else {
                    // Exactly the yaw an entity renderer gives a mob that is looking along this direction:
                    // the head is a slice of a maid model, so posing it as that maid is what keeps the face
                    // pointing where the block says it does.
                    pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
                }
                model.draw(pose, buffers, light, overlay, false);
                pose.popPose();
            }
        }
    }

    /**
     * The item form. Vanilla item rendering has already centred the pose on the item and applied the
     * display transforms from the item model, which are the vanilla head's, so all this does is draw the
     * head in the space those transforms were written for.
     */
    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
        public ItemRenderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
            if (DissectionTableRenderer.renderDissectedHeadItem(stack, pose, buffers, light, overlay)) {
                return;
            }
            MaidHeadModel model = MaidHeadModels.get(MaidHeadContent.dataOf(stack));
            if (model == null) {
                return;
            }
            pose.pushPose();
            // BEWLR receives block space (0..1), not the centred entity render space.
            pose.translate(0.5F, 0.75F, 0.5F);
            model.draw(pose, buffers, light, overlay, true);
            pose.popPose();
        }
    }

    /** Hands the item renderer to the game, created on the first request so Minecraft is up by then. */
    public static final IClientItemExtensions ITEM_EXTENSION = new IClientItemExtensions() {
        private BlockEntityWithoutLevelRenderer renderer;

        @Override
        public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            if (renderer == null) {
                renderer = new ItemRenderer();
            }
            return renderer;
        }
    };
}
