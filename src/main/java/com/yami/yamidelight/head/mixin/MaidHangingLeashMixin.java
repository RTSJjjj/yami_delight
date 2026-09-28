package com.yami.yamidelight.head.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yami.yamidelight.head.MaidHanging;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathfinderMob.class)
public abstract class MaidHangingLeashMixin {
    @Inject(method = "handleLeashAtDistance", at = @At("HEAD"), cancellable = true)
    private void yami$noElasticForce(Entity holder, float distance, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof EntityMaid maid && MaidHanging.engaged(maid)) cir.setReturnValue(false);
    }
}
