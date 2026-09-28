package com.yami.yamidelight.head.client.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Move the visual leash endpoint to the back, in the body's local frame. */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class MaidLeashAnchorMixin {
    @Inject(method = "getLeashOffset()Lnet/minecraft/world/phys/Vec3;",
            at = @At("RETURN"), cancellable = true)
    private void yami$backLeashAnchor(CallbackInfoReturnable<Vec3> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        Vec3 original = cir.getReturnValue();
        // EntityRenderer rotates this offset by body yaw. Negative local Z is
        // behind the maid; centering X also removes the legacy hand attachment.
        cir.setReturnValue(com.yami.yamidelight.head.client.MaidHangingSway.leashOffset(
                maid, new Vec3(0.0, original.y, -maid.getBbWidth() * 0.5)));
    }
}
