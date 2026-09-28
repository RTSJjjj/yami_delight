package com.yami.yamidelight.head.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.yami.yamidelight.head.MaidHanging;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MaidHangingMoveMixin {
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 yami$limitInitialFall(Vec3 movement) {
        if ((Object)this instanceof EntityMaid maid && MaidHanging.falling(maid)) {
            double remaining = Math.min(0, MaidHanging.state(maid).getDouble("fall_target_y") - maid.getY());
            return new Vec3(0, Math.max(remaining, Math.min(0, movement.y)), 0);
        }
        return movement;
    }
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void yami$freeze(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((Object)this instanceof EntityMaid maid && MaidHanging.active(maid)) {
            maid.setDeltaMovement(Vec3.ZERO);
            ci.cancel();
        }
    }
}
