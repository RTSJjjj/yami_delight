package com.yami.yamidelight.head.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yami.yamidelight.head.MaidBodyBlock;
import com.yami.yamidelight.head.MaidBodyBlockEntity;
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


public final class MaidBodyRenderers {
    /** Clearance beyond the body's actual depth; independent of the head configuration. */
    private static final float MOUNT_GAP = 0.01F;
    private MaidBodyRenderers() {}

    public static final class BlockRenderer implements BlockEntityRenderer<MaidBodyBlockEntity> {
        public BlockRenderer(BlockEntityRendererProvider.Context context) {}

        @Override
        public boolean shouldRenderOffScreen(MaidBodyBlockEntity body) { return true; }

        @Override
        public void render(MaidBodyBlockEntity body, float partialTick, PoseStack pose, MultiBufferSource buffers,
                           int light, int overlay) {
            try (var collision = StaticHairCollision.begin(body, pose)) {
                MaidHeadModel model = MaidBodyModels.get(body.data(), body.isRemains());
                if (model == null) {
                    return;
                }
                Direction facing = body.getBlockState().getValue(MaidBodyBlock.FACING);
                pose.pushPose();
                // Out of the wall first, in the block's own axes, then the pose: this keeps the distance the
                // body stands off the wall independent of which way it ends up facing.
                float gap = model.size().z * model.blockScale() * 0.5F + MOUNT_GAP;
                pose.translate(
                        0.5F - facing.getStepX() * (0.5F - gap),
                        0.5F - facing.getStepY() * (0.5F - gap),
                        0.5F - facing.getStepZ() * (0.5F - gap));
                if (facing.getAxis().isVertical()) {
                    // Posed the way a sleeping entity is: on her back for a body on the floor, face down for
                    // one on a ceiling. There is no vanilla body to copy here, so vanilla's own lying down
                    // pose is the closest thing there is.
                    pose.mulPose(Axis.ZP.rotationDegrees(facing == Direction.UP ? 90.0F : -90.0F));
                    pose.mulPose(Axis.YP.rotationDegrees(270.0F));
                } else {
                    // Exactly the yaw an entity renderer gives a mob that is looking along this direction:
                    // the body is a slice of a maid model, so posing it as that maid is what keeps the face
                    // pointing where the block says it does.
                    pose.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
                }
                model.draw(pose, buffers, light, overlay, false,
                        body.interactionData().getBoolean(com.yami.yamidelight.head.DissectionTableBlockEntity.TAIL_REMOVED));
                pose.popPose();
            }
        }
    }

    
    public static final class ItemRenderer extends BlockEntityWithoutLevelRenderer {
        public ItemRenderer() {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
            if (DissectionTableRenderer.renderItemPose(stack, pose, buffers, light, overlay)) return;
            MaidHeadModel model = MaidBodyModels.get(MaidHeadContent.dataOf(stack), stack.is(MaidHeadContent.MAID_REMAINS_ITEM.get()));
            if (model == null) {
                return;
            }
            pose.pushPose();
            // BEWLR receives block space (0..1), not the centred entity render space.
            pose.translate(0.5F, 0.75F, 0.5F);
            model.draw(pose, buffers, light, overlay, true,
                    com.yami.yamidelight.head.DissectionTableBlockEntity.tailRemoved(stack));
            pose.popPose();
        }
    }

    
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
