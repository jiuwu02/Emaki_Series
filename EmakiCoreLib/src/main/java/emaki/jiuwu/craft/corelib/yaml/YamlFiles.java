package emaki.jiuwu.craft.corelib.yaml;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import dev.dejvokep.boostedyaml.settings.updater.UpdaterSettings;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.MapYamlSection;
import emaki.jiuwu.craft.corelib.api.yaml.VersionedYamlFile;
import emaki.jiuwu.craft.corelib.api.yaml.YamlLoadException;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
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

/**
 * 已搬迁到 {@link emaki.jiuwu.craft.corelib.api.yaml.YamlFiles}，本类仅作过渡转发。
 *
 * <p>M2-2 路线 A：CoreLib 的通用工具与契约类型改由 {@code emaki-corelib-api}
 * 提供。此处保留全部 16 个 public static 方法签名并逐一委托，
 * 旧调用点行为完全不变。
 *
 * @deprecated 改用 {@link emaki.jiuwu.craft.corelib.api.yaml.YamlFiles}。
 *         保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
 */
@Deprecated(since = "4.6.19", forRemoval = true)
public final class YamlFiles {

    private YamlFiles() {
    }

    public static YamlSection load(File file) {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(file);
    }

    public static YamlSection load(InputStream inputStream) {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(inputStream);
    }

    public static YamlSection load(String payload) {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.load(payload);
    }

    public static YamlSection loadResource(JavaPlugin plugin, String resourcePath) {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.loadResource(plugin, resourcePath);
    }

    public static VersionedYamlFile loadVersionedResource(JavaPlugin plugin, String resourcePath) {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.loadVersionedResource(plugin, resourcePath);
    }

    public static VersionedYamlFile loadCurrentResource(JavaPlugin plugin,
            File target,
            String resourcePath) throws IOException {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.loadCurrentResource(plugin, target, resourcePath);
    }

    public static VersionedYamlFile syncVersionedResource(JavaPlugin plugin,
            File target,
            String resourcePath,
            String versionKey) throws IOException {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.syncVersionedResource(plugin, target, resourcePath, versionKey);
    }

    public static VersionedYamlFile syncVersionedResource(JavaPlugin plugin,
            File target,
            String resourcePath,
            String versionKey,
            Consumer<VersionedYamlFile> afterUpdate) throws IOException {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.syncVersionedResource(plugin, target, resourcePath, versionKey, afterUpdate);
    }

    public static void save(File file, YamlSection section) throws IOException {
        emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.save(file, section);
    }

    public static void save(File file, Map<String, ?> values) throws IOException {
        emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.save(file, values);
    }

    public static String dump(Map<String, ?> values) {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.dump(values);
    }

    public static boolean copyResourceIfMissing(JavaPlugin plugin, String resourcePath, File target) throws IOException {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.copyResourceIfMissing(plugin, resourcePath, target);
    }

    public static boolean copyResource(JavaPlugin plugin, String resourcePath, File target, boolean overwrite) throws IOException {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.copyResource(plugin, resourcePath, target, overwrite);
    }

    public static java.util.List<String> listResourcePaths(JavaPlugin plugin, String resourceDirectory) {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.listResourcePaths(plugin, resourceDirectory);
    }

    public static boolean ensureDirectory(Path path) throws IOException {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.ensureDirectory(path);
    }

    public static int mergeMissingValues(YamlSection runtime, YamlSection defaults) {
        return emaki.jiuwu.craft.corelib.api.yaml.YamlFiles.mergeMissingValues(runtime, defaults);
    }
}
