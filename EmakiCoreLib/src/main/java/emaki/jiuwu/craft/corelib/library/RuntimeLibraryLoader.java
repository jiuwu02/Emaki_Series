package emaki.jiuwu.craft.corelib.library;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;

public final class RuntimeLibraryLoader {

    private static final String ALIYUN_REPO = "https://maven.aliyun.com/repository/central";
    private static final String CENTRAL_REPO = "https://repo1.maven.org/maven2";
    private static final String REPOSILITE_RELEASES_REPO = "https://repo2.crypticlib.com/releases";
    private static final String REPOSILITE_SNAPSHOTS_REPO = "https://repo2.crypticlib.com/snapshots";

    private static final String WORLD_MOUNT_GROUP_ID = "emaki.jiuwu.craft";
    private static final String WORLD_MOUNT_ARTIFACT_ID = "emakinms";
    private static final String WORLD_MOUNT_MIN_VERSION = "1.0.0";
    private static final String WORLD_MOUNT_DESCRIPTOR = WORLD_MOUNT_GROUP_ID + ":" + WORLD_MOUNT_ARTIFACT_ID;

    private static final String FOLIA_REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer";
    private static final String CRAFT_SERVER = "org.bukkit.craftbukkit.CraftServer";
    private static final Pattern LATEST_VERSION = Pattern.compile("<(?:latest|release)>([^<]+)</(?:latest|release)>");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-fA-F]{64}");

    private static final String ADVENTURE_VERSION = "4.26.1";
    private static final Component LOG_PREFIX = Component.text("[library_loader] ", NamedTextColor.GRAY);

    private static final int PROBE_TIMEOUT_MS = 3000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 8000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 30000;

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
        List<RuntimeLibrary> libraries = libraries();
        ensureCacheDirectory();

        List<RuntimeLibrary> missing = libraries.stream()
                .filter(library -> !validCached(resolveLocalPath(library.coordinate()), library.expectedSha256()))
                .toList();
        String preferredRepo = missing.isEmpty() ? ALIYUN_REPO : probePreferredRepository();
        String fallbackRepo = preferredRepo.equals(ALIYUN_REPO) ? CENTRAL_REPO : ALIYUN_REPO;

        List<Path> prepared = new ArrayList<>(libraries.size() + 1);
        List<RuntimeLibrary> failed = new ArrayList<>();
        info(NamedTextColor.GRAY, "正在准备 CoreLib 运行库(共 " + libraries.size() + " 个)...");
        for (RuntimeLibrary library : libraries) {
            Path localFile = resolveLocalPath(library.coordinate());
            if (prepareLibrary(library, localFile, preferredRepo, fallbackRepo)) {
                prepared.add(localFile);
            } else {
                failed.add(library);
                error(NamedTextColor.RED, library + " 下载失败");
            }
        }

        if (!failed.isEmpty()) {
            throw new IllegalStateException("无法准备 CoreLib 运行时库: " + failed);
        }
        boolean foliaRuntime = isFoliaRuntime();
        if (foliaRuntime) {
            prepared.add(prepareWorldMountBridge());
        }
        info(NamedTextColor.GREEN, "CoreLib 运行库准备完成(" + prepared.size() + "/" + (libraries.size() + (foliaRuntime ? 1 : 0)) + ")");
        return List.copyOf(prepared);
    }

    private static final String GRAALVM_VERSION = "25.4.4.1.1";

    private List<RuntimeLibrary> libraries() {
        return List.of(
                adventure("adventure-api", "551e536b9ea868f30e72c7900a309b35124ee7d4889fa3b3aed0910299751a26"),
                adventure("adventure-key", "eec172d63db77b40eb7abeeb25f65eedea89bd30264d057b68b12fecb731be5e"),
                RuntimeLibrary.maven("examination-api", new LibraryCoordinate("net.kyori", "examination-api", "1.3.0"), "c9237ffecb05428f6eff86216246ac70ce0b47b04c08ea7ca35020fde57f8492"),
                RuntimeLibrary.maven("examination-string", new LibraryCoordinate("net.kyori", "examination-string", "1.3.0"), "7d01fc25a4bb3af0e1662685455f4541fbf4626216ea5846e455c1491e156b8c"),
                adventure("adventure-nbt", "f26ef6bff5fcdd817902b4dfd353561d98cb131b4ecb67341ff00ef2ed1336f8"),
                adventure("adventure-text-minimessage", "1d43451e9af473252dc8af3e8084238d5ce68ad43af0e3b7383eb3d4b63fff9f"),
                adventure("adventure-text-serializer-plain", "39b9bfe5790f645605ff5464382c0e062476f48f9bfb2548801f275f12faf405"),
                adventure("adventure-text-serializer-legacy", "721107bc213572454df1bfbe438dba630e30457550c750b7a154b35dc3264aa8"),
                adventure("adventure-text-serializer-gson", "e4a908dedc4acb4305083d916d362ddc2b21ecf452d577a0be665280bddae9fc"),
                adventure("adventure-text-serializer-gson-legacy-impl", "6eab1f13b59957ecdf9c5573c6791a14da53d0286fc4351b584b415946c8f80c"),
                adventure("adventure-text-serializer-json", "55c64b4333d5d2968a0125b8f29dae7cfba15392d5f99f78dbaea711cb0d4dc2"),
                adventure("adventure-text-serializer-json-legacy-impl", "cbe618e2ec55a4e77955afdeb72a2ff01dc309790c9562ae4c539bf30fe58b91"),
                adventure("adventure-text-serializer-commons", "66527fef559da4d91cda692491034f903d5b01e835de1b091edfbb6b51cee5a2"),
                RuntimeLibrary.maven("option", new LibraryCoordinate("net.kyori", "option", "1.1.0"), "97b69b4b17dfe02217c9131ad342564cbc9aebd04c75eb689639b5f78fd4b11c"),

                RuntimeLibrary.maven("gson", new LibraryCoordinate("com.google.code.gson", "gson", "2.11.0"), "57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b"),
                RuntimeLibrary.maven("boosted-yaml", new LibraryCoordinate("dev.dejvokep", "boosted-yaml", "1.3.8"), "17ee775046240f7d83ba0de69f79aaf20ad2ee2506928f160bc4407ba550c3af"),
                RuntimeLibrary.maven("exp4j", new LibraryCoordinate("net.objecthunter", "exp4j", "0.4.8"), "271f7824ee8a3468257bc0613afdabb67597af8389317643fa806b983b7ecb27"),
                RuntimeLibrary.maven("caffeine", new LibraryCoordinate("com.github.ben-manes.caffeine", "caffeine", "3.3.1"), "1f916d052a3f8c0c10951f343f043f176ff2d3c2f9e380e15f0be673c5e67c48"),

                RuntimeLibrary.maven("polyglot", new LibraryCoordinate("org.graalvm.polyglot", "polyglot", GRAALVM_VERSION), "cbacc2628fd5f99dbcebff7d398bcddde43ea15ebadc873d1da4454c718727d8"),
                RuntimeLibrary.maven("js-language", new LibraryCoordinate("org.graalvm.js", "js-language", GRAALVM_VERSION), "50a91172497d9b94b83210af2e95472ba3bd39e6b5606c9e7b07f3de0022fa93"),
                RuntimeLibrary.maven("truffle-api", new LibraryCoordinate("org.graalvm.truffle", "truffle-api", GRAALVM_VERSION), "9dcc81b6890d6fcc9ab57d17bc4430335f071d82b108c0af8556a28381978da6"),
                RuntimeLibrary.maven("truffle-runtime", new LibraryCoordinate("org.graalvm.truffle", "truffle-runtime", GRAALVM_VERSION), "25818863ef8e50fc6329a876a033055c7418f20bedb051cfbb31c3d895fad7c9"),
                RuntimeLibrary.maven("regex", new LibraryCoordinate("org.graalvm.regex", "regex", GRAALVM_VERSION), "5b43366990bdd438c7dbd2ba0b3dba211acfaf67054275d0717bd0b8ea1d8dc9"),
                RuntimeLibrary.maven("icu4j", new LibraryCoordinate("org.graalvm.shadowed", "icu4j", GRAALVM_VERSION), "02f9e8a76ed2f8fa80d53054ca4632c7058354010df27c8f4e7e7443543f7e1e")
        );
    }

    private static RuntimeLibrary adventure(String artifactId, String expectedSha256) {
        return RuntimeLibrary.maven(artifactId, new LibraryCoordinate("net.kyori", artifactId, ADVENTURE_VERSION), expectedSha256);
    }

    private boolean prepareLibrary(RuntimeLibrary library, Path localFile, String preferredRepo, String fallbackRepo) {
        if (validCached(localFile, library.expectedSha256())) {
            info(NamedTextColor.GREEN, library + " 已就绪(缓存, " + formatSize(sizeKb(localFile)) + ")");
            return true;
        }
        deleteIfExists(localFile);
        info(NamedTextColor.YELLOW, "正在下载 " + library + "...");
        boolean success = downloadLibrary(library.coordinate(), localFile, preferredRepo);
        if (!success) {
            success = downloadLibrary(library.coordinate(), localFile, fallbackRepo);
        }
        if (success && validCached(localFile, library.expectedSha256())) {
            info(NamedTextColor.GREEN, library + " 下载完成(" + formatSize(sizeKb(localFile)) + ")");
            return true;
        }
        deleteIfExists(localFile);
        return false;
    }

    private Path prepareWorldMountBridge() {
        LibraryCoordinate coordinate = resolveWorldMountCoordinate();
        String repository = coordinate.version().endsWith("SNAPSHOT") ? REPOSILITE_SNAPSHOTS_REPO : REPOSILITE_RELEASES_REPO;
        String downloadUrl = artifactUrl(repository, coordinate);
        info(NamedTextColor.GRAY, "正在准备副本世界桥 " + coordinate + " ...");
        String expectedSha256 = fetchWorldMountChecksum(downloadUrl + ".sha256", coordinate, repository);
        Path localFile = resolveLocalPath(coordinate);
        if (validCached(localFile, expectedSha256)) {
            info(NamedTextColor.GREEN, "副本世界桥 " + coordinate + " 已就绪(缓存, " + formatSize(sizeKb(localFile)) + ")");
            return localFile;
        }
        deleteIfExists(localFile);
        downloadWorldMountJar(downloadUrl, localFile, coordinate, repository);
        if (!validCached(localFile, expectedSha256)) {
            deleteIfExists(localFile);
            throw new IllegalStateException("副本世界桥校验失败: " + coordinate + " @ " + repository + ", SHA-256 不匹配或产物不可读, URL " + downloadUrl);
        }
        info(NamedTextColor.GREEN, "副本世界桥 " + coordinate + " 下载完成(" + formatSize(sizeKb(localFile)) + ")");
        return localFile;
    }

    private LibraryCoordinate resolveWorldMountCoordinate() {
        String resolvedVersion = null;
        List<String> probeSummary = new ArrayList<>();
        for (String repository : List.of(REPOSILITE_RELEASES_REPO, REPOSILITE_SNAPSHOTS_REPO)) {
            String metadataUrl = repository + "/" + WORLD_MOUNT_GROUP_ID.replace('.', '/') + "/" + WORLD_MOUNT_ARTIFACT_ID + "/maven-metadata.xml";
            MetadataProbe probe = fetchWorldMountLatestVersion(metadataUrl, repository);
            probeSummary.add(repository + " HTTP " + probe.status());
            if (probe.version() != null && (resolvedVersion == null || compareVersions(probe.version(), resolvedVersion) > 0)) {
                resolvedVersion = probe.version();
            }
        }
        if (resolvedVersion == null) {
            throw new IllegalStateException("副本世界桥版本解析失败: " + WORLD_MOUNT_DESCRIPTOR
                    + " 无可用的 maven-metadata.xml, " + String.join(", ", probeSummary));
        }
        if (compareVersions(resolvedVersion, WORLD_MOUNT_MIN_VERSION) < 0) {
            throw new IllegalStateException("副本世界桥版本过低: 仓库 latest " + resolvedVersion + " 低于最低要求 " + WORLD_MOUNT_MIN_VERSION + " (" + WORLD_MOUNT_DESCRIPTOR + ")");
        }
        return new LibraryCoordinate(WORLD_MOUNT_GROUP_ID, WORLD_MOUNT_ARTIFACT_ID, resolvedVersion);
    }

    private MetadataProbe fetchWorldMountLatestVersion(String metadataUrl, String repository) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(metadataUrl);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            if (status != 200) {
                return new MetadataProbe(null, status);
            }
            String metadata;
            try (InputStream inputStream = connection.getInputStream()) {
                metadata = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            Matcher matcher = LATEST_VERSION.matcher(metadata);
            return new MetadataProbe(matcher.find() ? matcher.group(1).trim() : null, status);
        } catch (IOException unavailable) {
            return new MetadataProbe(null, -1);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String fetchWorldMountChecksum(String checksumUrl, LibraryCoordinate coordinate, String repository) {
        byte[] checksumBytes = fetchWorldMountOrFail(checksumUrl, coordinate, repository, "校验和");
        String checksum = new String(checksumBytes, StandardCharsets.UTF_8).trim();
        if (!SHA256_HEX.matcher(checksum).matches()) {
            throw new IllegalStateException("副本世界桥校验和不可用: " + coordinate + " @ " + repository + ", URL " + checksumUrl);
        }
        return checksum.toLowerCase(Locale.ROOT);
    }

    private void downloadWorldMountJar(String downloadUrl, Path localFile, LibraryCoordinate coordinate, String repository) {
        byte[] jarBytes = fetchWorldMountOrFail(downloadUrl, coordinate, repository, "Jar");
        try {
            Files.createDirectories(localFile.getParent());
            Files.write(localFile, jarBytes);
        } catch (IOException writeFailure) {
            deleteIfExists(localFile);
            throw new IllegalStateException("副本世界桥写入失败: " + coordinate + " @ " + repository + ", 路径 " + localFile, writeFailure);
        }
    }

    private byte[] fetchWorldMountOrFail(String url, LibraryCoordinate coordinate, String repository, String payload) {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(url);
            connection.setRequestMethod("GET");
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IllegalStateException("副本世界桥" + payload + "获取失败: " + coordinate + " @ " + repository + ", HTTP " + status + ", URL " + url);
            }
            try (InputStream inputStream = connection.getInputStream()) {
                return inputStream.readAllBytes();
            }
        } catch (IOException networkFailure) {
            throw new IllegalStateException("副本世界桥" + payload + "获取失败: " + coordinate + " @ " + repository + ", URL " + url, networkFailure);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String artifactUrl(String repository, LibraryCoordinate coordinate) {
        return repository + "/" + coordinate.groupId().replace('.', '/')
                + "/" + coordinate.artifactId()
                + "/" + coordinate.version()
                + "/" + coordinate.artifactId() + "-" + coordinate.version() + ".jar";
    }

    private static int compareVersions(String left, String right) {
        String[] leftSegments = left.split("[.-]");
        String[] rightSegments = right.split("[.-]");
        int shared = Math.min(leftSegments.length, rightSegments.length);
        for (int index = 0; index < shared; index++) {
            int comparison = compareVersionSegment(leftSegments[index], rightSegments[index]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(leftSegments.length, rightSegments.length);
    }

    private static int compareVersionSegment(String left, String right) {
        try {
            return Long.compare(Long.parseLong(left), Long.parseLong(right));
        } catch (NumberFormatException notNumeric) {
            return left.compareTo(right);
        }
    }

    private static boolean isFoliaRuntime() {
        return typePresent(FOLIA_REGIONIZED_SERVER) || createWorldIsStubbed();
    }

    private static boolean typePresent(String typeName) {
        try {
            Class.forName(typeName);
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    private static boolean createWorldIsStubbed() {
        try {
            if (Bukkit.getGlobalRegionScheduler() == null) {
                return false;
            }
            Class<?> craftServer = Class.forName(CRAFT_SERVER);
            Method createWorld = craftServer.getMethod("createWorld", WorldCreator.class);
            createWorld.invoke(Bukkit.getServer(), new Object[]{null});
            return false;
        } catch (InvocationTargetException rejected) {
            return rejected.getCause() instanceof UnsupportedOperationException;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError probeFailure) {
            return false;
        }
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

    private HttpURLConnection openConnection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "EmakiCoreLib-LibraryLoader");
        return connection;
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
            Files.move(tempFile, localFile, StandardCopyOption.REPLACE_EXISTING);
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

    private Path resolveLocalPath(LibraryCoordinate coordinate) {
        return cacheDirectory
                .resolve(coordinate.groupId().replace('.', '/'))
                .resolve(coordinate.artifactId())
                .resolve(coordinate.version())
                .resolve(coordinate.artifactId() + "-" + coordinate.version() + ".jar");
    }

    private boolean validCached(Path path, String expectedSha256) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            if (Files.size(path) <= 0L) {
                return false;
            }
            try (JarFile ignored = new JarFile(path.toFile())) {
                return matchesChecksum(path, expectedSha256);
            }
        } catch (IOException | SecurityException exception) {
            debug("运行库缓存损坏: " + path, exception);
            return false;
        }
    }

    private boolean matchesChecksum(Path path, String expectedSha256) {
        if (expectedSha256 == null || expectedSha256.isEmpty()) {
            return false;
        }
        try {
            return sha256Hex(path).equalsIgnoreCase(expectedSha256);
        } catch (IOException | NoSuchAlgorithmException checksumFailure) {
            debug("运行库校验和计算失败: " + path, checksumFailure);
            return false;
        }
    }

    private String sha256Hex(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
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

    private String formatSize(long sizeKb) {
        if (sizeKb < 1024) {
            return sizeKb + "KB";
        }
        return String.format(Locale.ROOT, "%.1fMB", sizeKb / 1024.0);
    }

    private record ProbeResult(String name, boolean reachable, long latencyMs) {
    }

    private record MetadataProbe(String version, int status) {
    }

    private record RuntimeLibrary(String id, LibraryCoordinate coordinate, String expectedSha256) {

        static RuntimeLibrary maven(String id, LibraryCoordinate coordinate, String expectedSha256) {
            return new RuntimeLibrary(id, coordinate, expectedSha256);
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
