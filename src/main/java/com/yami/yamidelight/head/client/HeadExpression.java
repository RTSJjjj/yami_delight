package com.yami.yamidelight.head.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yami.yamidelight.YamiConfig;
import com.yami.yamidelight.YamiDelight;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A hand edited face, drawn over the head it belongs to.
 *
 * <p>Some maid models switch between several eyebrows, eyelids and mouths by moving one of them forward,
 * which is fine while the maid is animating and useless on a trophy that never animates: the copies pile
 * up in the same place. Redrawing those parts once, in Blockbench, and pasting that mesh onto the head is
 * the way out - the mesh sits in the model's own coordinates, so nothing has to be fitted at run time.
 *
 * <p>The file is produced by {@code import-head-expression.cjs} and names three things: which models it
 * belongs to (the mesh only fits the model it was drawn from), the bones it replaces and therefore
 * hides, and its own texture - which is the model's texture with the face redrawn, so the whole head
 * wears it.
 */
public final class HeadExpression {
    private static final String FOLDER = "head_expression";
    private static Map<String, Mesh> byModel;
    /** Explicit face styles are carried by head data rather than inferred from a model id. */
    private static Map<String, Mesh> byName;
    private static boolean reported;

    private HeadExpression() {}

    /**
     * One mesh, ready to draw. Vertices are in the same space as the head's own cubes, so the caller only
     * has to put the pose where the head bone is.
     */
    public record Mesh(String name, List<String> hide, ResourceLocation textureFile, byte[] texture,
                       float[] vertices, float[] uvs, int[][] quads, float[] offset) {
        public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay) {
            render(pose, buffer, light, overlay, true, true);
        }

        /**
         * The winefox Z dissection references intentionally have no eye or mouth cubes.  Their
         * expression mesh is the same one used by the ordinary trophy, but the two harvested parts
         * must be independently suppressible.  Winefox eye pieces live below -0.52 in this local
         * expression frame; the remaining visible face piece is the mouth/tongue area above it.
         */
        public void render(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                           boolean showEyes, boolean showMouth) {
            pose.pushPose();
            StaticHairCollision.applyMesh(this, pose);
            // Blockbench pixels: +X right, +Y up, +Z towards the back of the head.
            pose.translate(offset[0] / 16F, -offset[1] / 16F, offset[2] / 16F);
            PoseStack.Pose last = pose.last();
            for (int[] quad : quads) {
                if (!showEyes && isEyeQuad(quad)) continue;
                if (!showMouth && !isEyeQuad(quad)) continue;
                Vector3f normal = faceNormal(quad);
                for (int corner = 0; corner < 4; corner++) {
                    int vertex = quad[corner * 2] * 3;
                    int uv = quad[corner * 2 + 1] * 2;
                    buffer.addVertex(last, vertices[vertex], vertices[vertex + 1], vertices[vertex + 2])
                            .setColor(255, 255, 255, 255)
                            .setUv(uvs[uv], uvs[uv + 1])
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(last, normal.x, normal.y, normal.z);
                }
            }
            pose.popPose();
        }

        private boolean isEyeQuad(int[] quad) {
            float y = 0.0F;
            for (int corner = 0; corner < 4; corner++) y += vertices[quad[corner * 2] * 3 + 1];
            return y * 0.25F < -0.52F;
        }

        /** The normal of one quad, taken from its first three corners. */
        private Vector3f faceNormal(int[] quad) {
            int a = quad[0] * 3, b = quad[2] * 3, c = quad[4] * 3;
            float ax = vertices[b] - vertices[a], ay = vertices[b + 1] - vertices[a + 1], az = vertices[b + 2] - vertices[a + 2];
            float bx = vertices[c] - vertices[a], by = vertices[c + 1] - vertices[a + 1], bz = vertices[c + 2] - vertices[a + 2];
            Vector3f normal = new Vector3f(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx);
            return normal.lengthSquared() < 1.0E-12F ? normal.set(0.0F, 1.0F, 0.0F) : normal.normalize();
        }
    }

    /**
     * The face drawn for this model, or null when there is none or it is switched off.
     *
     * <p>Matching is deliberately loose. One mesh fits a whole family of models - the wine fox variants
     * are the same head with different skins - so the id only has to contain the name the mesh was drawn
     * for. A model that has the parts but places them somewhere else is rejected by the caller, which is
     * what stops a chibi variant from wearing a face drawn for a full sized one.
     */
    @Nullable
    public static Mesh forModel(String modelId) {
        if (!YamiConfig.expressionEnabled() || modelId == null || modelId.isEmpty()) {
            return null;
        }
        Map<String, Mesh> all = meshes();
        Mesh exact = all.get(modelId);
        if (exact != null) {
            return exact;
        }
        String lower = modelId.toLowerCase(java.util.Locale.ROOT);
        for (Map.Entry<String, Mesh> entry : all.entrySet()) {
            // Compare on the model name without its namespace, so "geckolib:winefox_jk" matches a mesh
            // drawn for "geckolib:winefox".
            int colon = entry.getKey().indexOf(':');
            String name = (colon < 0 ? entry.getKey() : entry.getKey().substring(colon + 1))
                    .toLowerCase(java.util.Locale.ROOT);
            if (!name.isEmpty() && lower.contains(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Whether this mesh was drawn for exactly this model, which is when its own texture is used too. */
    public static boolean isExact(@Nullable Mesh mesh, String modelId) {
        return mesh != null && meshes().get(modelId) == mesh;
    }

    /**
     * Resolves a named, persistent face style.  Unlike {@link #forModel(String)}, this deliberately
     * does not consult the optional global expression toggle: a feast has already changed the head and
     * that result must still be visible after it is picked back up.
     */
    @Nullable
    public static Mesh forFaceStyle(String style) {
        if (style == null || style.isBlank()) {
            return null;
        }
        meshes();
        return byName.get(style);
    }

    public static synchronized void invalidate() {
        byModel = null;
        byName = null;
    }

    private static synchronized Map<String, Mesh> meshes() {
        if (byModel == null) {
            Map<String, Mesh> found = new HashMap<>();
            Map<String, Mesh> named = new HashMap<>();
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            for (ResourceLocation file : manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).keySet()) {
                Resource resource = manager.getResource(file).orElse(null);
                if (resource == null) {
                    continue;
                }
                try (InputStream stream = resource.open()) {
                    read(found, named, manager, file, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException | RuntimeException e) {
                    report(e);
                }
            }
            byModel = found;
            byName = named;
        }
        return byModel;
    }

    private static void read(Map<String, Mesh> into, Map<String, Mesh> named, ResourceManager manager,
                             ResourceLocation file, String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        ResourceLocation texture = ResourceLocation.tryParse(root.get("texture").getAsString());
        byte[] png = texture == null ? null : bytes(manager.getResource(texture).orElse(null));
        if (texture == null || png == null) {
            report(null);
            return;
        }
        List<String> hide = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("hide")) {
            hide.add(element.getAsString());
        }
        Mesh mesh = new Mesh(baseName(file.getPath()), List.copyOf(hide), texture, png,
                numbers(root, "vertices"), numbers(root, "uvs"), quads(root),
                root.has("offset") ? numbers(root, "offset") : new float[] {0, 0, 0});
        named.put(mesh.name(), mesh);
        for (JsonElement element : root.getAsJsonArray("apply_to")) {
            into.put(element.getAsString(), mesh);
        }
    }

    /** The file name without folder or extension, used to tell one redrawn face from another. */
    private static String baseName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.endsWith(".json") ? name.substring(0, name.length() - ".json".length()) : name;
    }

    private static float[] numbers(JsonObject root, String member) {
        JsonArray array = root.getAsJsonArray(member);
        float[] values = new float[array.size()];
        for (int i = 0; i < array.size(); i++) {
            values[i] = array.get(i).getAsFloat();
        }
        return values;
    }

    private static int[][] quads(JsonObject root) {
        JsonArray array = root.getAsJsonArray("quads");
        int[][] quads = new int[array.size()][];
        for (int i = 0; i < array.size(); i++) {
            JsonArray quad = array.get(i).getAsJsonArray();
            int[] corners = new int[quad.size()];
            for (int corner = 0; corner < quad.size(); corner++) {
                corners[corner] = quad.get(corner).getAsInt();
            }
            quads[i] = corners;
        }
        return quads;
    }

    @Nullable
    private static byte[] bytes(@Nullable Resource resource) {
        if (resource == null) {
            return null;
        }
        try (InputStream stream = resource.open()) {
            return stream.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static void report(@Nullable Exception e) {
        if (!reported) {
            reported = true;
            YamiDelight.LOGGER.warn("yamidelight: could not read the head expression files ({}); "
                    + "heads will be drawn exactly as the model has them", e == null ? "missing texture" : e);
        }
    }
}
