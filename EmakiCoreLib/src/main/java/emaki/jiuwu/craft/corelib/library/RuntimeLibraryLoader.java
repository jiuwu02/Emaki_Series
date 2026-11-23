package emaki.jiuwu.craft.corelib.library;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public final class RuntimeLibraryLoader {

    private static final String ALIYUN_REPO = "https://maven.aliyun.com/repository/central";
    private static final String CENTRAL_REPO = "https://repo1.maven.org/maven2";
    private static final String REPOSILITE_RELEASES_REPO = "https://repo2.crypticlib.com/releases";
    private static final String REPOSILITE_SNAPSHOTS_REPO = "https://repo2.crypticlib.com/snapshots";

    private static final String ADVENTURE_VERSION = "4.26.1";
    private static final Component LOG_PREFIX = Component.text("[library_loader] ", NamedTextColor.GRAY);

    private static final int PROBE_TIMEOUT_MS = 3000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 8000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30000;

    private static final String[] CHECKSUM_SUFFIXES = {".sha256", ".sha1", ".md5"};
    private static final String[] CHECKSUM_ALGORITHMS = {"SHA-256", "SHA-1", "MD5"};
    private static final Pattern RELEASE_PATTERN = Pattern.compile("<release>([^<]+)</release>");
    private static final Pattern LATEST_PATTERN = Pattern.compile("<latest>([^<]+)</latest>");

    private final ComponentLogger logger;
    private final Path cacheDirectory;

    public RuntimeLibraryLoader(Path dataDirectory, ComponentLogger logger) {
        if (dataDirectory == null) {
            throw new IllegalArgumentException("CoreLib 数据目录不能为 null");
        }
        if (logger == null) {
            throw new IllegalArgumentException("CoreLib 插件加载器 logger 不能为 null");
        }
        this.logger = logger;
        this.cacheDirectory = dataDirectory.resolve("libraries");
    }

    public List<Path> prepare() {
        List<RuntimeLibrary> libraries = new ArrayList<>();
        for (RuntimeLibrary library : baseLibraries()) {
            libraries.add(resolveRelease(library));
        }
        ensureCacheDirectory();

        List<RuntimeLibrary> missing = libraries.stream()
                .filter(library -> !library.coordinate().version().isBlank())
                .filter(library -> !cachedJarOpens(resolveLocalPath(library.coordinate())))
                .toList();
        boolean centralMissing = missing.stream().anyMatch(library -> library.repositories().isEmpty());
        String preferredRepo = centralMissing ? probePreferredRepository() : ALIYUN_REPO;
        String fallbackRepo = preferredRepo.equals(ALIYUN_REPO) ? CENTRAL_REPO : ALIYUN_REPO;

        List<Path> prepared = new ArrayList<>(libraries.size());
        List<RuntimeLibrary> failed = new ArrayList<>();
        int ready = 0;
        info(NamedTextColor.GRAY, "正在准备 CoreLib 运行库(共 " + libraries.size() + " 个)...");
        for (RuntimeLibrary library : libraries) {
            if (library.coordinate().version().isBlank()) {
                failed.add(library);
                error(NamedTextColor.RED, library + " 无法确定版本号");
                continue;
            }
            Path localFile = resolveLocalPath(library.coordinate());
            if (prepareLibrary(library, localFile, preferredRepo, fallbackRepo)) {
                ready++;
                if (library.classpath()) {
                    prepared.add(localFile);
                }
            } else {
                failed.add(library);
                error(NamedTextColor.RED, library + " 下载失败");
            }
        }

        if (!failed.isEmpty()) {
            throw new IllegalStateException("无法准备 CoreLib 运行时库: " + failed);
        }
        info(NamedTextColor.GREEN, "CoreLib 运行库准备完成(" + ready + "/" + libraries.size() + ")");
        return List.copyOf(prepared);
    }

    private static final String GRAALVM_VERSION = "25.4.4.1.1";

    private List<RuntimeLibrary> baseLibraries() {
        return List.of(
                adventure("adventure-api"),
                adventure("adventure-key"),
                RuntimeLibrary.maven("examination-api", new LibraryCoordinate("net.kyori", "examination-api", "1.3.0")),
                RuntimeLibrary.maven("examination-string", new LibraryCoordinate("net.kyori", "examination-string", "1.3.0")),
                adventure("adventure-nbt"),
                adventure("adventure-text-minimessage"),
                adventure("adventure-text-serializer-plain"),
                adventure("adventure-text-serializer-legacy"),
                adventure("adventure-text-serializer-gson"),
                adventure("adventure-text-serializer-gson-legacy-impl"),
                adventure("adventure-text-serializer-json"),
                adventure("adventure-text-serializer-json-legacy-impl"),
                adventure("adventure-text-serializer-commons"),
                RuntimeLibrary.maven("option", new LibraryCoordinate("net.kyori", "option", "1.1.0")),

                RuntimeLibrary.maven("gson", new LibraryCoordinate("com.google.code.gson", "gson", "2.11.0")),
                RuntimeLibrary.maven("boosted-yaml", new LibraryCoordinate("dev.dejvokep", "boosted-yaml", "1.3.8")),
                RuntimeLibrary.maven("exp4j", new LibraryCoordinate("net.objecthunter", "exp4j", "0.4.8")),
                RuntimeLibrary.maven("caffeine", new LibraryCoordinate("com.github.ben-manes.caffeine", "caffeine", "3.3.1")),
                RuntimeLibrary.maven("postgresql", new LibraryCoordinate("org.postgresql", "postgresql", "42.7.7")),
                RuntimeLibrary.maven("sqlite-jdbc", new LibraryCoordinate("org.xerial", "sqlite-jdbc", "3.50.3.0")),

                RuntimeLibrary.maven("polyglot", new LibraryCoordinate("org.graalvm.polyglot", "polyglot", GRAALVM_VERSION)),
                RuntimeLibrary.maven("js-language", new LibraryCoordinate("org.graalvm.js", "js-language", GRAALVM_VERSION)),
                RuntimeLibrary.maven("truffle-api", new LibraryCoordinate("org.graalvm.truffle", "truffle-api", GRAALVM_VERSION)),
                RuntimeLibrary.maven("truffle-runtime", new LibraryCoordinate("org.graalvm.truffle", "truffle-runtime", GRAALVM_VERSION)),
                RuntimeLibrary.maven("regex", new LibraryCoordinate("org.graalvm.regex", "regex", GRAALVM_VERSION)),
                RuntimeLibrary.maven("icu4j", new LibraryCoordinate("org.graalvm.shadowed", "icu4j", GRAALVM_VERSION)),

                RuntimeLibrary.release("emakinms", "emaki.jiuwu.craft", "emakinms",
                        List.of(REPOSILITE_RELEASES_REPO, REPOSILITE_SNAPSHOTS_REPO))
        );
    }

    private static RuntimeLibrary adventure(String artifactId) {
        return RuntimeLibrary.maven(artifactId, new LibraryCoordinate("net.kyori", artifactId, ADVENTURE_VERSION));
    }

    private RuntimeLibrary resolveRelease(RuntimeLibrary library) {
        if (!library.release()) {
            return library;
        }
        for (String repository : library.repositories()) {
            String version = fetchReleaseVersion(repository, library.coordinate());
            if (version != null) {
                return library.withVersion(version);
            }
        }
        String cached = newestCachedVersion(library.coordinate());
        if (cached != null) {
            warn(NamedTextColor.YELLOW, library + " 无法获取最新版本信息, 使用本地缓存版本 " + cached);
            return library.withVersion(cached);
        }
        return library.withVersion("");
    }

    private String fetchReleaseVersion(String repository, LibraryCoordinate coordinate) {
        String metadataUrl = repository + "/" + coordinate.groupId().replace('.', '/')
                + "/" + coordinate.artifactId()
                + "/maven-metadata.xml";
        String body = fetchText(metadataUrl);
        if (body == null) {
            return null;
        }
        Matcher release = RELEASE_PATTERN.matcher(body);
        if (release.find()) {
            return release.group(1).trim();
        }
        Matcher latest = LATEST_PATTERN.matcher(body);
        return latest.find() ? latest.group(1).trim() : null;
    }

    private String newestCachedVersion(LibraryCoordinate coordinate) {
        Path root = cacheDirectory
                .resolve(coordinate.groupId().replace('.', '/'))
                .resolve(coordinate.artifactId());
        if (!Files.isDirectory(root)) {
            return null;
        }
        try (Stream<Path> versions = Files.list(root)) {
            return versions
                    .filter(Files::isDirectory)
                    .map(version -> version.resolve(coordinate.artifactId() + "-" + version.getFileName().toString() + ".jar"))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(this::lastModified))
                    .map(jar -> jar.getParent().getFileName().toString())
                    .orElse(null);
        } catch (IOException exception) {
            return null;
        }
    }

    private boolean prepareLibrary(RuntimeLibrary library, Path localFile, String preferredRepo, String fallbackRepo) {
        if (validCached(localFile)) {
            info(NamedTextColor.GREEN, library + " 已就绪(缓存, " + formatSize(sizeKb(localFile)) + ")");
            return true;
        }
        if (cachedJarOpens(localFile) && !hasChecksumFile(localFile)) {
            info(NamedTextColor.GREEN, library + " 已就绪(缓存, 未附带校验值, " + formatSize(sizeKb(localFile)) + ")");
            return true;
        }
        deleteIfExists(localFile);
        deleteChecksumFiles(localFile);
        info(NamedTextColor.YELLOW, "正在下载 " + library + "...");
        boolean success = false;
        for (String repository : library.repositories().isEmpty() ? List.of(preferredRepo, fallbackRepo) : library.repositories()) {
            if (downloadLibrary(library.coordinate(), localFile, repository)) {
                success = true;
                break;
            }
        }
        if (success && validCached(localFile)) {
            info(NamedTextColor.GREEN, library + " 下载完成(" + formatSize(sizeKb(localFile)) + ")");
            return true;
        }
        deleteIfExists(localFile);
        deleteChecksumFiles(localFile);
        return false;
    }

    private static String artifactUrl(String repository, LibraryCoordinate coordinate) {
        return repository + "/" + coordinate.groupId().replace('.', '/')
                + "/" + coordinate.artifactId()
                + "/" + coordinate.version()
                + "/" + coordinate.artifactId() + "-" + coordinate.version() + ".jar";
    }

    private String probePreferredRepository() {
        info(NamedTextColor.YELLOW, "正在检测 Maven 仓库...");

        ProbeResult aliyun = probeRepository(ALIYUN_REPO, "阿里云镜像");
        ProbeResult central = probeRepository(CENTRAL_REPO, "Maven Central");
        if (aliyun.reachable && central.reachable) {
            ProbeResult chosen = aliyun.latencyMs <= central.latencyMs ? aliyun : central;
            info(NamedTextColor.GRAY, "已选择 Maven 仓库: " + chosen.name + "(延迟 " + chosen.latencyMs + "ms)");
            return chosen == aliyun ? ALIYUN_REPO : CENTRAL_REPO;
        }
        if (aliyun.reachable) {
            info(NamedTextColor.GRAY, "已选择 Maven 仓库: " + aliyun.name + "(延迟 " + aliyun.latencyMs + "ms)");
            return ALIYUN_REPO;
        }
        if (central.reachable) {
            info(NamedTextColor.GRAY, "已选择 Maven 仓库: " + central.name + "(延迟 " + central.latencyMs + "ms)");
            return CENTRAL_REPO;
        }
        warn(NamedTextColor.YELLOW, "Maven 仓库探测均不可达, 将优先尝试阿里云镜像");
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
        String downloadUrl = artifactUrl(repoUrl, library);
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
            ChecksumSource source = fetchChecksumSource(downloadUrl);
            if (source == null) {
                warn(NamedTextColor.YELLOW, "仓库未提供校验值, 已放弃该来源: " + downloadUrl);
                deleteIfExists(tempFile);
                return false;
            }
            String digest = digestHex(tempFile, source.algorithm());
            if (!digest.equalsIgnoreCase(source.expected())) {
                warn(NamedTextColor.YELLOW, "校验值不匹配, 已放弃该来源: " + downloadUrl);
                deleteIfExists(tempFile);
                return false;
            }
            Files.move(tempFile, localFile, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(checksumPath(localFile, source.suffix()), digest + "\n");
            return true;
        } catch (Exception exception) {
            debug("下载请求失败: " + downloadUrl, exception);
            deleteIfExists(tempFile);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String fetchText(String requestUrl) {
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(requestUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "EmakiCoreLib-LibraryLoader");
            if (connection.getResponseCode() != 200) {
                return null;
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception exception) {
            debug("文本请求失败: " + requestUrl, exception);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private Path resolveLocalPath(LibraryCoordinate coordinate) {
        return cacheDirectory
                .resolve(coordinate.groupId().replace('.', '/'))
                .resolve(coordinate.artifactId())
                .resolve(coordinate.version())
                .resolve(coordinate.artifactId() + "-" + coordinate.version() + ".jar");
    }

    private ChecksumSource fetchChecksumSource(String downloadUrl) {
        for (int index = 0; index < CHECKSUM_SUFFIXES.length; index++) {
            String body = fetchText(downloadUrl + CHECKSUM_SUFFIXES[index]);
            if (body == null) {
                continue;
            }
            String expected = body.trim().split("\\s+")[0];
            if (!expected.isEmpty()) {
                return new ChecksumSource(CHECKSUM_SUFFIXES[index], CHECKSUM_ALGORITHMS[index], expected);
            }
        }
        return null;
    }

    private Path checksumPath(Path localFile, String suffix) {
        return localFile.resolveSibling(localFile.getFileName() + suffix);
    }

    private boolean hasChecksumFile(Path localFile) {
        for (String suffix : CHECKSUM_SUFFIXES) {
            if (Files.isRegularFile(checksumPath(localFile, suffix))) {
                return true;
            }
        }
        return false;
    }

    private void deleteChecksumFiles(Path localFile) {
        for (String suffix : CHECKSUM_SUFFIXES) {
            deleteIfExists(checksumPath(localFile, suffix));
        }
    }

    private String readChecksumFile(Path localFile, String suffix) {
        Path checksum = checksumPath(localFile, suffix);
        if (!Files.isRegularFile(checksum)) {
            return null;
        }
        try {
            String value = Files.readString(checksum).trim();
            return value.isEmpty() ? null : value;
        } catch (IOException exception) {
            debug("无法读取运行库校验值: " + checksum, exception);
            return null;
        }
    }

    private boolean validCached(Path path) {
        if (!cachedJarOpens(path)) {
            return false;
        }
        for (int index = 0; index < CHECKSUM_SUFFIXES.length; index++) {
            String expected = readChecksumFile(path, CHECKSUM_SUFFIXES[index]);
            if (expected == null) {
                continue;
            }
            try {
                return digestHex(path, CHECKSUM_ALGORITHMS[index]).equalsIgnoreCase(expected);
            } catch (IOException | NoSuchAlgorithmException checksumFailure) {
                debug("运行库校验和计算失败: " + path, checksumFailure);
                return false;
            }
        }
        return false;
    }

    private boolean cachedJarOpens(Path path) {
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
            debug("运行库缓存损坏: " + path, exception);
            return false;
        }
    }

    private String digestHex(Path path, String algorithm) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            debug("无法删除运行库缓存: " + path, exception);
        }
    }

    private void ensureCacheDirectory() {
        try {
            Files.createDirectories(cacheDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建 CoreLib 运行时库缓存: " + cacheDirectory, exception);
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

    private long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
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

    private record ChecksumSource(String suffix, String algorithm, String expected) {
    }

    private record RuntimeLibrary(String id, LibraryCoordinate coordinate, List<String> repositories, boolean classpath, boolean release) {

        static RuntimeLibrary maven(String id, LibraryCoordinate coordinate) {
            return new RuntimeLibrary(id, coordinate, List.of(), true, false);
        }

        static RuntimeLibrary release(String id, String groupId, String artifactId, List<String> repositories) {
            return new RuntimeLibrary(id, new LibraryCoordinate(groupId, artifactId, ""), repositories, false, true);
        }

        RuntimeLibrary withVersion(String version) {
            return new RuntimeLibrary(id, new LibraryCoordinate(coordinate.groupId(), coordinate.artifactId(), version),
                    repositories, classpath, release);
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
