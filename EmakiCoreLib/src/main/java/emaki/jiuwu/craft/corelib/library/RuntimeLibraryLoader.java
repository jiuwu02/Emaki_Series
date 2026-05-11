package emaki.jiuwu.craft.corelib.library;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * 运行时依赖库加载器。
 * <p>
 * 在 {@code onLoad()} 阶段自行从 Maven 仓库下载依赖并注入 ClassLoader，
 * 替代 Spigot 内置的 {@code plugin.yml libraries} 机制。
 * </p>
 * <p>
 * 支持双仓库延迟探测：优先使用阿里云 Maven 镜像，延迟过高或不可用时回退到 Maven Central。
 * </p>
 */
public final class RuntimeLibraryLoader {

    private static final String ALIYUN_REPO = "https://maven.aliyun.com/repository/central";
    private static final String CENTRAL_REPO = "https://repo1.maven.org/maven2";

    private static final int PROBE_TIMEOUT_MS = 3000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 8000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30000;

    /**
     * 需要在运行时下载的依赖库列表。
     * 更新依赖版本时同步修改此处。
     */
    private static final List<LibraryCoordinate> LIBRARIES = List.of(
            new LibraryCoordinate("net.kyori", "adventure-platform-bukkit", "4.4.1"),
            new LibraryCoordinate("net.kyori", "adventure-text-minimessage", "4.26.1"),
            new LibraryCoordinate("net.kyori", "adventure-text-serializer-plain", "4.26.1"),
            new LibraryCoordinate("dev.dejvokep", "boosted-yaml", "1.3.7"),
            new LibraryCoordinate("org.graalvm.polyglot", "polyglot", "24.2.1"),
            new LibraryCoordinate("org.graalvm.js", "js-language", "24.2.1"),
            new LibraryCoordinate("org.graalvm.regex", "regex", "24.2.1"),
            new LibraryCoordinate("org.graalvm.truffle", "truffle-api", "24.2.1"),
            new LibraryCoordinate("org.graalvm.shadowed", "icu4j", "24.2.1")
    );

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path cacheDirectory;

    public RuntimeLibraryLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.cacheDirectory = plugin.getDataFolder().toPath().resolve("libraries");
    }

    /**
     * 执行依赖库加载。在 onLoad() 中调用。
     */
    public void load() {
        if (LIBRARIES.isEmpty()) {
            return;
        }

        ensureCacheDirectory();

        String preferredRepo = probePreferredRepository();
        String fallbackRepo = preferredRepo.equals(ALIYUN_REPO) ? CENTRAL_REPO : ALIYUN_REPO;

        logger.info("[LibraryLoader] 正在加载依赖库 (共 " + LIBRARIES.size() + " 个)...");

        int loaded = 0;
        int cached = 0;
        int downloaded = 0;
        int failed = 0;

        for (LibraryCoordinate library : LIBRARIES) {
            Path localFile = resolveLocalPath(library);
            if (Files.exists(localFile) && Files.isRegularFile(localFile)) {
                if (injectToClassLoader(localFile)) {
                    loaded++;
                    cached++;
                    logger.info("[LibraryLoader] \u2713 " + library + " (已缓存)");
                } else {
                    failed++;
                    logger.warning("[LibraryLoader] \u2717 " + library + " (注入失败)");
                }
                continue;
            }

            boolean success = downloadLibrary(library, localFile, preferredRepo);
            if (!success) {
                logger.info("[LibraryLoader] 首选仓库下载失败，尝试备用仓库...");
                success = downloadLibrary(library, localFile, fallbackRepo);
            }

            if (success && injectToClassLoader(localFile)) {
                loaded++;
                downloaded++;
                long sizeKb = 0;
                try {
                    sizeKb = Files.size(localFile) / 1024;
                } catch (IOException ignored) {
                }
                logger.info("[LibraryLoader] \u2713 " + library + " (下载完成, " + formatSize(sizeKb) + ")");
            } else {
                failed++;
                logger.warning("[LibraryLoader] \u2717 " + library + " (下载或注入失败)");
            }
        }

        logger.info("[LibraryLoader] 依赖库加载完成 (" + loaded + "/" + LIBRARIES.size()
                + ", 缓存=" + cached + ", 下载=" + downloaded
                + (failed > 0 ? ", 失败=" + failed : "") + ")");
    }

    /**
     * 探测最快的 Maven 仓库。
     */
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
                if (aliyun.latencyMs <= central.latencyMs) {
                    logger.info("[LibraryLoader] 已选择仓库: " + aliyun.name + " (延迟 " + aliyun.latencyMs + "ms)");
                    return ALIYUN_REPO;
                } else {
                    logger.info("[LibraryLoader] 已选择仓库: " + central.name + " (延迟 " + central.latencyMs + "ms)");
                    return CENTRAL_REPO;
                }
            } else if (aliyun != null && aliyun.reachable) {
                logger.info("[LibraryLoader] 已选择仓库: " + aliyun.name + " (延迟 " + aliyun.latencyMs + "ms)");
                return ALIYUN_REPO;
            } else if (central != null && central.reachable) {
                logger.info("[LibraryLoader] 已选择仓库: " + central.name + " (延迟 " + central.latencyMs + "ms)");
                return CENTRAL_REPO;
            } else {
                logger.warning("[LibraryLoader] 两个仓库均不可达，将使用阿里云镜像作为默认。");
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
            boolean reachable = responseCode >= 200 && responseCode < 400;
            return new ProbeResult(name, reachable, latency);
        } catch (Exception exception) {
            long latency = System.currentTimeMillis() - start;
            return new ProbeResult(name, false, latency);
        }
    }

    /**
     * 下载单个依赖库到本地缓存。
     */
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

    /**
     * 将 jar 注入到插件的 ClassLoader。
     */
    private boolean injectToClassLoader(Path jarPath) {
        try {
            ClassLoader classLoader = plugin.getClass().getClassLoader();
            if (classLoader instanceof URLClassLoader urlClassLoader) {
                addUrlToClassLoader(urlClassLoader, jarPath.toUri().toURL());
                return true;
            }
            addUrlViaReflection(classLoader, jarPath.toUri().toURL());
            return true;
        } catch (Throwable throwable) {
            logger.log(Level.WARNING, "[LibraryLoader] ClassLoader 注入失败: " + jarPath.getFileName(), throwable);
            return false;
        }
    }

    private void addUrlToClassLoader(URLClassLoader classLoader, URL url) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(URLClassLoader.class, MethodHandles.lookup());
        MethodHandle addUrl = lookup.findVirtual(URLClassLoader.class, "addURL", MethodType.methodType(void.class, URL.class));
        addUrl.invoke(classLoader, url);
    }

    private void addUrlViaReflection(ClassLoader classLoader, URL url) throws Throwable {
        Class<?> clazz = classLoader.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
                MethodHandle addUrl = lookup.findVirtual(clazz, "addURL", MethodType.methodType(void.class, URL.class));
                addUrl.invoke(classLoader, url);
                return;
            } catch (NoSuchMethodException | IllegalAccessException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new UnsupportedOperationException("无法找到 addURL 方法: " + classLoader.getClass().getName());
    }

    private Path resolveLocalPath(LibraryCoordinate library) {
        return cacheDirectory
                .resolve(library.groupId().replace('.', File.separatorChar))
                .resolve(library.artifactId())
                .resolve(library.version())
                .resolve(library.artifactId() + "-" + library.version() + ".jar");
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

    public record LibraryCoordinate(String groupId, String artifactId, String version) {

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + version;
        }
    }
}
