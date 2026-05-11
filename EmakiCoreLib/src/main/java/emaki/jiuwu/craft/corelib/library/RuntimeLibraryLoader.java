package emaki.jiuwu.craft.corelib.library;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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
 * 运行时依赖库预下载器。
 * <p>
 * 在 {@code onLoad()} 阶段将依赖库预下载到服务端的 Maven 本地仓库缓存目录
 * ({@code ./libraries/})，使服务端内置的 LibraryLoader 在后续启动时可以直接
 * 使用本地缓存而无需从远程仓库下载。
 * </p>
 * <p>
 * 工作流程：
 * <ol>
 *   <li>首次启动：服务端内置 LibraryLoader 从 Maven Central 下载（可能慢）；
 *       同时本加载器在 onLoad() 中将文件预下载到缓存目录</li>
 *   <li>后续启动：服务端内置 LibraryLoader 发现本地缓存已存在，直接使用，无需联网</li>
 *   <li>如果首次启动因网络问题失败：用户重启后，本加载器已将文件放入缓存，
 *       服务端内置 LibraryLoader 即可成功加载</li>
 * </ol>
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
     * 需要预下载的依赖库列表。
     * 与 plugin.yml 中的 libraries 声明保持一致。
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

    private final Logger logger;
    private final Path cacheDirectory;

    public RuntimeLibraryLoader(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        // 服务端的 libraries 缓存目录（Maven 本地仓库格式）
        this.cacheDirectory = Path.of("libraries");
    }

    /**
     * 预下载依赖库到服务端缓存目录。在 onLoad() 中调用。
     * <p>
     * 如果所有文件已存在则直接跳过，不产生任何网络请求。
     * </p>
     */
    public void load() {
        if (LIBRARIES.isEmpty()) {
            return;
        }

        // 快速检查：所有库是否都已缓存
        boolean allCached = LIBRARIES.stream().allMatch(lib -> Files.exists(resolveLocalPath(lib)));
        if (allCached) {
            return;
        }

        ensureCacheDirectory();

        String preferredRepo = probePreferredRepository();
        String fallbackRepo = preferredRepo.equals(ALIYUN_REPO) ? CENTRAL_REPO : ALIYUN_REPO;

        int downloaded = 0;
        int skipped = 0;
        int failed = 0;

        for (LibraryCoordinate library : LIBRARIES) {
            Path localFile = resolveLocalPath(library);
            if (Files.exists(localFile) && Files.isRegularFile(localFile)) {
                skipped++;
                continue;
            }

            boolean success = downloadLibrary(library, localFile, preferredRepo);
            if (!success) {
                success = downloadLibrary(library, localFile, fallbackRepo);
            }

            if (success) {
                downloaded++;
                long sizeKb = 0;
                try {
                    sizeKb = Files.size(localFile) / 1024;
                } catch (IOException ignored) {
                }
                logger.info("[LibraryLoader] \u2713 " + library + " (" + formatSize(sizeKb) + ")");
            } else {
                failed++;
                logger.warning("[LibraryLoader] \u2717 " + library + " (预下载失败)");
            }
        }

        if (downloaded > 0 || failed > 0) {
            logger.info("[LibraryLoader] 预下载完成 (已缓存=" + skipped + ", 新下载=" + downloaded
                    + (failed > 0 ? ", 失败=" + failed : "") + ")");
            if (downloaded > 0) {
                logger.info("[LibraryLoader] 新下载的依赖库将在下次启动时由服务端自动加载。");
            }
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

    private Path resolveLocalPath(LibraryCoordinate library) {
        return cacheDirectory
                .resolve(library.groupId().replace('.', '/'))
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
