package com.yami.yamidelight.head.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yami.yamidelight.head.client.MaidHeadHider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GeckoMaidEntity.class, remap = false)
public abstract class MaidPoseMixin {
    @Inject(method = "setCustomAnimations", at = @At("RETURN"))
    private void yami$headless(CallbackInfoReturnable<Boolean> cir) {
        GeckoMaidEntity<?> gecko = (GeckoMaidEntity<?>) (Object) this;
        if (gecko.getEntity() instanceof EntityMaid maid) {
            MaidHeadHider.hideHead(maid);
            MaidHeadHider.keepHangingBody(maid);
        }
    }
}
