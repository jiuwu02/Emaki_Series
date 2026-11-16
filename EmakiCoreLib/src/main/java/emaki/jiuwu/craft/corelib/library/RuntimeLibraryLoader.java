package emaki.jiuwu.craft.corelib.library;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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
        boolean centralMissing = missing.stream().anyMatch(library -> library.repositories().isEmpty());
        String preferredRepo = centralMissing ? probePreferredRepository() : ALIYUN_REPO;
        String fallbackRepo = preferredRepo.equals(ALIYUN_REPO) ? CENTRAL_REPO : ALIYUN_REPO;

        List<Path> prepared = new ArrayList<>(libraries.size());
        List<RuntimeLibrary> failed = new ArrayList<>();
        int ready = 0;
        info(NamedTextColor.GRAY, "正在准备 CoreLib 运行库(共 " + libraries.size() + " 个)...");
        for (RuntimeLibrary library : libraries) {
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
                RuntimeLibrary.maven("icu4j", new LibraryCoordinate("org.graalvm.shadowed", "icu4j", GRAALVM_VERSION), "02f9e8a76ed2f8fa80d53054ca4632c7058354010df27c8f4e7e7443543f7e1e"),

                RuntimeLibrary.dedicated("emakinms", new LibraryCoordinate("emaki.jiuwu.craft", "emakinms", "1.0.0"), "f2ec5616c3e1b8241d9367a1d4b591af8e4ec41411d1c6c3d2ea6db66a33a00f", List.of(REPOSILITE_RELEASES_REPO, REPOSILITE_SNAPSHOTS_REPO))
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
        boolean success = false;
        for (String repository : library.repositories().isEmpty() ? List.of(preferredRepo, fallbackRepo) : library.repositories()) {
            if (downloadLibrary(library.coordinate(), localFile, repository)) {
                success = true;
                break;
            }
        }
        if (success && validCached(localFile, library.expectedSha256())) {
            info(NamedTextColor.GREEN, library + " 下载完成(" + formatSize(sizeKb(localFile)) + ")");
            return true;
        }
        deleteIfExists(localFile);
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
        return validCached(path, "SHA-256", expectedSha256);
    }

    private boolean validCached(Path path, String algorithm, String expected) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            if (Files.size(path) <= 0L) {
                return false;
            }
            try (JarFile ignored = new JarFile(path.toFile())) {
                return matchesChecksum(path, algorithm, expected);
            }
        } catch (IOException | SecurityException exception) {
            debug("运行库缓存损坏: " + path, exception);
            return false;
        }
    }

    private boolean matchesChecksum(Path path, String algorithm, String expected) {
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        try {
            return digestHex(path, algorithm).equalsIgnoreCase(expected);
        } catch (IOException | NoSuchAlgorithmException checksumFailure) {
            debug("运行库校验和计算失败: " + path, checksumFailure);
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

    private String formatSize(long sizeKb) {
        if (sizeKb < 1024) {
            return sizeKb + "KB";
        }
        return String.format(Locale.ROOT, "%.1fMB", sizeKb / 1024.0);
    }

    private record ProbeResult(String name, boolean reachable, long latencyMs) {
    }

    private record RuntimeLibrary(String id, LibraryCoordinate coordinate, String expectedSha256, List<String> repositories, boolean classpath) {

        static RuntimeLibrary maven(String id, LibraryCoordinate coordinate, String expectedSha256) {
            return new RuntimeLibrary(id, coordinate, expectedSha256, List.of(), true);
        }

        static RuntimeLibrary dedicated(String id, LibraryCoordinate coordinate, String expectedSha256, List<String> repositories) {
            return new RuntimeLibrary(id, coordinate, expectedSha256, repositories, false);
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
