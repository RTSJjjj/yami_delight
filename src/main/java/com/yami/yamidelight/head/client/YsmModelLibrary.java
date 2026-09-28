package com.yami.yamidelight.head.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yami.yamidelight.YamiDelight;
import com.yami.yamidelight.head.MaidHeadData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;

/**
 * Finds the Yes Steve Model model folder a head was taken from.
 *
 * <p>YSM keeps its models in three places and this looks in all of them: the {@code custom} folder where
 * players drop their own models, the {@code builtin} folder YSM unpacks its own models into, and the
 * {@code assets/yes_steve_model/builtin} tree of every mod jar and resource pack. That last one is why
 * this asks the resource manager instead of walking the game directory: it sees the same list the game
 * does, including models a pack ships.
 *
 * <p>A model is identified the way YSM identifies it in its own GUI - by the model folder's name, which
 * YSM itself restricts to lower case letters, digits and underscores and refuses to duplicate. The head
 * also carries the display name whose tooltip YSM shows, which is used as a second key so a pack that
 * renamed the folder still matches.
 */
public final class YsmModelLibrary {
    private static final String NAMESPACE = "yes_steve_model";
    private static final String CONFIG_DIR = "config/yes_steve_model";
    private static final String INFO_FILE = "ysm.json";
    private static final String DEFAULT_MODEL_FILE = "models/main.json";
    private static final String DEFAULT_ANIMATION_FILE = "animations/main.animation.json";
    private static final String DEFAULT_TEXTURE_FILE = "textures/default.png";

    private static List<Entry> entries;

    private YsmModelLibrary() {}

    /**
     * One model folder YSM would list. {@code path} is the folder's path relative to the model root and
     * is what an id that already carries a pack prefix is matched against.
     */
    public record Entry(String id, String path, String name, String defaultTexture, float heightScale,
                        String modelFile, String animationFile, List<String> textures, Source source) {
        /** The raw {@code models/main.json} of this model, or null when the source is unreadable. */
        public byte[] modelJson() {
            return source.read(modelFile);
        }

        /** The model's always running animations, which is where the default bone scales live. */
        @Nullable
        public byte[] animationJson() {
            return animationFile.isEmpty() ? null : source.read(animationFile);
        }

        /**
         * The texture the head asked for, or the model's own default when that texture is gone. Returns
         * null only when the model has no readable texture at all, which makes the head fall back.
         */
        @Nullable
        public byte[] texture(String wanted) {
            for (String candidate : textureCandidates(wanted)) {
                byte[] bytes = source.read(candidate);
                if (bytes != null) {
                    return bytes;
                }
            }
            return null;
        }

        private List<String> textureCandidates(String wanted) {
            Set<String> candidates = new LinkedHashSet<>();
            String name = wanted == null ? "" : wanted.trim();
            if (!name.isEmpty()) {
                if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) {
                    name = name + ".png";
                }
                candidates.add(name.contains("/") ? name : "textures/" + name);
                for (String listed : textures) {
                    if (stem(listed).equalsIgnoreCase(stem(wanted))) {
                        candidates.add(listed);
                    }
                }
            }
            if (!defaultTexture.isBlank()) {
                candidates.add("textures/" + defaultTexture + ".png");
            }
            candidates.addAll(textures);
            candidates.add(DEFAULT_TEXTURE_FILE);
            return List.copyOf(candidates);
        }

        private static String stem(String file) {
            String name = file;
            int slash = name.lastIndexOf('/');
            if (slash >= 0) {
                name = name.substring(slash + 1);
            }
            int dot = name.lastIndexOf('.');
            return dot < 0 ? name : name.substring(0, dot);
        }

        public String describe() {
            return id + " (" + source.describe() + ")";
        }
    }

    /** Where a model's files are read from: a folder on disk, a zip, or the resource manager. */
    public interface Source {
        @Nullable
        byte[] read(String relativePath);

        String describe();
    }

    @Nullable
    public static Entry find(MaidHeadData data) {
        List<Entry> all = entries();
        String id = data.modelId().trim();
        if (id.isEmpty()) {
            return null;
        }
        for (Entry entry : all) {
            if (entry.id().equalsIgnoreCase(id)) {
                return entry;
            }
        }
        for (Entry entry : all) {
            if (entry.path().equalsIgnoreCase(id) || entry.path().endsWith("/" + id)) {
                return entry;
            }
        }
        String name = data.modelName().trim();
        if (!name.isEmpty()) {
            for (Entry entry : all) {
                if (entry.name().equalsIgnoreCase(name)) {
                    return entry;
                }
            }
        }
        // Last resort: the id carried a pack prefix this install does not use ("builtin/misc/1_alex").
        for (Entry entry : all) {
            if (id.toLowerCase(Locale.ROOT).endsWith("/" + entry.path().toLowerCase(Locale.ROOT))) {
                return entry;
            }
        }
        return null;
    }

    /** Everything this client could not match, as a one line explanation per search root. */
    public static List<String> searchRoots() {
        Path root = FMLPaths.GAMEDIR.get().resolve(CONFIG_DIR);
        List<String> roots = new ArrayList<>();
        roots.add(root.resolve("custom").toString());
        roots.add(root.resolve("builtin").toString());
        roots.add("assets/" + NAMESPACE + "/builtin (mod jars and resource packs)");
        return roots;
    }

    public static synchronized void invalidate() {
        entries = null;
    }

    private static synchronized List<Entry> entries() {
        if (entries == null) {
            entries = scan();
            YamiDelight.LOGGER.info("yamidelight: {} YSM model folders are available", entries.size());
        }
        return entries;
    }

    private static List<Entry> scan() {
        List<Entry> found = new ArrayList<>();
        Path root = FMLPaths.GAMEDIR.get().resolve(CONFIG_DIR);
        // Custom models are added first so a folder the player wrote wins over a built in of the same id.
        scanFolder(found, root.resolve("custom"), "custom", 0);
        scanFolder(found, root.resolve("builtin"), "builtin", 0);
        scanResources(found);
        return List.copyOf(found);
    }

    private static void scanFolder(List<Entry> found, Path dir, String prefix, int depth) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var children = Files.list(dir)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                if (Files.isDirectory(child)) {
                    if (Files.isRegularFile(child.resolve(INFO_FILE))) {
                        add(found, new FolderSource(child), name, prefix + "/" + name);
                    } else if (depth < 2) {
                        // A pack folder ("custom/<pack>/<model>") is one level deeper.
                        scanFolder(found, child, prefix + "/" + name, depth + 1);
                    }
                } else if (isArchive(name)) {
                    scanArchive(found, child, prefix + "/" + name);
                }
            }
        } catch (IOException e) {
            YamiDelight.LOGGER.warn("yamidelight: could not read {}: {}", dir, e.toString());
        }
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".zip") || lower.endsWith(".ysm");
    }

    private static void scanArchive(List<Entry> found, Path archive, String path) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            String prefix = archivePrefix(zip);
            if (prefix == null) {
                return;
            }
            String name = archive.getFileName().toString();
            int dot = name.lastIndexOf('.');
            add(found, new ZipSource(archive, prefix), dot < 0 ? name : name.substring(0, dot), path);
        } catch (IOException e) {
            // .ysm files are encrypted on purpose, so this is a normal outcome rather than a failure.
            YamiDelight.LOGGER.info("yamidelight: {} is not a readable zip ({}), its heads will fall back",
                    archive.getFileName(), e.getMessage());
        }
    }

    /** The folder inside the archive that holds {@code ysm.json}: the root, or a single wrapping folder. */
    @Nullable
    private static String archivePrefix(ZipFile zip) {
        if (zip.getEntry(INFO_FILE) != null) {
            return "";
        }
        for (var entries = zip.entries(); entries.hasMoreElements(); ) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (entry.isDirectory() || !name.endsWith("/" + INFO_FILE)) {
                continue;
            }
            String prefix = name.substring(0, name.length() - INFO_FILE.length());
            if (prefix.indexOf('/', 0) == prefix.length() - 1) {
                return prefix;
            }
        }
        return null;
    }

    private static void scanResources(List<Entry> found) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        Map<ResourceLocation, Resource> infos =
                manager.listResources("builtin", location -> location.getPath().endsWith("/" + INFO_FILE));
        for (ResourceLocation location : infos.keySet()) {
            if (!location.getNamespace().equals(NAMESPACE)) {
                continue;
            }
            String file = location.getPath();
            String folder = file.substring(0, file.length() - INFO_FILE.length());
            String[] parts = folder.split("/");
            if (parts.length < 3) {
                continue;
            }
            String id = parts[parts.length - 1];
            add(found, new ResourceSource(manager, folder), id, folder.substring(0, folder.length() - 1));
        }
    }

    /**
     * Reads the model's own info file and turns it into an entry. A model without a readable info file is
     * skipped instead of guessed at, because both the texture and the model file path come from it.
     */
    private static void add(List<Entry> found, Source source, String id, String path) {
        byte[] info = source.read(INFO_FILE);
        if (info == null) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(new String(info, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject metadata = object(root, "metadata");
            JsonObject properties = object(root, "properties");
            JsonObject files = object(root, "files");
            JsonObject player = files == null ? null : object(files, "player");
            JsonObject model = player == null ? null : object(player, "model");
            String modelFile = model == null ? "" : string(model, "main");
            JsonObject animation = player == null ? null : object(player, "animation");
            String animationFile = animation == null ? "" : string(animation, "main");
            List<String> textures = new ArrayList<>();
            if (player != null && player.has("texture") && player.get("texture").isJsonArray()) {
                JsonArray array = player.getAsJsonArray("texture");
                for (JsonElement element : array) {
                    if (element.isJsonPrimitive()) {
                        textures.add(element.getAsString().replace('\\', '/'));
                    }
                }
            }
            String name = metadata == null ? "" : string(metadata, "name");
            String defaultTexture = properties == null ? "" : string(properties, "default_texture");
            float heightScale = 1.0F;
            if (properties != null && properties.has("height_scale")) {
                heightScale = properties.get("height_scale").getAsFloat();
            }
            found.add(new Entry(id, path, name, defaultTexture, heightScale,
                    modelFile.isBlank() ? DEFAULT_MODEL_FILE : modelFile,
                    animationFile.isBlank() ? DEFAULT_ANIMATION_FILE : animationFile,
                    List.copyOf(textures), source));
        } catch (RuntimeException e) {
            YamiDelight.LOGGER.warn("yamidelight: could not read {}/{}: {}", path, INFO_FILE, e.toString());
        }
    }

    @Nullable
    private static JsonObject object(JsonObject parent, String member) {
        JsonElement element = parent.get(member);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String string(JsonObject parent, String member) {
        JsonElement element = parent.get(member);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    /** A model folder in the game directory. */
    private record FolderSource(Path folder) implements Source {
        @Override
        public byte[] read(String relativePath) {
            Path file = folder.resolve(relativePath).normalize();
            if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
                return null;
            }
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public String describe() {
            return folder.toString();
        }
    }

    /** A model inside a .zip or an unencrypted .ysm archive. */
    private record ZipSource(Path archive, String prefix) implements Source {
        @Override
        public byte[] read(String relativePath) {
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                ZipEntry entry = zip.getEntry(prefix + relativePath);
                if (entry == null || entry.isDirectory()) {
                    return null;
                }
                try (InputStream stream = zip.getInputStream(entry)) {
                    return stream.readAllBytes();
                }
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public String describe() {
            return archive.getFileName() + (prefix.isEmpty() ? "" : "!" + prefix);
        }
    }

    /** A model shipped by a mod jar or a resource pack, reached through the resource manager. */
    private record ResourceSource(ResourceManager manager, String base) implements Source {
        @Override
        public byte[] read(String relativePath) {
            ResourceLocation location =
                    ResourceLocation.fromNamespaceAndPath(NAMESPACE, base + relativePath);
            return manager.getResource(location).map(resource -> {
                try (InputStream stream = resource.open()) {
                    return stream.readAllBytes();
                } catch (IOException e) {
                    return null;
                }
            }).orElse(null);
        }

        @Override
        public String describe() {
            return "assets/" + NAMESPACE + "/" + base;
        }
    }
}
