package com.yami.yamidelight.head.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Just enough of a Bedrock geometry file to measure one bone's subtree.
 *
 * <p>The drawing itself is left to the Bedrock model renderer Touhou Little Maid ships, because that is
 * the code the maid's own model is drawn with and matching it exactly is the whole point. What that
 * renderer cannot do is answer "how big is the head and where is its middle", and a head that hangs on a
 * wall needs both: this class answers those two questions from the same file.
 *
 * <p>The conventions are the renderer's own, and they are the reason a head does not come out mirrored
 * or upside down. Bedrock measures Y upwards from the feet while Minecraft models measure it downwards
 * from the shoulders, so a bone pivot at {@code [0, 33.05, 0]} becomes {@code (0, 24 - 33.05, 0)}; a
 * cube's own pivot, rotation and inflate are included before the bone chain is applied.
 */
final class BedrockGeometry {
    /** Bedrock's Y axis grows upwards, Minecraft's model space grows downwards from this height. */
    private static final float SHOULDER_HEIGHT = 24.0F;
    private static final float UNITS_PER_BLOCK = 16.0F;

    private record Bone(String name, String parent, float[] pivot, float[] rotation, List<float[]> cubes) {}

    private final Map<String, Bone> bones;

    private BedrockGeometry(Map<String, Bone> bones) {
        this.bones = bones;
    }

    @Nullable
    static BedrockGeometry parse(String json) {
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return null;
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
        JsonObject geometry = geometryBlock(root);
        if (geometry == null) {
            return null;
        }
        JsonElement boneArray = geometry.get("bones");
        if (boneArray == null || !boneArray.isJsonArray()) {
            return null;
        }
        Map<String, Bone> bones = new LinkedHashMap<>();
        for (JsonElement element : boneArray.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject bone = element.getAsJsonObject();
            String name = string(bone, "name");
            if (name.isEmpty()) {
                continue;
            }
            float[] pivot = floats(bone, "pivot", new float[] {0.0F, 0.0F, 0.0F});
            bones.put(name, new Bone(
                    name,
                    string(bone, "parent"),
                    pivot,
                    floats(bone, "rotation", new float[] {0.0F, 0.0F, 0.0F}),
                    cubes(bone, pivot)));
        }
        return bones.isEmpty() ? null : new BedrockGeometry(bones);
    }

    /**
     * The geometry block, whichever of the two shapes the file uses.
     *
     * <p>Yes Steve Model writes the modern shape, {@code minecraft:geometry} as an array of one geometry.
     * Touhou Little Maid's own models use the older shape, where the block is named after the model
     * itself - {@code geometry.model} - and holds its bones directly. Both are rendered by the same
     * renderer, and both are measured by this class.
     */
    @Nullable
    private static JsonObject geometryBlock(JsonObject root) {
        JsonElement modern = root.get("minecraft:geometry");
        if (modern != null && modern.isJsonArray() && !modern.getAsJsonArray().isEmpty()) {
            JsonElement first = modern.getAsJsonArray().get(0);
            if (first.isJsonObject()) {
                return first.getAsJsonObject();
            }
        }
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (!entry.getKey().startsWith("geometry") || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject candidate = entry.getValue().getAsJsonObject();
            if (candidate.has("bones")) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Cubes are converted into the frame the renderer draws them in: relative to the bone's pivot, with
     * the Y axis flipped, because a Bedrock cube is stored by its lowest corner and Minecraft model space
     * hangs from the shoulders.
     */
    private static List<float[]> cubes(JsonObject bone, float[] pivot) {
        JsonElement element = bone.get("cubes");
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<float[]> cubes = new ArrayList<>();
        for (JsonElement candidate : element.getAsJsonArray()) {
            if (!candidate.isJsonObject()) {
                continue;
            }
            JsonObject cube = candidate.getAsJsonObject();
            float[] origin = floats(cube, "origin", new float[] {0.0F, 0.0F, 0.0F});
            float[] size = floats(cube, "size", new float[] {0.0F, 0.0F, 0.0F});
            float[] cubePivot = floats(cube, "pivot", pivot);
            float[] rotation = floats(cube, "rotation", new float[] {0, 0, 0});
            float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : 0F;
            // Keep all eight rotated vertices: a pre-rotation AABB can be much larger
            // than the rendered hair, shrinking the entire inventory icon.
            Matrix4f transform = new Matrix4f()
                    .translate(cubePivot[0] - pivot[0], pivot[1] - cubePivot[1], cubePivot[2] - pivot[2])
                    .rotateZYX((float) Math.toRadians(rotation[2]),
                            (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[0]));
            float[] vertices = new float[24];
            for (int c = 0; c < 8; c++) {
                Vector3f v = new Vector3f(
                        origin[0] - cubePivot[0] + ((c & 1) == 0 ? -inflate : size[0] + inflate),
                        cubePivot[1] - origin[1] - size[1] + ((c & 2) == 0 ? -inflate : size[1] + inflate),
                        origin[2] - cubePivot[2] + ((c & 4) == 0 ? -inflate : size[2] + inflate));
                transform.transformPosition(v);
                vertices[c * 3] = v.x;
                vertices[c * 3 + 1] = v.y;
                vertices[c * 3 + 2] = v.z;
            }
            cubes.add(vertices);
        }
        return cubes;
    }

    private static float[] floats(JsonObject parent, String member, float[] fallback) {
        JsonElement element = parent.get(member);
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        JsonArray array = element.getAsJsonArray();
        float[] values = fallback.clone();
        for (int i = 0; i < Math.min(array.size(), 3); i++) {
            JsonElement entry = array.get(i);
            if (entry.isJsonPrimitive()) {
                values[i] = entry.getAsFloat();
            }
        }
        return values;
    }

    private static String string(JsonObject parent, String member) {
        JsonElement element = parent.get(member);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    /**
     * The bone to cut out of the model. YSM models call the head joint {@code MHead} and put the head,
     * hair, face and eyes underneath it, but a model written by hand may only have {@code Head}, so the
     * search widens one step at a time and never silently picks something else.
     */
    @Nullable
    String headBone() {
        for (String preferred : List.of("MHead", "Head")) {
            if (bones.containsKey(preferred)) {
                return preferred;
            }
        }
        for (String name : bones.keySet()) {
            if (name.toLowerCase(Locale.ROOT).equals("mhead")) {
                return name;
            }
        }
        for (String name : bones.keySet()) {
            if (name.toLowerCase(Locale.ROOT).equals("head")) {
                return name;
            }
        }
        return null;
    }

    List<String> subtree(String root) {
        List<String> names = new ArrayList<>();
        collect(root, names);
        return names;
    }

    /** A bone's own pivot, in the pixels the file writes it in. */
    @Nullable
    float[] pivotOf(String bone) {
        Bone found = bones.get(bone);
        return found == null ? null : found.pivot().clone();
    }

    /**
     * The skull inside the head group, which is what the head is measured by.
     *
     * <p>Measuring everything under {@code MHead} would measure the hairstyle too, and a wine fox with
     * a hat half a block wide and hair down her back would then hang on the wall centred on her fringe
     * with her face somewhere off to one side. The skull is a small cube at a fixed place in every model
     * seen so far, so when one is there it decides the centre, the size and how far the head stands off
     * the wall; the rest of the group is still drawn, it just no longer decides the placement.
     */
    @Nullable
    String skull(String headBone) {
        String fallback = null;
        for (String name : subtree(headBone)) {
            Bone bone = bones.get(name);
            if (bone == null || bone.cubes().isEmpty()) {
                continue;
            }
            if (name.toLowerCase(Locale.ROOT).equals("head")) {
                return name;
            }
            if (fallback == null) {
                fallback = name;
            }
        }
        return fallback;
    }

    private void collect(String name, List<String> into) {
        if (!bones.containsKey(name) || into.contains(name)) {
            return;
        }
        into.add(name);
        for (Bone bone : bones.values()) {
            if (name.equals(bone.parent())) {
                collect(bone.name(), into);
            }
        }
    }

    /**
     * Centre and size of a bone's subtree in blocks: {@code [centreX, centreY, centreZ, sizeX, sizeY, sizeZ]}.
     * Null when the subtree holds no cubes at all, which means there is nothing to measure and therefore
     * nothing to centre.
     */
    @Nullable
    float[] measure(String root) {
        return measure(subtree(root));
    }

    /**
     * Centre and size of the cubes a single bone owns, ignoring everything hanging off it. This is what a
     * head is placed by: the skull, never the hairstyle.
     */
    @Nullable
    float[] measureOwn(String bone) {
        return bones.containsKey(bone) ? measure(List.of(bone)) : null;
    }

    /** Centre and size of a bone's subtree, leaving out the bones that are not drawn. */
    @Nullable
    float[] measure(String root, Set<String> skip) {
        List<String> names = subtree(root);
        Set<String> hidden = new java.util.HashSet<>();
        for (String bone : skip) hidden.addAll(subtree(bone));
        names.removeIf(hidden::contains);
        return names.isEmpty() ? null : measure(names);
    }

    @Nullable
    private float[] measure(List<String> names) {
        Matrix4f[] matrices = new Matrix4f[names.size()];
        boolean any = false;
        for (int i = 0; i < names.size(); i++) {
            matrices[i] = matrixOf(names.get(i));
            any |= !bones.get(names.get(i)).cubes().isEmpty();
        }
        if (!any) {
            return null;
        }
        Vector3f min = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        Vector3f corner = new Vector3f();
        for (int i = 0; i < names.size(); i++) {
            for (float[] box : bones.get(names.get(i)).cubes()) {
                for (int c = 0; c < 8; c++) {
                    corner.set(
                            box[c * 3], box[c * 3 + 1], box[c * 3 + 2]);
                    matrices[i].transformPosition(corner);
                    min.min(corner);
                    max.max(corner);
                }
            }
        }
        return new float[] {
                (min.x + max.x) / 2.0F / UNITS_PER_BLOCK,
                (min.y + max.y) / 2.0F / UNITS_PER_BLOCK,
                (min.z + max.z) / 2.0F / UNITS_PER_BLOCK,
                (max.x - min.x) / UNITS_PER_BLOCK,
                (max.y - min.y) / UNITS_PER_BLOCK,
                (max.z - min.z) / UNITS_PER_BLOCK
        };
    }

    /** Measure the rendered final pose, rather than the unanimated geometry bounds. */
    float[] measureBaked(String root, Map<String, com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart> parts) {
        Vector3f min = new Vector3f(Float.MAX_VALUE), max = new Vector3f(-Float.MAX_VALUE);
        boolean any = false;
        for (String name : subtree(root)) {
            var part = parts.get(name);
            if (part == null) continue;
            var chain = new ArrayList<com.github.tartaricacid.simplebedrockmodel.client.bedrock.model.BedrockPart>();
            boolean hidden = false;
            for (var p = part; p != null; p = p.getParent()) {
                chain.add(0, p);
                hidden |= p.xScale == 0 && p.yScale == 0 && p.zScale == 0;
            }
            if (hidden) continue;
            Matrix4f matrix = new Matrix4f();
            for (var p : chain) matrix.translate(p.x, p.y, p.z).rotateZYX(p.zRot, p.yRot, p.xRot)
                    .scale(p.xScale, p.yScale, p.zScale);
            for (float[] cube : bones.get(name).cubes()) for (int c = 0; c < 8; c++) {
                var point = new Vector3f(cube[c*3], cube[c*3+1], cube[c*3+2]);
                matrix.transformPosition(point);
                min.min(point); max.max(point); any = true;
            }
        }
        return any ? new float[] {(min.x+max.x)/32F, (min.y+max.y)/32F, (min.z+max.z)/32F,
                (max.x-min.x)/16F, (max.y-min.y)/16F, (max.z-min.z)/16F} : null;
    }

    /**
     * The accumulated transform of one bone, in pixels, exactly as the renderer builds it.
     *
     * <p>A bone with a parent is placed by its distance from that parent's pivot, not by its own pivot:
     * pivots are absolute model coordinates, and the renderer turns them into a parent relative move so
     * that a parent's rotation carries its children along. Getting this wrong would not bend the head -
     * it would drop the whole thing a few blocks below the wall.
     */
    private Matrix4f matrixOf(String name) {
        List<Bone> chain = new ArrayList<>();
        Map<String, Bone> seen = new HashMap<>();
        for (Bone bone = bones.get(name); bone != null; bone = bones.get(bone.parent())) {
            if (seen.put(bone.name(), bone) != null) {
                break;
            }
            chain.add(bone);
        }
        Matrix4f matrix = new Matrix4f();
        for (int i = chain.size() - 1; i >= 0; i--) {
            Bone bone = chain.get(i);
            Bone parent = bone.parent().isEmpty() ? null : bones.get(bone.parent());
            matrix.translate(
                    bone.pivot()[0] - (parent == null ? 0.0F : parent.pivot()[0]),
                    parent == null ? SHOULDER_HEIGHT - bone.pivot()[1] : parent.pivot()[1] - bone.pivot()[1],
                    bone.pivot()[2] - (parent == null ? 0.0F : parent.pivot()[2]));
            float x = (float) Math.toRadians(bone.rotation()[0]);
            float y = (float) Math.toRadians(bone.rotation()[1]);
            float z = (float) Math.toRadians(bone.rotation()[2]);
            if (x != 0.0F || y != 0.0F || z != 0.0F) {
                matrix.rotateZYX(z, y, x);
            }
        }
        return matrix;
    }
}
