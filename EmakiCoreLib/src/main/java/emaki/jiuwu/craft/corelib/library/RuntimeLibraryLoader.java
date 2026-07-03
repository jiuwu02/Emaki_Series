package emaki.jiuwu.craft.corelib.library;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;
import sun.misc.Unsafe;

public final class RuntimeLibraryLoader {

    private static final String ALIYUN_REPO = "https://maven.aliyun.com/repository/central";
    private static final String CENTRAL_REPO = "https://repo1.maven.org/maven2";

    private static final int PROBE_TIMEOUT_MS = 3000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 8000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30000;

    private static final MethodHandle ADD_URL_HANDLE;

    static {
        MethodHandle handle = null;
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Unsafe unsafe = (Unsafe) unsafeField.get(null);

            Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
            long offset = unsafe.staticFieldOffset(implLookupField);
            MethodHandles.Lookup trustedLookup = (MethodHandles.Lookup) unsafe.getObject(MethodHandles.Lookup.class, offset);

            handle = trustedLookup.findVirtual(URLClassLoader.class, "addURL", MethodType.methodType(void.class, URL.class));
        } catch (Throwable ignored) {
        }
        ADD_URL_HANDLE = handle;
    }

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path cacheDirectory;

    public RuntimeLibraryLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.cacheDirectory = plugin.getDataFolder().toPath().resolve("libraries");
    }

    public void load() {
        List<RuntimeLibrary> libraries = libraries();
        if (libraries.isEmpty()) {
            return;
        }
        if (ADD_URL_HANDLE == null) {
            logger.warning("[LibraryLoader] ClassLoader 注入不可用，跳过自定义库加载。");
            return;
        }

        ensureCacheDirectory();

        List<RuntimeLibrary> missingDownloadable = libraries.stream()
                .filter(RuntimeLibrary::downloadable)
                .filter(library -> !validCached(resolveLocalPath(library)))
                .toList();
        String preferredRepo = missingDownloadable.isEmpty() ? ALIYUN_REPO : probePreferredRepository();
        String fallbackRepo = preferredRepo.equals(ALIYUN_REPO) ? CENTRAL_REPO : ALIYUN_REPO;

        int prepared = 0;
        int prepareFailed = 0;
        List<RuntimeLibrary> readyLibraries = new ArrayList<>();
        logger.info("[LibraryLoader] 正在准备依赖库 (共 " + libraries.size() + " 个)...");
        for (RuntimeLibrary library : libraries) {
            Path localFile = resolveLocalPath(library);
            if (prepareLibrary(library, localFile, preferredRepo, fallbackRepo)) {
                prepared++;
                readyLibraries.add(library);
            } else {
                prepareFailed++;
                logger.warning("[LibraryLoader]   ✗ " + library + " (准备失败)");
            }
        }
        logger.info("[LibraryLoader] 依赖库准备完成 (" + prepared + "/" + libraries.size()
                + (prepareFailed > 0 ? ", 失败=" + prepareFailed : "") + ")");

        logger.info("[LibraryLoader] 正在注入依赖库 (共 " + readyLibraries.size() + " 个)...");
        List<RuntimeLibrary> injectedLibraries = new ArrayList<>();
        int injectFailed = 0;
        for (RuntimeLibrary library : readyLibraries) {
            Path localFile = resolveLocalPath(library);
            if (injectToClassLoader(localFile)) {
                injectedLibraries.add(library);
            } else {
                injectFailed++;
                logger.warning("[LibraryLoader]   ✗ " + library + " (注入失败)");
            }
        }

        logger.info("[LibraryLoader] 正在验证依赖库 (共 " + injectedLibraries.size() + " 个)...");
        int loaded = 0;
        int verifyFailed = 0;
        for (RuntimeLibrary library : injectedLibraries) {
            if (verifyClassProbes(library)) {
                loaded++;
                logger.info("[LibraryLoader]   ✓ " + library);
            } else {
                verifyFailed++;
                logger.warning("[LibraryLoader]   ✗ " + library + " (验证失败)");
            }
        }

        int loadFailed = injectFailed + verifyFailed;
        logger.info("[LibraryLoader] 依赖库加载完成 (" + loaded + "/" + libraries.size()
                + (loadFailed > 0 ? ", 失败=" + loadFailed : "") + ")");
    }

    private List<RuntimeLibrary> libraries() {
        String coreVersion = plugin.getDescription().getVersion();
        return List.of(
                RuntimeLibrary.maven("adventure-api", new LibraryCoordinate("net.kyori", "adventure-api", "4.26.1"),
                        "net.kyori.adventure.text.Component"),
                RuntimeLibrary.maven("adventure-key", new LibraryCoordinate("net.kyori", "adventure-key", "4.26.1"),
                        "net.kyori.adventure.key.Key"),
                RuntimeLibrary.maven("examination-api", new LibraryCoordinate("net.kyori", "examination-api", "1.3.0")),
                RuntimeLibrary.maven("examination-string", new LibraryCoordinate("net.kyori", "examination-string", "1.3.0")),
                RuntimeLibrary.maven("adventure-nbt", new LibraryCoordinate("net.kyori", "adventure-nbt", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-minimessage", new LibraryCoordinate("net.kyori", "adventure-text-minimessage", "4.26.1"),
                        "net.kyori.adventure.text.minimessage.MiniMessage"),
                RuntimeLibrary.maven("adventure-text-serializer-plain", new LibraryCoordinate("net.kyori", "adventure-text-serializer-plain", "4.26.1"),
                        "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer"),
                RuntimeLibrary.maven("adventure-text-serializer-legacy", new LibraryCoordinate("net.kyori", "adventure-text-serializer-legacy", "4.26.1"),
                        "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer"),
                RuntimeLibrary.maven("adventure-text-serializer-gson", new LibraryCoordinate("net.kyori", "adventure-text-serializer-gson", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-serializer-gson-legacy-impl", new LibraryCoordinate("net.kyori", "adventure-text-serializer-gson-legacy-impl", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-serializer-json", new LibraryCoordinate("net.kyori", "adventure-text-serializer-json", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-serializer-json-legacy-impl", new LibraryCoordinate("net.kyori", "adventure-text-serializer-json-legacy-impl", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-serializer-commons", new LibraryCoordinate("net.kyori", "adventure-text-serializer-commons", "4.21.0")),
                RuntimeLibrary.maven("option", new LibraryCoordinate("net.kyori", "option", "1.1.0")),
                RuntimeLibrary.maven("gson", new LibraryCoordinate("com.google.code.gson", "gson", "2.8.0"),
                        "com.google.gson.Gson"),
                RuntimeLibrary.maven("boosted-yaml", new LibraryCoordinate("dev.dejvokep", "boosted-yaml", "1.3.7"),
                        "dev.dejvokep.boostedyaml.YamlDocument"),
                RuntimeLibrary.maven("exp4j", new LibraryCoordinate("net.objecthunter", "exp4j", "0.4.8"),
                        "net.objecthunter.exp4j.ExpressionBuilder"),
                RuntimeLibrary.maven("caffeine", new LibraryCoordinate("com.github.ben-manes.caffeine", "caffeine", "3.2.4"),
                        "com.github.benmanes.caffeine.cache.Caffeine"),
                RuntimeLibrary.maven("graal-polyglot", new LibraryCoordinate("org.graalvm.polyglot", "polyglot", "25.0.3"),
                        "org.graalvm.polyglot.Context"),
                RuntimeLibrary.maven("graal-collections", new LibraryCoordinate("org.graalvm.sdk", "collections", "25.0.3")),
                RuntimeLibrary.maven("graal-nativeimage", new LibraryCoordinate("org.graalvm.sdk", "nativeimage", "25.0.3")),
                RuntimeLibrary.maven("graal-word", new LibraryCoordinate("org.graalvm.sdk", "word", "25.0.3")),
                RuntimeLibrary.maven("graal-js-language", new LibraryCoordinate("org.graalvm.js", "js-language", "25.0.3"),
                        "com.oracle.truffle.js.lang.JavaScriptLanguage"),
                RuntimeLibrary.maven("graal-regex", new LibraryCoordinate("org.graalvm.regex", "regex", "25.0.3")),
                RuntimeLibrary.maven("graal-truffle-api", new LibraryCoordinate("org.graalvm.truffle", "truffle-api", "25.0.3"),
                        "com.oracle.truffle.api.TruffleLanguage"),
                RuntimeLibrary.maven("graal-icu4j", new LibraryCoordinate("org.graalvm.shadowed", "icu4j", "25.0.3")),
                RuntimeLibrary.maven("graal-xz", new LibraryCoordinate("org.graalvm.shadowed", "xz", "25.0.3")),
                RuntimeLibrary.bundled("bstats-runtime",
                        new LibraryCoordinate("emaki.jiuwu.craft", "emaki-bstats-runtime", coreVersion),
                        "runtime-libraries/emaki/jiuwu/craft/emaki-bstats-runtime/" + coreVersion
                                + "/emaki-bstats-runtime-" + coreVersion + ".jar",
                        "emaki.jiuwu.craft.runtime.bstats.bukkit.Metrics")
        );
    }

    private boolean prepareLibrary(RuntimeLibrary library, Path localFile, String preferredRepo, String fallbackRepo) {
        if (validCached(localFile)) {
            logger.info("[LibraryLoader]   ✓ " + library + " (缓存命中)");
            return true;
        }
        deleteIfExists(localFile);
        if (library.fallbackResource() != null && extractBundledLibrary(library, localFile)) {
            logger.info("[LibraryLoader]   ✓ " + library + " (随包释放)");
            return true;
        }
        if (!library.downloadable()) {
            return false;
        }
        boolean success = downloadLibrary(library.coordinate(), localFile, preferredRepo);
        if (!success) {
            success = downloadLibrary(library.coordinate(), localFile, fallbackRepo);
        }
        if (success && validCached(localFile)) {
            long sizeKb = sizeKb(localFile);
            logger.info("[LibraryLoader]   ✓ " + library + " (下载, " + formatSize(sizeKb) + ")");
            return true;
        }
        deleteIfExists(localFile);
        return false;
    }

    private boolean extractBundledLibrary(RuntimeLibrary library, Path localFile) {
        String resource = library.fallbackResource();
        if (resource == null) {
            return false;
        }
        try (InputStream inputStream = plugin.getResource(resource)) {
            if (inputStream == null) {
                logger.warning("[LibraryLoader] 随包依赖不存在: " + resource);
                return false;
            }
            Files.createDirectories(localFile.getParent());
            Path tempFile = localFile.resolveSibling(localFile.getFileName() + ".tmp");
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tempFile, localFile, StandardCopyOption.REPLACE_EXISTING);
            return validCached(localFile);
        } catch (Exception exception) {
            logger.log(Level.FINE, "[LibraryLoader] 随包依赖释放失败: " + resource, exception);
            deleteIfExists(localFile);
            return false;
        }
    }

    private String probePreferredRepository() {
        logger.info("[LibraryLoader] 正在检测最快的 Maven 仓库...");

        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "emaki-library-probe");
            thread.setDaemon(true);
            return thread;
        });

        try {
            CompletableFuture<ProbeResult> aliyunFuture = CompletableFuture.supplyAsync(
                    () -> probeRepository(ALIYUN_REPO, "阿里云镜像"), executor);
            CompletableFuture<ProbeResult> centralFuture = CompletableFuture.supplyAsync(
                    () -> probeRepository(CENTRAL_REPO, "Maven Central"), executor);

            ProbeResult aliyun = null;
            ProbeResult central = null;
            try {
                aliyun = aliyunFuture.get(PROBE_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            try {
                central = centralFuture.get(PROBE_TIMEOUT_MS + 500, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }

            if (aliyun != null && aliyun.reachable && central != null && central.reachable) {
                ProbeResult chosen = aliyun.latencyMs <= central.latencyMs ? aliyun : central;
                logger.info("[LibraryLoader] 已选择仓库: " + chosen.name + " (延迟 " + chosen.latencyMs + "ms)");
                return chosen == aliyun ? ALIYUN_REPO : CENTRAL_REPO;
            } else if (aliyun != null && aliyun.reachable) {
                logger.info("[LibraryLoader] 已选择仓库: " + aliyun.name + " (延迟 " + aliyun.latencyMs + "ms)");
                return ALIYUN_REPO;
            } else if (central != null && central.reachable) {
                logger.info("[LibraryLoader] 已选择仓库: " + central.name + " (延迟 " + central.latencyMs + "ms)");
                return CENTRAL_REPO;
            } else {
                logger.warning("[LibraryLoader] 两个仓库均不可达，使用阿里云镜像作为默认。");
                return ALIYUN_REPO;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private ProbeResult probeRepository(String repoUrl, String name) {
        long start = System.currentTimeMillis();
        try {
            URL url = URI.create(repoUrl + "/org/apache/maven/maven-parent/1/maven-parent-1.pom").toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(PROBE_TIMEOUT_MS);
            connection.setReadTimeout(PROBE_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "EmakiCoreLib-LibraryLoader");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            long latency = System.currentTimeMillis() - start;
            return new ProbeResult(name, responseCode >= 200 && responseCode < 400, latency);
        } catch (Exception exception) {
            return new ProbeResult(name, false, System.currentTimeMillis() - start);
        }
    }

    private boolean downloadLibrary(LibraryCoordinate library, Path localFile, String repoUrl) {
        String artifactPath = library.groupId().replace('.', '/')
                + "/" + library.artifactId()
                + "/" + library.version()
                + "/" + library.artifactId() + "-" + library.version() + ".jar";
        String downloadUrl = repoUrl + "/" + artifactPath;

        try {
            Files.createDirectories(localFile.getParent());
            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "EmakiCoreLib-LibraryLoader");

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                connection.disconnect();
                return false;
            }

            Path tempFile = localFile.resolveSibling(localFile.getFileName() + ".tmp");
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            connection.disconnect();

            Files.move(tempFile, localFile, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception exception) {
            logger.log(Level.FINE, "[LibraryLoader] 下载失败: " + downloadUrl, exception);
            return false;
        }
    }

    private boolean injectToClassLoader(Path jarPath) {
        try {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            if (!(classLoader instanceof URLClassLoader urlClassLoader)) {
                logger.warning("[LibraryLoader] ClassLoader 不是 URLClassLoader: " + classLoader.getClass().getName());
                return false;
            }
            ADD_URL_HANDLE.invoke(urlClassLoader, jarPath.toUri().toURL());
            return true;
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[LibraryLoader] ClassLoader 注入失败: " + jarPath.getFileName(), throwable);
            return false;
        }
    }

    private boolean verifyClassProbes(RuntimeLibrary library) {
        if (library.classProbes().isEmpty()) {
            return true;
        }
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        for (String className : library.classProbes()) {
            try {
                Class.forName(className, false, classLoader);
            } catch (Throwable throwable) {
                logger.log(Level.WARNING, "[LibraryLoader] 类验证失败: " + className + " from " + library, throwable);
                return false;
            }
        }
        return true;
    }

    private Path resolveLocalPath(RuntimeLibrary library) {
        LibraryCoordinate coordinate = library.coordinate();
        return cacheDirectory
                .resolve(coordinate.groupId().replace('.', '/'))
                .resolve(coordinate.artifactId())
                .resolve(coordinate.version())
                .resolve(coordinate.artifactId() + "-" + coordinate.version() + ".jar");
    }

    private boolean validCached(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            if (Files.size(path) <= 0L) {
                return false;
            }
            try (JarFile ignored = new JarFile(path.toFile())) {
                return true;
            }
        } catch (IOException exception) {
            logger.log(Level.FINE, "[LibraryLoader] 依赖缓存损坏: " + path, exception);
            return false;
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            logger.log(Level.FINE, "[LibraryLoader] 无法删除依赖缓存: " + path, exception);
        }
    }

    private long sizeKb(Path path) {
        try {
            return Files.size(path) / 1024;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private void ensureCacheDirectory() {
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException exception) {
            logger.warning("[LibraryLoader] 无法创建缓存目录: " + cacheDirectory);
        }
    }

    private String formatSize(long sizeKb) {
        if (sizeKb < 1024) {
            return sizeKb + "KB";
        }
        return String.format("%.1fMB", sizeKb / 1024.0);
    }

    private record ProbeResult(String name, boolean reachable, long latencyMs) {
    }

    private record RuntimeLibrary(String id,
            LibraryCoordinate coordinate,
            boolean downloadable,
            String fallbackResource,
            List<String> classProbes) {

        static RuntimeLibrary maven(String id, LibraryCoordinate coordinate, String... classProbes) {
            return new RuntimeLibrary(id, coordinate, true, null, List.of(classProbes));
        }

        static RuntimeLibrary bundled(String id, LibraryCoordinate coordinate, String fallbackResource, String... classProbes) {
            return new RuntimeLibrary(id, coordinate, false, fallbackResource, List.of(classProbes));
        }

        @Override
        public String toString() {
            return id + " [" + coordinate + "]";
        }
    }

    public record LibraryCoordinate(String groupId, String artifactId, String version) {

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }
}
