package emaki.jiuwu.craft.corelib.yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class YamlFiles {

    private static final GeneralSettings BOOSTED_GENERAL_SETTINGS = GeneralSettings.builder()
            .setUseDefaults(false)
            .build();
    private static final LoaderSettings BOOSTED_LOADER_SETTINGS = LoaderSettings.builder()
            .setCreateFileIfAbsent(true)
            .setAutoUpdate(false)
            .build();
    private static final DumperSettings BOOSTED_DUMPER_SETTINGS = DumperSettings.builder()
            .setIndentation(2)
            .build();
    private static final UpdaterSettings BOOSTED_UPDATER_SETTINGS = UpdaterSettings.builder()
            .setAutoSave(false)
            .setKeepAll(true)
            .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS)
            .build();

    private YamlFiles() {
    }

    public static YamlSection load(File file) {
        if (file == null || !file.exists()) {
            return new MapYamlSection();
        }
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            return SnakeYamlSupport.load(inputStream);
        } catch (Exception exception) {
            return new MapYamlSection();
        }
    }

    public static YamlSection load(InputStream inputStream) {
        return SnakeYamlSupport.load(inputStream);
    }

    public static YamlSection load(String payload) {
        return SnakeYamlSupport.load(payload);
    }

    public static YamlSection loadResource(JavaPlugin plugin, String resourcePath) {
        if (plugin == null || Texts.isBlank(resourcePath)) {
            return new MapYamlSection();
        }
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            return load(inputStream);
        } catch (Exception exception) {
            return new MapYamlSection();
        }
    }

    public static VersionedYamlFile loadVersionedResource(JavaPlugin plugin, String resourcePath) {
        if (plugin == null || Texts.isBlank(resourcePath)) {
            return null;
        }
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return null;
            }
            YamlDocument document = YamlDocument.create(
                    inputStream,
                    BOOSTED_GENERAL_SETTINGS,
                    BOOSTED_LOADER_SETTINGS,
                    BOOSTED_DUMPER_SETTINGS,
                    BOOSTED_UPDATER_SETTINGS
            );
            return new VersionedYamlFile(null, resourcePath, document);
        } catch (Exception exception) {
            return null;
        }
    }

    public static VersionedYamlFile syncVersionedResource(JavaPlugin plugin,
            File target,
            String resourcePath,
            String versionKey) throws IOException {
        return syncVersionedResource(plugin, target, resourcePath, versionKey, null);
    }

    public static VersionedYamlFile syncVersionedResource(JavaPlugin plugin,
            File target,
            String resourcePath,
            String versionKey,
            Consumer<VersionedYamlFile> migration) throws IOException {
        VersionedYamlFile versionedFile = openVersioned(plugin, target, resourcePath);
        if (versionedFile == null || Texts.isBlank(versionKey)) {
            return versionedFile;
        }
        String bundledVersion = Texts.toStringSafe(versionedFile.bundledVersion(versionKey)).trim();
        if (bundledVersion.isBlank()) {
            return versionedFile;
        }
        String runtimeVersion = Texts.toStringSafe(versionedFile.version(versionKey)).trim();
        if (!runtimeVersion.isBlank() && compareVersions(runtimeVersion, bundledVersion) >= 0) {
            return versionedFile;
        }
        versionedFile.document().update(BOOSTED_UPDATER_SETTINGS);
        if (migration != null) {
            migration.accept(versionedFile);
        }
        versionedFile.root().set(versionKey, bundledVersion);
        versionedFile.save();
        return versionedFile;
    }

    public static void save(File file, YamlSection section) throws IOException {
        Objects.requireNonNull(section, "section");
        save(file, section.asMap());
    }

    public static void save(File file, Map<String, ?> values) throws IOException {
        Objects.requireNonNull(file, "file");
        ensureDirectory(file.toPath().getParent());
        Path target = file.toPath();
        Path parent = target.getParent();
        Path tempDirectory = parent == null ? Path.of(".") : parent;
        Path temp = Files.createTempFile(tempDirectory, target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temp, SnakeYamlSupport.dump(values), StandardCharsets.UTF_8);
            moveReplacing(temp, target);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    public static String dump(Map<String, ?> values) {
        return SnakeYamlSupport.dump(values);
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

    public static int mergeMissingValues(YamlSection runtime, YamlSection defaults) {
        if (runtime == null || defaults == null) {
            return 0;
        }
        return mergeMissingValues(runtime, defaults, "");
    }

    private static VersionedYamlFile openVersioned(JavaPlugin plugin, File target, String resourcePath) throws IOException {
        if (plugin == null || target == null || Texts.isBlank(resourcePath)) {
            return null;
        }
        ensureDirectory(target.toPath().getParent());
        try (InputStream defaults = plugin.getResource(resourcePath)) {
            YamlDocument document = defaults == null
                    ? YamlDocument.create(
                            target,
                            BOOSTED_GENERAL_SETTINGS,
                            BOOSTED_LOADER_SETTINGS,
                            BOOSTED_DUMPER_SETTINGS,
                            BOOSTED_UPDATER_SETTINGS
                    )
                    : YamlDocument.create(
                            target,
                            defaults,
                            BOOSTED_GENERAL_SETTINGS,
                            BOOSTED_LOADER_SETTINGS,
                            BOOSTED_DUMPER_SETTINGS,
                            BOOSTED_UPDATER_SETTINGS
                    );
            return new VersionedYamlFile(target, resourcePath, document);
        }
    }

    private static int mergeMissingValues(YamlSection runtime, YamlSection defaults, String parentPath) {
        int merged = 0;
        for (String key : defaults.getKeys(false)) {
            String fullPath = parentPath == null || parentPath.isBlank() ? key : parentPath + "." + key;
            YamlSection nested = defaults.getSection(key);
            if (nested != null) {
                merged += mergeMissingValues(runtime, nested, fullPath);
                continue;
            }
            if (!runtime.contains(fullPath)) {
                runtime.set(fullPath, defaults.get(key));
                merged++;
            }
        }
        return merged;
    }

    private static int compareVersions(String current, String latest) {
        if (Texts.isBlank(current)) {
            return -1;
        }
        try {
            String[] currentParts = current.split("\\.");
            String[] latestParts = latest.split("\\.");
            int length = Math.max(currentParts.length, latestParts.length);
            for (int index = 0; index < length; index++) {
                int currentValue = index < currentParts.length ? Integer.parseInt(currentParts[index]) : 0;
                int latestValue = index < latestParts.length ? Integer.parseInt(latestParts[index]) : 0;
                if (currentValue != latestValue) {
                    return Integer.compare(currentValue, latestValue);
                }
            }
        } catch (Exception ignored) {
            return 0;
        }
        return 0;
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
