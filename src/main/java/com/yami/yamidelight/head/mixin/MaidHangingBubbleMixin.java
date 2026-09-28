package com.yami.yamidelight.head.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.ChatBubbleManager;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.yami.yamidelight.head.MaidHanging;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChatBubbleManager.class, remap = false)
public abstract class MaidHangingBubbleMixin {
    @Shadow @Final private EntityMaid maid;
    @Inject(method = "addChatBubble", at = @At("HEAD"), cancellable = true)
    private void yami$noHangingBubble(IChatBubbleData data, CallbackInfoReturnable<Long> cir) {
        if (MaidHanging.engaged(maid)) cir.setReturnValue(-1L);
    }
}
