package com.yami.yamidelight.head.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yami.yamidelight.YamiDelight;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Reads one of Touhou Little Maid's own models.
 *
 * <p>These are the same Bedrock geometry files as a YSM model, and the mod's own models are laid out
 * the same way: {@code assets/<namespace>/models/entity/<model>.json} next to
 * {@code assets/<namespace>/textures/entity/<model>.png}, with the model id being
 * {@code <namespace>:<model>}. Twenty of them came from Yes Steve Model and still call the head joint
 * {@code MHead}; the rest call it {@code head}. Both are found by name, so neither family needs its own
 * code path beyond this lookup.
 *
 * <p>There are three places a model can live and all three are searched, cheapest first. Model packs the
 * player downloaded sit in {@code <game>/tlm_custom_pack/<pack>/assets}, which is a plain folder. Packs
 * that ship inside the mod jar - including the built in one - are reached through the resource manager
 * under {@code tlm_custom_pack/...}, which the game nests one level deeper than a resource pack would.
 * And a resource pack that provides {@code assets/<namespace>/models/entity/...} directly is read the
 * normal way.
 */
public final class TlmModelLibrary {
    private static final String PACK_DIR = "tlm_custom_pack";
    private static final String ASSETS_MARKER = "/assets/";
    private static final String MODEL_DIR = "models/entity/";
    private static final String TEXTURE_DIR = "textures/entity/";
    private static final String MODEL_LIST = "maid_model.json";

    /** Geometry and texture of one model. Either may be null when that file is missing. */
    public record Model(@Nullable byte[] geometry, @Nullable byte[] texture, @Nullable byte[] animation, float scale) {}

    /** What one entry of a pack's {@code maid_model.json} says, when it says anything at all. */
    private record Entry(@Nullable ResourceLocation model, @Nullable ResourceLocation texture,
                         List<ResourceLocation> animations, float scale) {}

    private static List<Path> folders;
    private static Map<String, ResourceLocation> packed;
    private static Map<String, Entry> entries;

    private TlmModelLibrary() {}

    /** Loads a model by id, or null when the geometry file cannot be found anywhere. */
    @Nullable
    public static Model load(String modelId) {
        ResourceLocation id = ResourceLocation.tryParse(modelId);
        if (id == null) {
            id = ResourceLocation.tryParse(YamiDelight.TOUHOU_LITTLE_MAID + ":" + modelId);
        }
        if (id == null) {
            return null;
        }
        // A model id is normally its own file name, but a pack may list a variant that reuses another
        // model's geometry ("hakurei_reimu_2" is "hakurei_reimu"), and only the pack's own list says so.
        Entry entry = entries().getOrDefault(modelId, new Entry(null, null, List.of(), 1F));
        ResourceLocation geometryId = entry.model() != null
                ? entry.model()
                : id.withPath(MODEL_DIR + id.getPath() + ".json");
        ResourceLocation textureId = entry.texture() != null
                ? entry.texture()
                : id.withPath(TEXTURE_DIR + id.getPath() + ".png");
        byte[] geometry = read(geometryId);
        if (geometry == null) {
            return null;
        }
        JsonObject merged = new JsonObject();
        for (ResourceLocation animationId : entry.animations()) {
            byte[] source = read(animationId);
            if (source == null) continue;
            try {
                var clips = JsonParser.parseString(new String(source, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animations");
                if (clips != null) for (var clip : clips.entrySet()) merged.add(clip.getKey(), clip.getValue());
            } catch (RuntimeException e) { YamiDelight.LOGGER.warn("yamidelight: invalid animation {}", animationId); }
        }
        JsonObject animation = new JsonObject(); animation.add("animations", merged);
        return new Model(geometry, read(textureId), animation.toString().getBytes(StandardCharsets.UTF_8), entry.scale());
    }

    public static synchronized void invalidate() {
        folders = null;
        packed = null;
        entries = null;
    }

    @Nullable
    private static byte[] read(ResourceLocation location) {
        String namespace = location.getNamespace();
        String relativePath = location.getPath();
        for (Path assets : folders()) {
            Path file = assets.resolve(namespace).resolve(relativePath);
            if (Files.isRegularFile(file)) {
                try {
                    return Files.readAllBytes(file);
                } catch (IOException e) {
                    YamiDelight.LOGGER.warn("yamidelight: could not read {}: {}", file, e.toString());
                }
            }
        }
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        ResourceLocation nested = packed().get(namespace + ":" + relativePath);
        if (nested != null) {
            byte[] bytes = bytes(manager.getResource(nested).orElse(null));
            if (bytes != null) {
                return bytes;
            }
        }
        return bytes(manager.getResource(ResourceLocation.fromNamespaceAndPath(namespace, relativePath))
                .orElse(null));
    }

    /**
     * Every model a pack lists, keyed by the id a maid carries.
     *
     * <p>Only the two optional fields are read: the geometry a variant borrows and the texture it wears.
     * Everything else in those files - names, descriptions, animations - belongs to Touhou Little Maid
     * and is none of this mod's business.
     */
    private static Map<String, Entry> entries() {
        if (entries == null) {
            Map<String, Entry> index = new HashMap<>();
            for (Path assets : folders()) {
                try (var namespaces = Files.list(assets)) {
                    for (Path namespace : namespaces.toList()) {
                        readModelList(index, namespace.resolve(MODEL_LIST), null);
                    }
                } catch (IOException e) {
                    warnCouldNotListPacks(e);
                }
            }
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            for (ResourceLocation location
                    : manager.listResources(PACK_DIR, path -> path.getPath().endsWith(MODEL_LIST)).keySet()) {
                readModelList(index, null, manager.getResource(location).orElse(null));
            }
            entries = index;
        }
        return entries;
    }

    private static void readModelList(Map<String, Entry> index, @Nullable Path file, @Nullable Resource resource) {
        byte[] bytes = null;
        try {
            if (file != null && Files.isRegularFile(file)) {
                bytes = Files.readAllBytes(file);
            } else if (resource != null) {
                bytes = bytes(resource);
            }
        } catch (IOException e) {
            YamiDelight.LOGGER.warn("yamidelight: could not read {}: {}", file, e.toString());
        }
        if (bytes == null) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement list = root.get("model_list");
            if (list == null || !list.isJsonArray()) {
                return;
            }
            JsonArray models = list.getAsJsonArray();
            for (JsonElement element : models) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject model = element.getAsJsonObject();
                String id = model.has("model_id") ? model.get("model_id").getAsString() : null;
                if (id == null || index.containsKey(id)) {
                    continue;
                }
                List<ResourceLocation> animations = new ArrayList<>();
                if (model.has("animation") && model.get("animation").isJsonArray()) {
                    for (var value : model.getAsJsonArray("animation")) {
                        ResourceLocation animation = location(value);
                        if (animation != null) animations.add(animation);
                    }
                }
                float scale = model.has("render_entity_scale") ? model.get("render_entity_scale").getAsFloat() : 1F;
                index.put(id, new Entry(location(model.get("model")), location(model.get("texture")), List.copyOf(animations), scale));
            }
        } catch (RuntimeException e) {
            YamiDelight.LOGGER.warn("yamidelight: could not parse a model list: {}", e.toString());
        }
    }

    @Nullable
    private static ResourceLocation location(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() ? ResourceLocation.tryParse(element.getAsString()) : null;
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

    /** The {@code assets} folder of every model pack on disk, at {@code <game>/tlm_custom_pack/<pack>}. */
    private static List<Path> folders() {
        if (folders == null) {
            List<Path> found = new ArrayList<>();
            Path root = FMLPaths.GAMEDIR.get().resolve(PACK_DIR);
            if (Files.isDirectory(root)) {
                try (var children = Files.list(root)) {
                    for (Path child : children.toList()) {
                        Path assets = child.resolve("assets");
                        if (Files.isDirectory(assets)) {
                            found.add(assets);
                        }
                    }
                } catch (IOException e) {
                    warnCouldNotListPacks(e);
                }
            }
            folders = List.copyOf(found);
        }
        return folders;
    }

    private static void warnCouldNotListPacks(IOException e) {
        YamiDelight.LOGGER.warn("yamidelight: could not list {} model packs: {}",
                FMLPaths.GAMEDIR.get().resolve(PACK_DIR), e.toString());
    }

    /**
     * Models shipped inside a jar, keyed the way they are named in a model id. The game keeps them under
     * {@code tlm_custom_pack/<pack>/assets/<namespace>/}, so the extra folder has to be peeled off
     * before the path looks like the one a resource pack would use.
     */
    private static Map<String, ResourceLocation> packed() {
        if (packed == null) {
            Map<String, ResourceLocation> index = new HashMap<>();
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            for (ResourceLocation location : manager.listResources(PACK_DIR, path -> true).keySet()) {
                String path = location.getPath();
                int marker = path.indexOf(ASSETS_MARKER);
                if (marker < 0) {
                    continue;
                }
                String rest = path.substring(marker + ASSETS_MARKER.length());
                int slash = rest.indexOf('/');
                if (slash < 0) {
                    continue;
                }
                index.put(rest.substring(0, slash) + ":" + rest.substring(slash + 1), location);
            }
            packed = index;
        }
        return packed;
    }
}
