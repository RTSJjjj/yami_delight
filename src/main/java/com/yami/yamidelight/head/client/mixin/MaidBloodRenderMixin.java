package com.yami.yamidelight.head.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yami.yamidelight.head.client.MaidBloodSpray;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GeoReplacedEntityRenderer.class, remap = false)
public abstract class MaidBloodRenderMixin {
    @org.spongepowered.asm.mixin.Unique
    private Runnable yami$restoreMouth = () -> {};
    @org.spongepowered.asm.mixin.Unique
    private Runnable yami$restoreAllHead = () -> {};
    @org.spongepowered.asm.mixin.Shadow
    public abstract net.minecraft.resources.ResourceLocation getTextureLocation(LivingEntity entity);
    @org.spongepowered.asm.mixin.Shadow
    protected com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity<?> currentAnimatable;
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/core/AnimatableEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void yami$beginHangingSway(LivingEntity entity,
            com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity<?> animatable,
            float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (entity instanceof EntityMaid maid) com.yami.yamidelight.head.client.MaidHangingSway.begin(maid, pose, partialTick);
    }
    @Inject(method = "renderEarly(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V", at = @At("RETURN"))
    private void yami$spray(LivingEntity entity, PoseStack pose, float partialTick,
            MultiBufferSource buffers, VertexConsumer vertices, int light, int overlay,
            float r, float g, float b, float alpha, CallbackInfo ci) {
        if (entity instanceof EntityMaid maid) {
            yami$restoreMouth.run();
            yami$restoreMouth = () -> {};
            yami$restoreAllHead.run();
            yami$restoreAllHead = () -> {};
            if (currentAnimatable != null) com.yami.yamidelight.head.client.MaidFinalPose.applyLive(
                    maid, currentAnimatable.getCurrentModel(), partialTick, pose);
            if (currentAnimatable != null) yami$restoreMouth = com.yami.yamidelight.head.client.MaidHangingMouth.render(
                    maid, currentAnimatable.getCurrentModel(), pose, buffers, light, overlay, getTextureLocation(maid));
            MaidBloodSpray.render(maid, pose, partialTick);
            if (currentAnimatable != null) yami$restoreAllHead = com.yami.yamidelight.head.client.AllHeadParts.prepareLive(
                    maid, currentAnimatable.getCurrentModel(), pose, buffers, light, overlay);
            if (currentAnimatable != null) com.yami.yamidelight.head.client.MaidHangingDrips.render(
                    maid, currentAnimatable.getCurrentModel(), pose, partialTick);
        }
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;Lcom/github/tartaricacid/touhoulittlemaid/geckolib3/core/AnimatableEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void yami$restoreMouthAfterRender(LivingEntity entity,
            com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity<?> animatable,
            float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        yami$restoreMouth.run();
        yami$restoreMouth = () -> {};
        yami$restoreAllHead.run();
        yami$restoreAllHead = () -> {};
        if (entity instanceof EntityMaid maid) com.yami.yamidelight.head.client.MaidHangingSway.end(maid);
    }
}
