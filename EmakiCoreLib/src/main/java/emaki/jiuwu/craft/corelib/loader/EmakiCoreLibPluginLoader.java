package emaki.jiuwu.craft.corelib.loader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * Paper plugin loader for EmakiCoreLib.
 *
 * <p>Under {@code paper-plugin.yml} the plugin classloader is no longer a
 * {@link java.net.URLClassLoader}, so CoreLib's legacy
 * {@code RuntimeLibraryLoader} (which injects jars through an Unsafe-obtained
 * {@code URLClassLoader#addURL}) silently fails. Paper's supported replacement
 * is a {@link PluginLoader} that declares the required runtime libraries here,
 * so Paper resolves them into the plugin's classpath before the main class is
 * instantiated.</p>
 *
 * <p>All downloadable third-party runtime libraries (Adventure serializers,
 * exp4j, Caffeine, GraalJS/Truffle) are declared through
 * {@link MavenLibraryResolver}. The bStats runtime jar is a locally-relocated
 * artifact that is bundled inside CoreLib's own jar (never published to a
 * remote Maven repository), so it is extracted from the plugin jar and added
 * through {@link JarLibrary}, keeping the existing "single relocated bStats
 * artifact" architecture untouched.</p>
 */
public final class EmakiCoreLibPluginLoader implements PluginLoader {

    private static final String ALIYUN_REPO = "https://maven.aliyun.com/repository/central";

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();
        // Aliyun mirror first (matches the legacy loader's preferred repository),
        // then Paper's Maven Central mirror as fallback.
        resolver.addRepository(new RemoteRepository.Builder("aliyun", "default", ALIYUN_REPO).build());
        resolver.addRepository(new RemoteRepository.Builder(
                "central", "default", MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR).build());

        for (String coordinate : mavenCoordinates()) {
            resolver.addDependency(new Dependency(new DefaultArtifact(coordinate), null));
        }
        classpathBuilder.addLibrary(resolver);

        Path bstatsJar = extractBundledBStatsRuntime(classpathBuilder);
        if (bstatsJar != null) {
            classpathBuilder.addLibrary(new JarLibrary(bstatsJar));
        }
    }

    /**
     * Maven coordinates for every downloadable runtime library, mirroring the
     * legacy {@code RuntimeLibraryLoader.libraries()} list (bStats runtime is
     * handled separately as a bundled jar).
     */
    private List<String> mavenCoordinates() {
        return List.of(
                "net.kyori:adventure-api:4.26.1",
                "net.kyori:adventure-key:4.26.1",
                "net.kyori:examination-api:1.3.0",
                "net.kyori:examination-string:1.3.0",
                "net.kyori:adventure-nbt:4.21.0",
                "net.kyori:adventure-text-minimessage:4.26.1",
                "net.kyori:adventure-text-serializer-plain:4.26.1",
                "net.kyori:adventure-text-serializer-legacy:4.26.1",
                "net.kyori:adventure-text-serializer-gson:4.21.0",
                "net.kyori:adventure-text-serializer-gson-legacy-impl:4.21.0",
                "net.kyori:adventure-text-serializer-json:4.21.0",
                "net.kyori:adventure-text-serializer-json-legacy-impl:4.21.0",
                "net.kyori:adventure-text-serializer-commons:4.21.0",
                "net.kyori:option:1.1.0",
                "com.google.code.gson:gson:2.8.0",
                "dev.dejvokep:boosted-yaml:1.3.7",
                "net.objecthunter:exp4j:0.4.8",
                "com.github.ben-manes.caffeine:caffeine:3.2.4",
                "org.graalvm.polyglot:polyglot:25.0.3",
                "org.graalvm.sdk:collections:25.0.3",
                "org.graalvm.sdk:nativeimage:25.0.3",
                "org.graalvm.sdk:word:25.0.3",
                "org.graalvm.js:js-language:25.0.3",
                "org.graalvm.regex:regex:25.0.3",
                "org.graalvm.truffle:truffle-api:25.0.3",
                "org.graalvm.shadowed:icu4j:25.0.3",
                "org.graalvm.shadowed:xz:25.0.3"
        );
    }

    /**
     * Extracts the relocated bStats runtime jar bundled inside CoreLib's own jar
     * to the plugin data directory so it can be added as a {@link JarLibrary}.
     *
     * @return the extracted jar path, or {@code null} if extraction fails; a
     *         missing bStats runtime must never break startup.
     */
    private Path extractBundledBStatsRuntime(PluginClasspathBuilder classpathBuilder) {
        var context = classpathBuilder.getContext();
        String version = context.getConfiguration().getVersion();
        String internalPath = "runtime-libraries/emaki/jiuwu/craft/emaki-bstats-runtime/"
                + version + "/emaki-bstats-runtime-" + version + ".jar";
        Path pluginJar = context.getPluginSource();
        Path targetDir = context.getDataDirectory().resolve("libraries");
        Path targetJar = targetDir.resolve("emaki-bstats-runtime-" + version + ".jar");
        try {
            if (Files.isRegularFile(targetJar) && Files.size(targetJar) > 0L) {
                return targetJar;
            }
            Files.createDirectories(targetDir);
            try (var jarFs = java.nio.file.FileSystems.newFileSystem(pluginJar)) {
                Path source = jarFs.getPath(internalPath);
                if (!Files.isRegularFile(source)) {
                    context.getLogger().warn("[LibraryLoader] 随包 bStats runtime 不存在: {}", internalPath);
                    return null;
                }
                Files.copy(source, targetJar, StandardCopyOption.REPLACE_EXISTING);
            }
            return targetJar;
        } catch (Exception exception) {
            context.getLogger().warn("[LibraryLoader] bStats runtime 释放失败: {}", exception.getMessage());
            return null;
        }
    }
}
