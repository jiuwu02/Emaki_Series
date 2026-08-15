package emaki.jiuwu.craft.corelib.loader;

import java.nio.file.Path;

import emaki.jiuwu.craft.corelib.library.RuntimeLibraryLoader;
import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.JarLibrary;

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
