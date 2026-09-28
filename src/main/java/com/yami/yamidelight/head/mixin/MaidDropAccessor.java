package com.yami.yamidelight.head.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = EntityMaid.class, remap = false)
public interface MaidDropAccessor {
    @Accessor("alreadyDropped")
    void yami$setAlreadyDropped(boolean value);
}
