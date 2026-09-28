package com.yami.yamidelight.head.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.yami.yamidelight.head.MaidHanging;

/** The server broadcasts our clip; do not add the original maid death voice on top. */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class MaidDeathSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void yami$muteHangingVoice(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        // TLM sends its voice-pack packet from this method for sounds whose path starts with maid.
        // Our death2 clip uses Level.playSound directly, so it remains audible.
        if (MaidHanging.engaged((EntityMaid)(Object)this) && sound.getLocation().getPath().startsWith("maid")) ci.cancel();
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true, require = 1)
    private void yami$replaceDeathVoice(CallbackInfoReturnable<SoundEvent> cir) {
        cir.setReturnValue(null);
    }
}
