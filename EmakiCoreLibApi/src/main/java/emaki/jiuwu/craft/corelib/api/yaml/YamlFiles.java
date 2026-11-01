package emaki.jiuwu.craft.corelib.api.yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import org.bukkit.plugin.java.JavaPlugin;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.libs.org.snakeyaml.engine.v2.common.FlowStyle;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.api.yaml.YamlLoadException;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

/**
 * Central YAML file utilities for plugin configuration: plain and versioned reads, bundled
 * resource bootstrap, version synchronization, missing-default merging and block-style writes.
 *
 * <p>All reads are UTF-8 and all writes go through {@link #saveText}, which creates missing
 * parent directories first. A one-shot startup handoff cache holds the documents opened during
 * bootstrap: {@code syncVersionedResource} publishes them and the consuming reads
 * ({@link #pollStartupCache}, {@link #load(File)}, {@link #loadCurrentResource}) remove them on
 * first access, so later reads see the already-opened document instead of re-reading disk.
 * Failures surface as {@link YamlLoadException} on reads and {@link IOException} on writes;
 * {@link #dump(Map)} is the only entry that never throws.
 */
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
            .setFlowStyle(FlowStyle.BLOCK)
            .build();
    private static final UpdaterSettings BOOSTED_UPDATER_SETTINGS = UpdaterSettings.builder()
            .setAutoSave(false)
            .setKeepAll(true)
            .setOptionSorting(UpdaterSettings.OptionSorting.SORT_BY_DEFAULTS)
            .build();
    private static final Map<Path, VersionedYamlFile> STARTUP_HANDOFF = new ConcurrentHashMap<>();

    private YamlFiles() {
    }

    /**
     * Takes the cached startup document for a file out of the handoff cache.
     *
     * <p>Consuming is destructive: a second call for the same file answers {@code null} again,
     * which is what gives post-bootstrap reads their read-disk-later semantics.
     *
     * @param target file whose cached document is wanted; {@code null} yields {@code null}
     * @return the cached document keyed by the normalized absolute path, or {@code null} when
     *         nothing was cached
     */
    public static VersionedYamlFile pollStartupCache(File target) {
        if (target == null) {
            return null;
        }
        return STARTUP_HANDOFF.remove(normalizeKey(target));
    }

    private static Path normalizeKey(File target) {
        return target.toPath().toAbsolutePath().normalize();
    }

    private static void rememberStartupCache(File target, VersionedYamlFile value) {
        if (target != null && value != null && value.root() != null) {
            STARTUP_HANDOFF.put(normalizeKey(target), value);
        }
    }

    /**
     * Reads a YAML file into a detached section.
     *
     * <p>The startup handoff cache is consulted first (and consumed when it hits), so a file
     * synced during bootstrap is not read from disk twice.
     *
     * @param file file to read; {@code null} or a missing file yields an empty section
     * @return the parsed content as a plain map-backed section; never {@code null}
     * @throws YamlLoadException if the file exists but cannot be read or parsed
     */
    public static YamlSection load(File file) {
        if (file == null || !file.exists()) {
            return new MapYamlSection();
        }
        VersionedYamlFile cached = pollStartupCache(file);
        if (cached != null && cached.root() != null) {
            return cached.root().copy();
        }
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            return BoostedYamlSupport.load(inputStream);
        } catch (YamlLoadException exception) {
            throw new YamlLoadException("加载 YAML 文件 '" + file.getPath() + "' 失败: " + safeMessage(exception), exception);
        } catch (Exception exception) {
            throw new YamlLoadException("读取 YAML 文件 '" + file.getPath() + "' 失败: " + safeMessage(exception), exception);
        }
    }

    /**
     * Reads YAML text from a stream.
     *
     * @param inputStream source to read; {@code null} yields an empty section
     * @return the parsed content as a plain map-backed section; never {@code null}
     * @throws YamlLoadException if the document cannot be parsed
     */
    public static YamlSection load(InputStream inputStream) {
        return BoostedYamlSupport.load(inputStream);
    }

    /**
     * Reads YAML text.
     *
     * @param payload document text; {@code null} yields an empty section
     * @return the parsed content as a plain map-backed section; never {@code null}
     * @throws YamlLoadException if the document cannot be parsed
     */
    public static YamlSection load(String payload) {
        return BoostedYamlSupport.load(payload);
    }

    /**
     * Reads a bundled YAML resource shipped inside the plugin jar.
     *
     * @param plugin plugin owning the resource; {@code null} yields an empty section
     * @param resourcePath jar-relative resource path; blank yields an empty section
     * @return the parsed content, or an empty section when the plugin or resource does not
     *         exist; never {@code null}
     * @throws YamlLoadException if the resource exists but cannot be read or parsed
     */
    public static YamlSection loadResource(JavaPlugin plugin, String resourcePath) {
        if (plugin == null || Texts.isBlank(resourcePath)) {
            return new MapYamlSection();
        }
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            if (inputStream == null) {
                return new MapYamlSection();
            }
            return load(inputStream);
        } catch (YamlLoadException exception) {
            throw new YamlLoadException("加载 YAML 资源 '" + resourcePath + "' 失败: " + safeMessage(exception), exception);
        } catch (Exception exception) {
            throw new YamlLoadException("读取 YAML 资源 '" + resourcePath + "' 失败: " + safeMessage(exception), exception);
        }
    }

    /**
     * Opens a bundled YAML resource as a versioned document without touching disk.
     *
     * <p>The returned view has no runtime file ({@link VersionedYamlFile#file()} is
     * {@code null}), so it must not be saved; use it to read the bundled version and defaults.
     *
     * @param plugin plugin owning the resource; {@code null} yields {@code null}
     * @param resourcePath jar-relative resource path; blank yields {@code null}
     * @return the opened document, or {@code null} when the plugin or resource does not exist
     * @throws YamlLoadException if the resource exists but cannot be parsed
     */
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
            throw new YamlLoadException("加载带版本 YAML 资源 '" + resourcePath + "' 失败: " + safeMessage(exception), exception);
        }
    }

    /**
     * Opens a versioned resource against its runtime file, bootstrapping the file from the jar
     * when it does not exist yet.
     *
     * <p>The startup handoff cache is consumed when it still holds the target, so a document
     * synced during bootstrap is not reopened. When the bundled resource itself is missing, the
     * target file is still created on disk by the loader.
     *
     * @param plugin plugin owning the bundled resource; may be {@code null} (then the method
     *               answers {@code null})
     * @param target runtime file to open or create; {@code null} yields {@code null}
     * @param resourcePath jar-relative resource path; blank yields {@code null}
     * @return the opened document with its bundled defaults attached, or {@code null} when an
     *         argument rules the open out
     * @throws IOException if the resource cannot be copied or the document cannot be opened
     */
    public static VersionedYamlFile loadCurrentResource(JavaPlugin plugin,
            File target,
            String resourcePath) throws IOException {
        if (target != null && !target.exists()) {
            copyResource(plugin, resourcePath, target, false);
            return openVersioned(plugin, target, resourcePath);
        }
        VersionedYamlFile cached = pollStartupCache(target);
        if (cached != null) {
            return cached;
        }
        return openVersioned(plugin, target, resourcePath);
    }

    /**
     * Synchronizes a versioned resource without a post-update hook.
     *
     * <p>Shorthand for the five-argument overload with no {@code afterUpdate} callback.
     *
     * @param plugin plugin owning the bundled resource
     * @param target runtime file to open or update
     * @param resourcePath jar-relative resource path
     * @param versionKey key holding the version in both documents
     * @return the opened (and possibly updated) document, or {@code null} when an argument rules
     *         the open out
     * @throws IOException if the file cannot be created, read or saved
     */
    public static VersionedYamlFile syncVersionedResource(JavaPlugin plugin,
            File target,
            String resourcePath,
            String versionKey) throws IOException {
        return syncVersionedResource(plugin, target, resourcePath, versionKey, null);
    }

    /**
     * Opens a versioned resource, merges missing bundled keys into the runtime file when its
     * stored version is older, and republishes the result to the startup handoff cache.
     *
     * <p>The comparison is numeric per dot- or dash-separated segment. A blank or older runtime
     * version triggers the BoostedYAML update (keep all keys, no auto-save), then
     * {@code afterUpdate} runs when given, then the version key is stamped with the bundled
     * version and the document is saved once; the returned view reports
     * {@link VersionedYamlFile#versionUpdated()} as {@code true}. An up-to-date file is
     * returned unchanged with the flag still {@code false}.
     *
     * @param plugin plugin owning the bundled resource; may be {@code null} (then the method
     *               answers {@code null})
     * @param target runtime file to open or create; {@code null} yields {@code null} and is
     *                never cached
     * @param resourcePath jar-relative resource path; blank yields {@code null}
     * @param versionKey key holding the version in both documents; blank skips any update
     * @param afterUpdate callback between the merge and the version stamp, before the save; may
     *                    be {@code null}
     * @return the opened (and possibly updated) document, or {@code null} when an argument rules
     *         the open out
     * @throws IOException if the file cannot be created, read or saved
     */
    public static VersionedYamlFile syncVersionedResource(JavaPlugin plugin,
            File target,
            String resourcePath,
            String versionKey,
            Consumer<VersionedYamlFile> afterUpdate) throws IOException {
        VersionedYamlFile versionedFile = doSyncVersionedResource(plugin, target, resourcePath,
                versionKey, afterUpdate);
        rememberStartupCache(target, versionedFile);
        return versionedFile;
    }

    private static VersionedYamlFile doSyncVersionedResource(JavaPlugin plugin,
            File target,
            String resourcePath,
            String versionKey,
            Consumer<VersionedYamlFile> afterUpdate) throws IOException {
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
        if (afterUpdate != null) {
            afterUpdate.accept(versionedFile);
        }
        versionedFile.root().set(versionKey, bundledVersion);
        versionedFile.save();
        return new VersionedYamlFile(
                versionedFile.file(),
                versionedFile.resourcePath(),
                versionedFile.document(),
                true,
                runtimeVersion,
                bundledVersion
        );
    }

    /**
     * Writes a section to a file as block-style YAML.
     *
     * @param file target file; created together with its parent directories
     * @param section content to write; {@code null} is rejected
     * @throws IOException if the text cannot be written
     * @throws NullPointerException if {@code file} or {@code section} is {@code null}
     */
    public static void save(File file, YamlSection section) throws IOException {
        Objects.requireNonNull(section, "section");
        save(file, section.asMap());
    }

    /**
     * Writes a map to a file as block-style YAML.
     *
     * @param file target file; created together with its parent directories
     * @param values content to write; {@code null} renders an empty document
     * @throws IOException if the text cannot be written
     * @throws NullPointerException if {@code file} is {@code null}
     */
    public static void save(File file, Map<String, ?> values) throws IOException {
        saveText(file, BoostedYamlSupport.dump(values));
    }

    /**
     * Writes raw text to a file in UTF-8, creating missing parent directories first.
     *
     * @param file target file; must not be {@code null}
     * @param content text to write; {@code null} is rejected
     * @throws IOException if the directory or the file cannot be written
     * @throws NullPointerException if {@code file} or {@code content} is {@code null}
     */
    public static void saveText(File file, String content) throws IOException {
        Objects.requireNonNull(file, "file");
        ensureDirectory(file.toPath().getParent());
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    /**
     * Renders a map as block-style YAML without touching disk.
     *
     * <p>Shorthand for {@link BoostedYamlSupport#dump(Map)}; an empty string is returned when
     * the document cannot be rendered.
     *
     * @param values values to render; {@code null} renders an empty document
     * @return the YAML text, possibly empty; never {@code null} and never throws
     */
    public static String dump(Map<String, ?> values) {
        return BoostedYamlSupport.dump(values);
    }

    /**
     * Copies a bundled resource only when the target file does not exist yet.
     *
     * @param plugin plugin owning the resource
     * @param resourcePath jar-relative resource path
     * @param target destination file; created together with its parent directories
     * @return {@code true} only when the resource was copied; an existing target, a missing
     *         resource or a {@code null} argument yields {@code false}
     * @throws IOException if the copy fails
     */
    public static boolean copyResourceIfMissing(JavaPlugin plugin, String resourcePath, File target) throws IOException {
        if (target != null && target.exists()) {
            return false;
        }
        return copyResource(plugin, resourcePath, target, false);
    }

    /**
     * Copies a bundled resource to a target file.
     *
     * @param plugin plugin owning the resource
     * @param resourcePath jar-relative resource path
     * @param target destination file; created together with its parent directories
     * @param overwrite whether an existing target may be replaced
     * @return {@code true} only when bytes were actually copied; {@code null} arguments, an
     *         existing target without {@code overwrite}, or a resource the jar does not ship
     *         yield {@code false}
     * @throws IOException if the copy fails
     */
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

    /**
     * Lists the YAML files shipped under a directory of the plugin jar.
     *
     * <p>The directory is resolved through the classpath first and falls back to the plugin's
     * code source, so the listing works both from an exploded directory and from a packaged
     * jar. Both path separators and surrounding slashes are accepted in
     * {@code resourceDirectory}, only {@code .yml}/{@code .yaml} files are reported, and the
     * result is sorted case-insensitively.
     *
     * @param plugin plugin owning the resources; {@code null} yields an empty list
     * @param resourceDirectory jar-relative directory; blank yields an empty list
     * @return an immutable, case-insensitively sorted list of jar-relative file paths; empty
     *         when nothing is found or any lookup step fails (this method never throws)
     */
    public static List<String> listResourcePaths(JavaPlugin plugin, String resourceDirectory) {
        if (plugin == null || Texts.isBlank(resourceDirectory)) {
            return List.of();
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
        } catch (Exception _) {
            return List.of();
        }
        ArrayList<String> ordered = new ArrayList<>(resourcePaths);
        ordered.sort(String::compareToIgnoreCase);
        return List.copyOf(ordered);
    }

    /**
     * Creates a directory and any missing parent directories.
     *
     * @param path directory to create; {@code null} yields {@code false} without touching disk
     * @return {@code true} when the directory exists after the call
     * @throws IOException if the directory cannot be created
     */
    public static boolean ensureDirectory(Path path) throws IOException {
        if (path == null) {
            return false;
        }
        Files.createDirectories(path);
        return true;
    }

    /**
     * Copies every default value whose key is missing from the runtime section.
     *
     * <p>The walk is recursive: nested default sections are traversed and each missing leaf is
     * written into {@code runtime} in place, creating intermediate sections as needed.
     *
     * @param runtime section to fill; mutated directly; {@code null} yields {@code 0}
     * @param defaults section supplying the missing values; {@code null} yields {@code 0}
     * @return the number of values copied
     */
    public static int mergeMissingValues(YamlSection runtime, YamlSection defaults) {
        if (runtime == null || defaults == null) {
            return 0;
        }
        return mergeMissingValues(runtime, defaults, "");
    }

    /**
     * Counts default keys that are missing from the runtime section, without changing it.
     *
     * <p>Same recursive walk as {@link #mergeMissingValues(YamlSection, YamlSection)}, but
     * read-only: nested sections are descended into and only missing leaves are counted.
     *
     * @param runtime section to inspect; {@code null} yields {@code 0}
     * @param defaults section supplying the reference keys; {@code null} yields {@code 0}
     * @return the number of missing leaf keys
     */
    public static int countMissingKeys(YamlSection runtime, YamlSection defaults) {
        if (runtime == null || defaults == null) {
            return 0;
        }
        return countMissingKeys(runtime, defaults, "");
    }

    /**
     * Merges the bundled defaults of a versioned resource into its runtime file when keys are
     * missing.
     *
     * <p>The file is opened through {@link #loadCurrentResource}; when the runtime root lacks
     * any default key, the BoostedYAML updater pulls the missing entries in and the document is
     * saved once. An already-complete file (or one that cannot be opened together with its
     * defaults) is left untouched.
     *
     * @param plugin plugin owning the bundled resource
     * @param target runtime file
     * @param resourcePath jar-relative resource path
     * @return the number of missing keys detected before the merge; {@code 0} when nothing was
     *         missing or the file could not be opened with defaults
     * @throws IOException if the file cannot be opened or saved
     */
    public static int mergeMissingKeys(JavaPlugin plugin, File target, String resourcePath) throws IOException {
        VersionedYamlFile versionedFile = loadCurrentResource(plugin, target, resourcePath);
        if (versionedFile == null || versionedFile.root() == null || versionedFile.defaults() == null) {
            return 0;
        }
        int missing = countMissingKeys(versionedFile.root(), versionedFile.defaults());
        if (missing == 0) {
            return 0;
        }
        versionedFile.document().update(BOOSTED_UPDATER_SETTINGS);
        versionedFile.save();
        return missing;
    }

    private static int countMissingKeys(YamlSection runtime, YamlSection defaults, String parentPath) {
        int missing = 0;
        for (String key : defaults.getKeys(false)) {
            String fullPath = parentPath == null || parentPath.isBlank() ? key : parentPath + "." + key;
            YamlSection nested = defaults.getSection(key);
            if (nested != null) {
                missing += countMissingKeys(runtime, nested, fullPath);
                continue;
            }
            if (!runtime.contains(fullPath)) {
                missing++;
            }
        }
        return missing;
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
        if (Texts.isBlank(latest)) {
            return 0;
        }
        if (current.equals(latest)) {
            return 0;
        }
        String[] currentParts = current.split("[.-]");
        String[] latestParts = latest.split("[.-]");
        int length = Math.max(currentParts.length, latestParts.length);
        for (int index = 0; index < length; index++) {
            String currentPart = index < currentParts.length ? currentParts[index] : "0";
            String latestPart = index < latestParts.length ? latestParts[index] : "0";
            int comparison = compareVersionPart(currentPart, latestPart);
            if (comparison != 0) {
                return comparison;
            }
        }
        return current.compareToIgnoreCase(latest);
    }

    private static int compareVersionPart(String current, String latest) {
        try {
            return Integer.compare(Integer.parseInt(current), Integer.parseInt(latest));
        } catch (NumberFormatException ignored) {
            return Texts.toStringSafe(current).compareToIgnoreCase(Texts.toStringSafe(latest));
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return throwable == null ? "未知错误" : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }

    private static void scanResourceLocation(URL location, String normalizedDirectory, LinkedHashSet<String> resourcePaths) throws Exception {
        if (location == null || resourcePaths == null || Texts.isBlank(normalizedDirectory)) {
            return;
        }
        String protocol = Texts.toStringSafe(location.getProtocol()).trim().toLowerCase(Locale.ROOT);
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
        String protocol = Texts.toStringSafe(location.getProtocol()).trim().toLowerCase(Locale.ROOT);
        if ("file".equals(protocol)) {
            Path root = Path.of(location.toURI());
            if (Files.isDirectory(root)) {
                scanFileTree(root.resolve(normalizedDirectory), normalizedDirectory, resourcePaths);
                return;
            }
            if (root.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
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
        String lower = path.trim().toLowerCase(Locale.ROOT);
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
