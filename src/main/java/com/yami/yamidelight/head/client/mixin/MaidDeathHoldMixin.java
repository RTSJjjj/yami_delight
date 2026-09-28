package com.yami.yamidelight.head.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.AnimatableEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.builder.Animation;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.controller.AnimationController;
import com.yami.yamidelight.head.client.MaidHeadHider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keep feeding the final pose into the bone queues until the server removes the maid. */
@Mixin(value = AnimationController.class, remap = false)
public abstract class MaidDeathHoldMixin {
    @Shadow protected Animation currentAnimation;
    @Shadow @Final protected AnimatableEntity<?> animatable;

    // The first double argument is the clip-local tick; the second is the global tick.
    // TLM 1.5.3 stops non-repeating clips at localTick >= animationLength, including
    // HOLD_ON_LAST_FRAME, and returns without filling this frame's bone queues.
    @ModifyVariable(method = "processCurrentAnimation", at = @At("HEAD"),
            argsOnly = true, ordinal = 0, require = 1)
    private double yami$holdFinalPose(double localTick) {
        if (currentAnimation == null || !(animatable.getEntity() instanceof EntityMaid maid)) return localTick;
        boolean hanging = "yamidelight_hanging".equals(currentAnimation.animationName)
                && com.yami.yamidelight.head.MaidHanging.active(maid);
        if (!hanging && !("yamidelight_dead".equals(currentAnimation.animationName)
                && MaidHeadHider.isHeadless(maid.getId()))) return localTick;
        if (hanging) localTick += com.yami.yamidelight.head.MaidHanging.state(maid).getInt("skip_ticks");
        double length = currentAnimation.animationLength;
        if (!Double.isFinite(length) || length <= 0.0D) return localTick;
        // Stay one representable double below the end: visually the exact final
        // pose, without entering STOPPED, restarting the clip, or freezing the world clock.
        return Math.min(localTick, Math.nextDown(length));
    }
}
