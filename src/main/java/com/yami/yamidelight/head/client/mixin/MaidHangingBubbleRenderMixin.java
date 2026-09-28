package com.yami.yamidelight.head.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.chatbubble.ChatBubbleRenderer;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.chatbubble.EntityGraphics;
import com.yami.yamidelight.head.MaidHanging;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatBubbleRenderer.class, remap = false)
public abstract class MaidHangingBubbleRenderMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void yami$hideHangingBubbles(EntityGraphics graphics, CallbackInfo ci) {
        if (MaidHanging.engaged(graphics.getMaid())) ci.cancel();
    }
}
