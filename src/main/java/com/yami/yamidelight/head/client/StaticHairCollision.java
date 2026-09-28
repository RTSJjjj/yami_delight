package com.yami.yamidelight.head.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Client-only, event-invalidated static fitting. No gravity, ticking strands or per-frame search. */
public final class StaticHairCollision {
    private static final Map<BlockEntity, Cache> INSTANCES = new WeakHashMap<>();
    private static final Map<Object, Strand> MESHES = new IdentityHashMap<>();
    private static Context current;
    // Spread the initial fitting of a roomful of heads across ticks instead of stalling one frame.
    private static int remaining = 12;
    record Strand(AABB bounds, Vector3f pivot, List<AABB> pieces) {}
    private record Fit(Matrix4f placement, Matrix4f correction) {}
    private static final class Cache {
        final Map<Object, Fit> fits = new IdentityHashMap<>();
        AABB watched;
    }
    private record Context(BlockEntity block, Matrix4f inverseView, Cache cache) {}
    private StaticHairCollision() {}

    public static void clear() { INSTANCES.clear(); MESHES.clear(); current = null; }
    public static void tick() { remaining = 12; }
    static boolean active() { return current != null; }

    static HeadModelGeometry.Scope begin(BlockEntity block, PoseStack pose) {
        Context previous = current;
        current = block.getLevel() == null ? null : new Context(block,
                new Matrix4f(pose.last().pose()).invert(), INSTANCES.computeIfAbsent(block, ignored -> new Cache()));
        return () -> current = previous;
    }

    /** Called by client world update notifications, not polled by the renderer. */
    public static void blockChanged(Level level, BlockPos position) {
        AABB changed = new AABB(position).inflate(1.0); // Neighbor-sensitive stair/fence shapes.
        INSTANCES.forEach((block, cache) -> {
            if (block.getLevel() == level && (block.getBlockPos().equals(position)
                    || cache.watched != null && cache.watched.intersects(changed))) cache.fits.clear();
        });
    }
    public static void chunkChanged(Level level, net.minecraft.world.level.ChunkPos chunk) {
        AABB changed = new AABB(chunk.getMinBlockX()-1,level.getMinBuildHeight(),chunk.getMinBlockZ()-1,
                chunk.getMaxBlockX()+2,level.getMaxBuildHeight(),chunk.getMaxBlockZ()+2);
        INSTANCES.forEach((block, cache) -> {
            if (block.getLevel() == level && cache.watched != null && cache.watched.intersects(changed)) cache.fits.clear();
        });
    }

    static void register(HeadExpression.Mesh mesh, JsonObject batch) {
        if (!batch.has("collision")) return;
        var c = batch.getAsJsonObject("collision");
        // Old resources described whole roots. Never move those if a pack has not been reimported.
        if (!c.has("tip") || !c.get("tip").getAsBoolean() || !c.has("pieces")) return;
        var b = c.getAsJsonArray("bounds"); var p = c.getAsJsonArray("pivot");
        List<AABB> pieces = new ArrayList<>();
        for (var value : c.getAsJsonArray("pieces")) {
            var a = value.getAsJsonArray();
            pieces.add(new AABB(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble(),
                    a.get(3).getAsDouble(),a.get(4).getAsDouble(),a.get(5).getAsDouble()));
        }
        MESHES.put(mesh, new Strand(new AABB(b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble(),
                b.get(3).getAsDouble(), b.get(4).getAsDouble(), b.get(5).getAsDouble()),
                new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat()), List.copyOf(pieces)));
    }
    static void applyMesh(HeadExpression.Mesh mesh, PoseStack pose) {
        if (current == null || HeadHairPlacement.active()) return;
        Strand strand = MESHES.get(mesh);
        if (strand != null) apply(mesh, strand, pose);
    }

    static void apply(Object key, Strand strand, PoseStack pose) {
        if (current == null) return;
        // Keep coordinates relative to this block, avoiding float precision loss far from spawn.
        Matrix4f placement = new Matrix4f(current.inverseView).mul(pose.last().pose());
        Fit fit = current.cache.fits.get(key);
        if (fit == null || !fit.placement.equals(placement, 0.0001F)) {
            if (remaining <= 0) return;
            remaining--;
            fit = new Fit(placement, solve(strand, placement));
            current.cache.fits.put(key, fit);
        }
        pose.mulPose(fit.correction);
    }

    private static Matrix4f solve(Strand strand, Matrix4f placement) {
        if (Math.abs(placement.determinant()) < 1.0E-8F) return new Matrix4f();
        AABB original = transformed(strand.bounds, placement);
        Vector3f pivot = placement.transformPosition(new Vector3f(strand.pivot));
        double radius = 0;
        for (Vector3f corner : corners(original)) radius = Math.max(radius, corner.distance(pivot));
        // Only a terminal joint can rotate; its pivot and all root geometry stay fixed.
        AABB region = new AABB(pivot.x-radius, pivot.y-radius, pivot.z-radius,
                pivot.x+radius, pivot.y+radius, pivot.z+radius).inflate(0.02);
        BlockPos origin = current.block.getBlockPos();
        AABB worldRegion = region.move(origin.getX(), origin.getY(), origin.getZ());
        if (worldRegion.getXsize()*worldRegion.getYsize()*worldRegion.getZsize() > 4096) return new Matrix4f();
        current.cache.watched = current.cache.watched == null ? worldRegion : current.cache.watched.minmax(worldRegion);
        List<AABB> obstacles = new ArrayList<>();
        // Real VoxelShapes: includes the table itself, slabs, stairs and adjacent solid blocks.
        Level level = current.block.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(worldRegion.minX,worldRegion.minY,worldRegion.minZ),
                BlockPos.containing(worldRegion.maxX,worldRegion.maxY,worldRegion.maxZ))) {
            // The skull's own selection/collision box is not an external obstacle. The table is.
            if (pos.equals(origin) && !(current.block instanceof com.yami.yamidelight.head.DissectionTableBlockEntity)) continue;
            if (!level.hasChunkAt(pos)) continue;
            var state = level.getBlockState(pos);
            // A two-cell meal's other half is also our own coarse occupancy box, not terrain.
            if (state.is(current.block.getBlockState().getBlock())) {
                if (state.getBlock() instanceof com.yami.yamidelight.feast.StuffedWinefoxBlock
                        && com.yami.yamidelight.feast.StuffedWinefoxBlock.primary(pos,state).equals(origin)) continue;
                if (state.getBlock() instanceof com.yami.yamidelight.feast.SeaLandWindWinefoxBlock
                        && com.yami.yamidelight.feast.SeaLandWindWinefoxBlock.primary(pos,state).equals(origin)) continue;
            }
            // Include fences and walls using their actual collision shapes, not full-block boxes.
            var shape = state.getCollisionShape(level,pos);
            for (AABB box : shape.toAabbs()) obstacles.add(box.move(pos.getX()-origin.getX(),pos.getY()-origin.getY(),pos.getZ()-origin.getZ()));
        }
        if (!hits(strand, placement, obstacles)) return new Matrix4f();
        Matrix4f inverse = new Matrix4f(placement).invert();
        // Conservative terminal bend only. No root rotation or translation fallback.
        for (int degrees = 5; degrees <= 20; degrees += 5) {
            for (int axis = 0; axis < 2; axis++) for (int sign : new int[]{1,-1}) {
                float angle = (float)Math.toRadians(degrees * sign);
                Matrix4f world = new Matrix4f().translate(pivot);
                if (axis == 0) world.rotateX(angle); else world.rotateZ(angle);
                world.translate(-pivot.x, -pivot.y, -pivot.z);
                if (!hits(strand, new Matrix4f(world).mul(placement), obstacles))
                    return new Matrix4f(inverse).mul(world).mul(placement);
            }
        }
        return new Matrix4f(); // No gentle collision-free solution: keep the authored pose.
    }
    private static boolean overlap(AABB a, AABB b) {
        double e = 0.0001;
        return a.maxX>b.minX+e && a.minX<b.maxX-e && a.maxY>b.minY+e && a.minY<b.maxY-e
                && a.maxZ>b.minZ+e && a.minZ<b.maxZ-e;
    }
    private static boolean hits(Strand strand, Matrix4f placement, List<AABB> boxes) {
        // Test each authored cube, not the empty volume between distant cubes in a whole strand.
        for (AABB piece : strand.pieces) {
            AABB bounds = transformed(piece,placement);
            for (AABB box : boxes) if (overlap(bounds, box)) return true;
        }
        return false;
    }
    private static List<Vector3f> corners(AABB b) {
        List<Vector3f> points = new ArrayList<>(8);
        for (int i=0;i<8;i++) points.add(new Vector3f((float)((i&1)==0?b.minX:b.maxX),
                (float)((i&2)==0?b.minY:b.maxY), (float)((i&4)==0?b.minZ:b.maxZ)));
        return points;
    }
    private static AABB transformed(AABB box, Matrix4f transform) {
        double x=Double.POSITIVE_INFINITY,y=x,z=x, xx=Double.NEGATIVE_INFINITY,yy=xx,zz=xx;
        for (Vector3f p : corners(box)) {
            transform.transformPosition(p);
            x=Math.min(x,p.x);y=Math.min(y,p.y);z=Math.min(z,p.z);
            xx=Math.max(xx,p.x);yy=Math.max(yy,p.y);zz=Math.max(zz,p.z);
        }
        return new AABB(x,y,z,xx,yy,zz);
    }
}
