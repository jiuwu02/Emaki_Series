package emaki.jiuwu.craft.corelib.library;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;








public final class RuntimeLibraryLoader {

    private static final String ALIYUN_REPO = "https://maven.aliyun.com/repository/central";
    private static final String CENTRAL_REPO = "https://repo1.maven.org/maven2";
    private static final Component LOG_PREFIX = Component.text("[LibraryLoader] ", NamedTextColor.GRAY);

    private static final int PROBE_TIMEOUT_MS = 3000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 8000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30000;

    private final ComponentLogger logger;
    private final Path cacheDirectory;

    public RuntimeLibraryLoader(Path dataDirectory, ComponentLogger logger) {
        if (dataDirectory == null) {
            throw new IllegalArgumentException("CoreLib data directory cannot be null");
        }
        if (logger == null) {
            throw new IllegalArgumentException("CoreLib plugin loader logger cannot be null");
        }
        this.logger = logger;
        this.cacheDirectory = dataDirectory.resolve("libraries");
    }







    public List<Path> prepare() {
        List<RuntimeLibrary> libraries = libraries();
        ensureCacheDirectory();

        List<RuntimeLibrary> missing = libraries.stream()
                .filter(library -> !validCached(resolveLocalPath(library)))
                .toList();
        String preferredRepo = missing.isEmpty() ? ALIYUN_REPO : probePreferredRepository();
        String fallbackRepo = preferredRepo.equals(ALIYUN_REPO) ? CENTRAL_REPO : ALIYUN_REPO;

        List<Path> prepared = new ArrayList<>(libraries.size());
        List<RuntimeLibrary> failed = new ArrayList<>();
        info(NamedTextColor.GRAY, "正在准备 CoreLib 运行库（共 " + libraries.size() + " 个）...");
        for (RuntimeLibrary library : libraries) {
            Path localFile = resolveLocalPath(library);
            if (prepareLibrary(library, localFile, preferredRepo, fallbackRepo)) {
                prepared.add(localFile);
            } else {
                failed.add(library);
                error(NamedTextColor.RED, library + " 下载失败");
            }
        }

        if (!failed.isEmpty()) {
            throw new IllegalStateException("CoreLib runtime libraries could not be prepared: " + failed);
        }
        info(NamedTextColor.GREEN, "CoreLib 运行库准备完成（" + prepared.size() + "/" + libraries.size() + "）");
        return List.copyOf(prepared);
    }

    private List<RuntimeLibrary> libraries() {
        return List.of(
                RuntimeLibrary.maven("adventure-api", new LibraryCoordinate("net.kyori", "adventure-api", "4.26.1")),
                RuntimeLibrary.maven("adventure-key", new LibraryCoordinate("net.kyori", "adventure-key", "4.26.1")),
                RuntimeLibrary.maven("examination-api", new LibraryCoordinate("net.kyori", "examination-api", "1.3.0")),
                RuntimeLibrary.maven("examination-string", new LibraryCoordinate("net.kyori", "examination-string", "1.3.0")),
                RuntimeLibrary.maven("adventure-nbt", new LibraryCoordinate("net.kyori", "adventure-nbt", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-minimessage", new LibraryCoordinate("net.kyori", "adventure-text-minimessage", "4.26.1")),
                RuntimeLibrary.maven("adventure-text-serializer-plain", new LibraryCoordinate("net.kyori", "adventure-text-serializer-plain", "4.26.1")),
                RuntimeLibrary.maven("adventure-text-serializer-legacy", new LibraryCoordinate("net.kyori", "adventure-text-serializer-legacy", "4.26.1")),
                RuntimeLibrary.maven("adventure-text-serializer-gson", new LibraryCoordinate("net.kyori", "adventure-text-serializer-gson", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-serializer-gson-legacy-impl", new LibraryCoordinate("net.kyori", "adventure-text-serializer-gson-legacy-impl", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-serializer-json", new LibraryCoordinate("net.kyori", "adventure-text-serializer-json", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-serializer-json-legacy-impl", new LibraryCoordinate("net.kyori", "adventure-text-serializer-json-legacy-impl", "4.21.0")),
                RuntimeLibrary.maven("adventure-text-serializer-commons", new LibraryCoordinate("net.kyori", "adventure-text-serializer-commons", "4.21.0")),
                RuntimeLibrary.maven("option", new LibraryCoordinate("net.kyori", "option", "1.1.0")),
                RuntimeLibrary.maven("gson", new LibraryCoordinate("com.google.code.gson", "gson", "2.8.0")),
                RuntimeLibrary.maven("boosted-yaml", new LibraryCoordinate("dev.dejvokep", "boosted-yaml", "1.3.7")),
                RuntimeLibrary.maven("exp4j", new LibraryCoordinate("net.objecthunter", "exp4j", "0.4.8")),
                RuntimeLibrary.maven("caffeine", new LibraryCoordinate("com.github.ben-manes.caffeine", "caffeine", "3.2.4")),
                RuntimeLibrary.maven("graal-polyglot", new LibraryCoordinate("org.graalvm.polyglot", "polyglot", "25.0.3")),
                RuntimeLibrary.maven("graal-collections", new LibraryCoordinate("org.graalvm.sdk", "collections", "25.0.3")),
                RuntimeLibrary.maven("graal-nativeimage", new LibraryCoordinate("org.graalvm.sdk", "nativeimage", "25.0.3")),
                RuntimeLibrary.maven("graal-word", new LibraryCoordinate("org.graalvm.sdk", "word", "25.0.3")),
                RuntimeLibrary.maven("graal-js-language", new LibraryCoordinate("org.graalvm.js", "js-language", "25.0.3")),
                RuntimeLibrary.maven("graal-regex", new LibraryCoordinate("org.graalvm.regex", "regex", "25.0.3")),
                RuntimeLibrary.maven("graal-truffle-api", new LibraryCoordinate("org.graalvm.truffle", "truffle-api", "25.0.3")),
                RuntimeLibrary.maven("graal-icu4j", new LibraryCoordinate("org.graalvm.shadowed", "icu4j", "25.0.3")),
                RuntimeLibrary.maven("graal-xz", new LibraryCoordinate("org.graalvm.shadowed", "xz", "25.0.3"))
        );
    }

    private boolean prepareLibrary(RuntimeLibrary library, Path localFile, String preferredRepo, String fallbackRepo) {
        if (validCached(localFile)) {
            info(NamedTextColor.GREEN, library + " 已就绪（缓存，" + formatSize(sizeKb(localFile)) + "）");
            return true;
        }
        deleteIfExists(localFile);
        info(NamedTextColor.YELLOW, "正在下载 " + library + "...");
        boolean success = downloadLibrary(library.coordinate(), localFile, preferredRepo);
        if (!success) {
            success = downloadLibrary(library.coordinate(), localFile, fallbackRepo);
        }
        if (success && validCached(localFile)) {
            info(NamedTextColor.GREEN, library + " 下载完成（" + formatSize(sizeKb(localFile)) + "）");
            return true;
        }
        deleteIfExists(localFile);
        return false;
    }

    private String probePreferredRepository() {
        info(NamedTextColor.YELLOW, "正在检测 Maven 仓库...");

        ProbeResult aliyun = probeRepository(ALIYUN_REPO, "阿里云镜像");
        ProbeResult central = probeRepository(CENTRAL_REPO, "Maven Central");
        if (aliyun.reachable && central.reachable) {
            ProbeResult chosen = aliyun.latencyMs <= central.latencyMs ? aliyun : central;
            info(NamedTextColor.GRAY, "已选择 Maven 仓库：" + chosen.name + "（延迟 " + chosen.latencyMs + "ms）");
            return chosen == aliyun ? ALIYUN_REPO : CENTRAL_REPO;
        }
        if (aliyun.reachable) {
            info(NamedTextColor.GRAY, "已选择 Maven 仓库：" + aliyun.name + "（延迟 " + aliyun.latencyMs + "ms）");
            return ALIYUN_REPO;
        }
        if (central.reachable) {
            info(NamedTextColor.GRAY, "已选择 Maven 仓库：" + central.name + "（延迟 " + central.latencyMs + "ms）");
            return CENTRAL_REPO;
        }
        warn(NamedTextColor.YELLOW, "Maven 仓库探测均不可达，将优先尝试阿里云镜像");
        return ALIYUN_REPO;
    }

    private ProbeResult probeRepository(String repoUrl, String name) {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(repoUrl + "/org/apache/maven/maven-parent/1/maven-parent-1.pom").toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(PROBE_TIMEOUT_MS);
            connection.setReadTimeout(PROBE_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "EmakiCoreLib-LibraryLoader");
            int responseCode = connection.getResponseCode();
            return new ProbeResult(name, responseCode >= 200 && responseCode < 400, System.currentTimeMillis() - start);
        } catch (Exception exception) {
            return new ProbeResult(name, false, System.currentTimeMillis() - start);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private boolean downloadLibrary(LibraryCoordinate library, Path localFile, String repoUrl) {
        String artifactPath = library.groupId().replace('.', '/')
                + "/" + library.artifactId()
                + "/" + library.version()
                + "/" + library.artifactId() + "-" + library.version() + ".jar";
        String downloadUrl = repoUrl + "/" + artifactPath;
        Path tempFile = localFile.resolveSibling(localFile.getFileName() + ".tmp");
        HttpURLConnection connection = null;
        try {
            Files.createDirectories(localFile.getParent());
            URL url = URI.create(downloadUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "EmakiCoreLib-LibraryLoader");
            if (connection.getResponseCode() != 200) {
                return false;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tempFile, localFile, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception exception) {
            debug("下载请求失败：" + downloadUrl, exception);
            deleteIfExists(tempFile);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
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
        } catch (IOException | SecurityException exception) {
            debug("运行库缓存损坏：" + path, exception);
            return false;
        }
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            debug("无法删除运行库缓存：" + path, exception);
        }
    }

    private void ensureCacheDirectory() {
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create CoreLib runtime library cache: " + cacheDirectory, exception);
        }
    }

    private void info(NamedTextColor color, String message) {
        logger.info(LOG_PREFIX.append(Component.text(message, color)));
    }

    private void warn(NamedTextColor color, String message) {
        logger.warn(LOG_PREFIX.append(Component.text(message, color)));
    }

    private void error(NamedTextColor color, String message) {
        logger.error(LOG_PREFIX.append(Component.text(message, color)));
    }

    private void debug(String message, Throwable throwable) {
        logger.debug(LOG_PREFIX.append(Component.text(message, NamedTextColor.DARK_GRAY)), throwable);
    }

    private long sizeKb(Path path) {
        try {
            return Files.size(path) / 1024;
        } catch (IOException ignored) {
            return 0L;
        }
    }

    private String formatSize(long sizeKb) {
        if (sizeKb < 1024) {
            return sizeKb + "KB";
        }
        return String.format(Locale.ROOT, "%.1fMB", sizeKb / 1024.0);
    }

    private record ProbeResult(String name, boolean reachable, long latencyMs) {
    }

    private record RuntimeLibrary(String id, LibraryCoordinate coordinate) {

        static RuntimeLibrary maven(String id, LibraryCoordinate coordinate) {
            return new RuntimeLibrary(id, coordinate);
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
