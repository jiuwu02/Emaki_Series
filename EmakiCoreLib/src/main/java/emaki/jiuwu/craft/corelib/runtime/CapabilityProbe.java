package emaki.jiuwu.craft.corelib.runtime;

import java.util.Locale;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;




public final class CapabilityProbe {

    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";
    private static final String PAPER_MARKER = "io.papermc.paper.configuration.Configuration";

    private final Server server;
    private final ClassLoader classLoader;
    private final boolean folia;
    private final boolean paper;
    private final boolean nativeExecutionSchedulers;
    private final boolean nativeOwnershipChecks;

    private CapabilityProbe(Server server,
            ClassLoader classLoader,
            boolean folia,
            boolean paper,
            boolean nativeExecutionSchedulers,
            boolean nativeOwnershipChecks) {
        this.server = server;
        this.classLoader = classLoader == null ? CapabilityProbe.class.getClassLoader() : classLoader;
        this.folia = folia;
        this.paper = paper;
        this.nativeExecutionSchedulers = nativeExecutionSchedulers;
        this.nativeOwnershipChecks = nativeOwnershipChecks;
    }

    public static CapabilityProbe detect(Server server) {
        ClassLoader loader = server == null || server.getClass().getClassLoader() == null
                ? CapabilityProbe.class.getClassLoader()
                : server.getClass().getClassLoader();
        boolean folia = classAvailable(FOLIA_MARKER, loader);
        boolean paper = folia || classAvailable(PAPER_MARKER, loader) || serverNameContains(server, "paper");
        boolean executionSchedulers = folia
                && hasMethod(server == null ? null : server.getClass(), "getGlobalRegionScheduler")
                && hasMethod(server == null ? null : server.getClass(), "getRegionScheduler")
                && hasMethod(server == null ? null : server.getClass(), "getAsyncScheduler")
                && hasMethod(Entity.class, "getScheduler");
        boolean ownershipChecks = folia
                && hasMethod(Bukkit.class, "isGlobalTickThread")
                && hasMethod(Bukkit.class, "isOwnedByCurrentRegion", Entity.class)
                && hasMethod(Bukkit.class, "isOwnedByCurrentRegion", Location.class);
        return new CapabilityProbe(server, loader, folia, paper, executionSchedulers, ownershipChecks);
    }

    static CapabilityProbe fixed(Server server, ClassLoader classLoader, boolean folia, boolean paper) {
        return new CapabilityProbe(server, classLoader, folia, paper, folia, folia);
    }

    public boolean folia() {
        return folia;
    }

    public boolean paper() {
        return paper;
    }

    public boolean nativeExecutionSchedulers() {
        return nativeExecutionSchedulers;
    }

    public boolean nativeOwnershipChecks() {
        return nativeOwnershipChecks;
    }

    public boolean foliaBackendReady() {
        return folia && nativeExecutionSchedulers && nativeOwnershipChecks;
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

    private static boolean hasMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        if (type == null || methodName == null || methodName.isBlank()) {
            return false;
        }
        try {
            type.getMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException | LinkageError | SecurityException ignored) {
            return false;
        }
    }

    private static boolean serverNameContains(Server server, String token) {
        if (server == null || token == null) {
            return false;
        }
        try {
            return server.getName().toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
