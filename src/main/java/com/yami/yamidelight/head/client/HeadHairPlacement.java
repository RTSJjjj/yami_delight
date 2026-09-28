package com.yami.yamidelight.head.client;

import com.github.tartaricacid.simplebedrockmodel.client.bedrock.AbstractBedrockModel;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Shared two-pose policy for heads and every dish rendered from a fox brain bowl. No physics. */
final class HeadHairPlacement {
    private enum Mode { NONE, GROUND, DEFAULT }
    private record Support(BlockState own, BlockState below, boolean floorMount, boolean ground) {}
    private static final Map<BlockEntity,Support> CACHE = new WeakHashMap<>();
    private static Mode mode = Mode.NONE;
    private static HeadHairParts fallback;
    private HeadHairPlacement() {}
    static boolean active() { return mode != Mode.NONE; }
    static boolean ground() { return mode == Mode.GROUND; }
    static void clear() { CACHE.clear();fallback=null;mode=Mode.NONE; }
    static HeadModelGeometry.Scope begin(BlockEntity block, boolean floorMount, boolean enabled) {
        Mode previous = mode;
        mode = !enabled ? Mode.NONE : supported(block,floorMount) ? Mode.GROUND : Mode.DEFAULT;
        return ()->mode=previous;
    }
    private static boolean supported(BlockEntity block, boolean floorMount) {
        var level=block.getLevel();
        if (level==null || !floorMount) return false;
        var below=block.getBlockPos().below();
        BlockState state=level.getBlockState(below),own=block.getBlockState();
        Support cached=CACHE.get(block);
        if (cached!=null && cached.own==own && cached.below==state && cached.floorMount==floorMount) return cached.ground;
        boolean thin=state.is(BlockTags.FENCES)||state.is(BlockTags.WALLS)||state.is(BlockTags.FENCE_GATES)
                ||state.getBlock() instanceof FenceBlock||state.getBlock() instanceof WallBlock
                ||state.getBlock() instanceof FenceGateBlock||state.getBlock() instanceof IronBarsBlock;
        boolean ground=!thin && state.isFaceSturdy(level,below,Direction.UP);
        CACHE.put(block,new Support(own,state,floorMount,ground));
        return ground;
    }
    /** Input pose is the authored MHead-local (Blockbench axes) frame. */
    static void draw(MaidHeadModel head, PoseStack pose, MultiBufferSource buffers, ResourceLocation texture, int light, int overlay) {
        if (ground()) FoxHeadFoodRenderer.drawGroundHair(pose,buffers,texture,light,overlay);
        else if (head==null || !head.drawDefaultHair(pose,buffers,texture,light,overlay)) fallback().draw(pose,buffers,texture,light,overlay);
    }
    private static HeadHairParts fallback() {
        if (fallback!=null) return fallback;
        var resource=ResourceLocation.fromNamespaceAndPath("yamidelight","winefox_remains_fallback.json");
        try(var stream=Minecraft.getInstance().getResourceManager().getResourceOrThrow(resource).open()) {
            var model=new AbstractBedrockModel(stream) {};
            fallback=new HeadHairParts(model.getModelMap().get("MHead"),model.getModelMap());
            return fallback;
        } catch(java.io.IOException ex) { throw new IllegalStateException("Cannot read default winefox hair",ex); }
    }
}
