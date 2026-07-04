package emaki.jiuwu.craft.corelib.loader;

import java.util.List;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
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
 * {@link MavenLibraryResolver}. bStats is shaded and relocated directly into
 * CoreLib's own jar (see the {@code maven-shade-plugin} relocation of
 * {@code org.bstats} to {@code emaki.jiuwu.craft.runtime.bstats}), so it needs
 * no loader handling here.</p>
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
    }

    /**
     * Maven coordinates for every downloadable runtime library, mirroring the
     * legacy {@code RuntimeLibraryLoader.libraries()} list. bStats is not listed
     * here because it is shaded/relocated into CoreLib's own jar.
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
}
