package com.yami.yamidelight.head.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.client.animation.gecko.AnimationManager;
import com.github.tartaricacid.touhoulittlemaid.client.entity.GeckoMaidEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.PlayState;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.AnimationBuilder;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.event.predicate.AnimationEvent;
import com.yami.yamidelight.head.client.MaidHeadHider;
import com.yami.yamidelight.head.client.MaidDeathAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AnimationManager.class, remap = false)
public abstract class MaidAnimationMixin {
    @Inject(method = "predicateMain", at = @At("HEAD"), cancellable = true)
    private void yami$death(AnimationEvent<GeckoMaidEntity<?>> event, CallbackInfoReturnable<PlayState> cir) {
        if (com.yami.yamidelight.head.MaidHanging.active(event.getAnimatableEntity().getEntity())) {
            MaidDeathAnimation.tick();
            event.getController().setAnimation(new AnimationBuilder().playAndHold("yamidelight_hanging"));
            cir.setReturnValue(PlayState.CONTINUE);
            return;
        }
        if (MaidHeadHider.isHeadless(event.getAnimatableEntity().getEntity().getId())) {
            MaidDeathAnimation.tick();
            event.getController().setAnimation(new AnimationBuilder().playAndHold("yamidelight_dead"));
            cir.setReturnValue(PlayState.CONTINUE);
        }
    }
}
