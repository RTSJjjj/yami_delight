package com.yami.yamidelight.head.client.mixin;

import com.yami.yamidelight.head.client.StaticHairCollision;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class HairCollisionWorldMixin {
    @Inject(method="onChunkLoaded", at=@At("TAIL"))
    private void yami$loaded(net.minecraft.world.level.ChunkPos chunk, CallbackInfo ci) {
        StaticHairCollision.chunkChanged((ClientLevel)(Object)this, chunk);
    }
    @Inject(method="unload", at=@At("TAIL"))
    private void yami$unloaded(net.minecraft.world.level.chunk.LevelChunk chunk, CallbackInfo ci) {
        StaticHairCollision.chunkChanged((ClientLevel)(Object)this, chunk.getPos());
    }
    @Inject(method="sendBlockUpdated", at=@At("TAIL"))
    private void yami$changed(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
        StaticHairCollision.blockChanged((ClientLevel)(Object)this, pos);
    }
    @Inject(method="setBlocksDirty", at=@At("TAIL"))
    private void yami$dirty(BlockPos pos, BlockState before, BlockState after, CallbackInfo ci) {
        StaticHairCollision.blockChanged((ClientLevel)(Object)this, pos);
    }
}
