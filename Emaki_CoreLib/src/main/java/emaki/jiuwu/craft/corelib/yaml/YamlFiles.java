package emaki.jiuwu.craft.corelib.yaml;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class YamlFiles {

    private YamlFiles() {
    }

    public static YamlConfiguration load(File file) {
        if (file == null) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public static YamlConfiguration load(InputStream inputStream) {
        if (inputStream == null) {
            return new YamlConfiguration();
        }
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            return new YamlConfiguration();
        }
    }

    public static YamlConfiguration loadResource(JavaPlugin plugin, String resourcePath) {
        if (plugin == null || resourcePath == null) {
            return null;
        }
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            return load(inputStream);
        } catch (Exception exception) {
            return null;
        }
    }

    public static void save(File file, YamlConfiguration configuration) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(configuration, "configuration");
        ensureDirectory(file.toPath().getParent());
        Path target = file.toPath();
        Path parent = target.getParent();
        Path tempDirectory = parent == null ? Path.of(".") : parent;
        Path temp = Files.createTempFile(tempDirectory, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            configuration.save(temp.toFile());
            moveReplacing(temp, target);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    public static void save(File file, Map<String, ?> values) throws IOException {
        YamlConfiguration configuration = new YamlConfiguration();
        write(configuration, values);
        save(file, configuration);
    }

    public static void write(YamlConfiguration configuration, Map<String, ?> values) {
        if (configuration == null || values == null) {
            return;
        }
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            configuration.set(entry.getKey(), ConfigNodes.toPlainData(entry.getValue()));
        }
    }

    public static boolean copyResourceIfMissing(JavaPlugin plugin, String resourcePath, File target) throws IOException {
        if (target != null && target.exists()) {
            return false;
        }
        return copyResource(plugin, resourcePath, target, false);
    }

    public static boolean copyResource(JavaPlugin plugin, String resourcePath, File target, boolean overwrite) throws IOException {
        if (plugin == null || resourcePath == null || target == null) {
            return false;
        }
        if (target.exists() && !overwrite) {
            return false;
        }
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return false;
            }
            ensureDirectory(target.toPath().getParent());
            if (overwrite) {
                Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(inputStream, target.toPath());
            }
            return true;
        }
    }

    public static java.util.List<String> listResourcePaths(JavaPlugin plugin, String resourceDirectory) {
        if (plugin == null || Texts.isBlank(resourceDirectory)) {
            return java.util.List.of();
        }
        String normalizedDirectory = normalizeResourceDirectory(resourceDirectory);
        LinkedHashSet<String> resourcePaths = new LinkedHashSet<>();
        try {
            URL rootUrl = plugin.getClass().getResource("/" + normalizedDirectory);
            if (rootUrl != null) {
                scanResourceLocation(rootUrl, normalizedDirectory, resourcePaths);
            }
            if (resourcePaths.isEmpty()) {
                URL codeSourceUrl = plugin.getClass().getProtectionDomain().getCodeSource() == null
                    ? null
                    : plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
                if (codeSourceUrl != null) {
                    scanCodeSourceLocation(codeSourceUrl, normalizedDirectory, resourcePaths);
                }
            }
        } catch (Exception ignored) {
            return java.util.List.of();
        }
        ArrayList<String> ordered = new ArrayList<>(resourcePaths);
        ordered.sort(String::compareToIgnoreCase);
        return java.util.List.copyOf(ordered);
    }

    public static boolean ensureDirectory(Path path) throws IOException {
        if (path == null) {
            return false;
        }
        Files.createDirectories(path);
        return true;
    }

    public static int mergeMissingValues(YamlConfiguration runtime, ConfigurationSection defaults) {
        if (runtime == null || defaults == null) {
            return 0;
        }
        return mergeMissingValues(runtime, defaults, "");
    }

    public static int syncVersionedResource(JavaPlugin plugin,
                                            File target,
                                            String resourcePath,
                                            String versionKey) throws IOException {
        if (plugin == null || target == null || Texts.isBlank(resourcePath) || Texts.isBlank(versionKey)) {
            return 0;
        }
        YamlConfiguration bundled = loadResource(plugin, resourcePath);
        if (bundled == null) {
            return 0;
        }
        if (!target.exists()) {
            copyResourceIfMissing(plugin, resourcePath, target);
            return 0;
        }
        String bundledVersion = Texts.toStringSafe(bundled.get(versionKey)).trim();
        if (bundledVersion.isBlank()) {
            return 0;
        }
        YamlConfiguration runtime = load(target);
        String runtimeVersion = Texts.toStringSafe(runtime.get(versionKey)).trim();
        if (bundledVersion.equals(runtimeVersion)) {
            return 0;
        }
        int merged = mergeMissingValues(runtime, bundled);
        runtime.set(versionKey, bundled.get(versionKey));
        save(target, runtime);
        return merged;
    }

    private static int mergeMissingValues(YamlConfiguration runtime, ConfigurationSection defaults, String parentPath) {
        int merged = 0;
        for (String key : defaults.getKeys(false)) {
            String fullPath = parentPath == null || parentPath.isBlank() ? key : parentPath + "." + key;
            if (defaults.isConfigurationSection(key)) {
                ConfigurationSection nested = defaults.getConfigurationSection(key);
                if (nested != null) {
                    merged += mergeMissingValues(runtime, nested, fullPath);
                }
                continue;
            }
            if (!runtime.contains(fullPath) && !runtime.getValues(false).containsKey(fullPath)) {
                runtime.set(fullPath, ConfigNodes.toPlainData(defaults.get(key)));
                merged++;
            }
        }
        return merged;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void scanResourceLocation(URL location, String normalizedDirectory, LinkedHashSet<String> resourcePaths) throws Exception {
        if (location == null || resourcePaths == null || Texts.isBlank(normalizedDirectory)) {
            return;
        }
        String protocol = Texts.toStringSafe(location.getProtocol()).trim().toLowerCase();
        if ("file".equals(protocol)) {
            Path root = Path.of(location.toURI());
            scanFileTree(root, normalizedDirectory, resourcePaths);
            return;
        }
        if ("jar".equals(protocol)) {
            scanJarLocation(location, normalizedDirectory, resourcePaths);
        }
    }

    private static void scanCodeSourceLocation(URL location, String normalizedDirectory, LinkedHashSet<String> resourcePaths) throws Exception {
        if (location == null || resourcePaths == null || Texts.isBlank(normalizedDirectory)) {
            return;
        }
        String protocol = Texts.toStringSafe(location.getProtocol()).trim().toLowerCase();
        if ("file".equals(protocol)) {
            Path root = Path.of(location.toURI());
            if (Files.isDirectory(root)) {
                scanFileTree(root.resolve(normalizedDirectory), normalizedDirectory, resourcePaths);
                return;
            }
            if (root.toString().toLowerCase().endsWith(".jar")) {
                scanJarFile(root, normalizedDirectory, resourcePaths);
            }
            return;
        }
        if ("jar".equals(protocol)) {
            scanJarLocation(location, normalizedDirectory, resourcePaths);
        }
    }

    private static void scanFileTree(Path resourceRoot, String normalizedDirectory, LinkedHashSet<String> resourcePaths) throws IOException {
        if (resourceRoot == null || resourcePaths == null || !Files.exists(resourceRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(resourceRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                String relative = resourceRoot.relativize(path).toString().replace(File.separatorChar, '/');
                if (isYamlResource(relative)) {
                    resourcePaths.add(normalizedDirectory + "/" + relative);
                }
            });
        }
    }

    private static void scanJarLocation(URL location, String normalizedDirectory, LinkedHashSet<String> resourcePaths) throws Exception {
        if (location == null || resourcePaths == null || Texts.isBlank(normalizedDirectory)) {
            return;
        }
        JarURLConnection connection = (JarURLConnection) location.openConnection();
        try (JarFile jarFile = connection.getJarFile()) {
            scanJarEntries(jarFile, normalizedDirectory, resourcePaths);
        }
    }

    private static void scanJarFile(Path jarPath, String normalizedDirectory, LinkedHashSet<String> resourcePaths) throws IOException {
        if (jarPath == null || resourcePaths == null || Texts.isBlank(normalizedDirectory) || !Files.exists(jarPath)) {
            return;
        }
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            scanJarEntries(jarFile, normalizedDirectory, resourcePaths);
        }
    }

    private static void scanJarEntries(JarFile jarFile, String normalizedDirectory, LinkedHashSet<String> resourcePaths) {
        if (jarFile == null || resourcePaths == null || Texts.isBlank(normalizedDirectory)) {
            return;
        }
        String prefix = normalizedDirectory.endsWith("/") ? normalizedDirectory : normalizedDirectory + "/";
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry == null || entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (Texts.isBlank(name) || !name.startsWith(prefix)) {
                continue;
            }
            String relative = name.substring(prefix.length());
            if (isYamlResource(relative)) {
                resourcePaths.add(name);
            }
        }
    }

    private static boolean isYamlResource(String path) {
        if (Texts.isBlank(path)) {
            return false;
        }
        String lower = path.trim().toLowerCase();
        return lower.endsWith(".yml") || lower.endsWith(".yaml");
    }

    private static String normalizeResourceDirectory(String resourceDirectory) {
        String normalized = Texts.toStringSafe(resourceDirectory).trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
