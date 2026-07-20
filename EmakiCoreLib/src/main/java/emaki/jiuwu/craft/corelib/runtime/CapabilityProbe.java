package emaki.jiuwu.craft.corelib.runtime;

import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import emaki.jiuwu.craft.corelib.execution.PlatformCapabilities;

/**
 * Fail-closed runtime capability probe. Platform facts are snapshotted while plugin enabled state remains dynamic.
 */
public final class CapabilityProbe {

    private final Server server;
    private final ClassLoader classLoader;
    private final boolean folia;
    private final boolean paper;

    private CapabilityProbe(Server server, ClassLoader classLoader, boolean folia, boolean paper) {
        this.server = server;
        this.classLoader = classLoader == null ? CapabilityProbe.class.getClassLoader() : classLoader;
        this.folia = folia;
        this.paper = paper;
    }

    public static CapabilityProbe detect(Server server) {
        PlatformCapabilities capabilities = PlatformCapabilities.detect(server);
        ClassLoader loader = server == null || server.getClass().getClassLoader() == null
                ? CapabilityProbe.class.getClassLoader()
                : server.getClass().getClassLoader();
        return new CapabilityProbe(server, loader, capabilities.folia(), capabilities.paper());
    }

    static CapabilityProbe fixed(Server server, ClassLoader classLoader, boolean folia, boolean paper) {
        return new CapabilityProbe(server, classLoader, folia, paper);
    }

    public boolean folia() {
        return folia;
    }

    public boolean paper() {
        return paper;
    }

    public boolean supports(ExecutionDomain domain) {
        Objects.requireNonNull(domain, "domain");
        return switch (domain) {
            case ASYNC_COMPUTE, PHYSICAL_FILE -> true;
            case SERVER_GLOBAL, LOCATION_REGION, ENTITY -> server != null;
        };
    }

    public boolean hasNativeOwnershipSchedulers() {
        return folia;
    }

    public boolean isClassAvailable(String className) {
        return classAvailable(className, classLoader);
    }

    public boolean isPluginPresent(String pluginName) {
        PluginManager pluginManager = pluginManager();
        if (pluginManager == null || pluginName == null || pluginName.isBlank()) {
            return false;
        }
        try {
            return pluginManager.getPlugin(pluginName) != null;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    public boolean isPluginEnabled(String pluginName) {
        PluginManager pluginManager = pluginManager();
        if (pluginManager == null || pluginName == null || pluginName.isBlank()) {
            return false;
        }
        try {
            Plugin plugin = pluginManager.getPlugin(pluginName);
            return plugin != null && plugin.isEnabled();
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private PluginManager pluginManager() {
        if (server == null) {
            return null;
        }
        try {
            return server.getPluginManager();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean classAvailable(String className, ClassLoader classLoader) {
        if (className == null || className.isBlank()) {
            return false;
        }
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return false;
        }
    }
}
