package emaki.jiuwu.craft.corelib.loader;

import java.nio.file.Path;

import emaki.jiuwu.craft.corelib.library.RuntimeLibraryLoader;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;

/**
 * Paper plugin loader for EmakiCoreLib.
 *
 * <p>Paper prepares the plugin data directory before the plugin instance is
 * created. CoreLib uses that loading phase to download or reuse its runtime
 * libraries under {@code plugins/EmakiCoreLib/libraries}, then adds every
 * verified local jar through Paper's supported {@link JarLibrary} mechanism.</p>
 *
 * <p>This keeps the runtime cache private to CoreLib and uses Paper's supported
 * classpath assembly path for the plugin classloader.</p>
 */
public final class EmakiCoreLibPluginLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpathBuilder) {
        var context = classpathBuilder.getContext();
        Path dataDirectory = context.getDataDirectory();
        for (Path library : new RuntimeLibraryLoader(dataDirectory, context.getLogger()).prepare()) {
            classpathBuilder.addLibrary(new JarLibrary(library));
        }
    }
}
