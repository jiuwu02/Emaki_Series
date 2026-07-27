package emaki.jiuwu.craft.corelib.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import emaki.jiuwu.craft.corelib.api.CompatibilityReport;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;




public final class PlatformCapabilities {

    public static final String MINIMUM_MINECRAFT_VERSION = "1.21.8";
    public static final String VERIFIED_MAXIMUM_EXCLUSIVE = "1.22.0";
    public static final int MINIMUM_JAVA_FEATURE = 25;

    private static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";
    private static final String PAPER_MARKER = "io.papermc.paper.configuration.Configuration";

    private final Server server;
    private final ClassLoader classLoader;
    private final boolean folia;
    private final boolean paper;
    private final boolean nativeExecutionSchedulers;
    private final boolean nativeOwnershipChecks;

    private PlatformCapabilities(Server server,
            ClassLoader classLoader,
            boolean folia,
            boolean paper,
            boolean nativeExecutionSchedulers,
            boolean nativeOwnershipChecks) {
        this.server = server;
        this.classLoader = classLoader == null ? PlatformCapabilities.class.getClassLoader() : classLoader;
        this.folia = folia;
        this.paper = paper;
        this.nativeExecutionSchedulers = nativeExecutionSchedulers;
        this.nativeOwnershipChecks = nativeOwnershipChecks;
    }

    public static PlatformCapabilities detect(Server server) {
        ClassLoader loader = server == null || server.getClass().getClassLoader() == null
                ? PlatformCapabilities.class.getClassLoader()
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
        return new PlatformCapabilities(server, loader, folia, paper, executionSchedulers, ownershipChecks);
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

    public CompatibilityReport compatibilityReport(String coreLibVersion) {
        String minecraftVersion = minecraftVersion();
        int javaFeature = Runtime.version().feature();
        String platform = folia ? "FOLIA" : paper ? "PAPER" : "UNSUPPORTED";
        List<CompatibilityReport.Issue> issues = new ArrayList<>();
        if (javaFeature < MINIMUM_JAVA_FEATURE) {
            issues.add(new CompatibilityReport.Issue(
                    CompatibilityReport.Severity.ERROR,
                    "JAVA_TOO_OLD",
                    "Java " + MINIMUM_JAVA_FEATURE + " or newer is required; detected " + javaFeature + "."));
        }
        if (!paper) {
            issues.add(new CompatibilityReport.Issue(
                    CompatibilityReport.Severity.ERROR,
                    "PAPER_REQUIRED",
                    "Paper or Folia runtime capabilities are required."));
        }
        if (folia && !foliaBackendReady()) {
            issues.add(new CompatibilityReport.Issue(
                    CompatibilityReport.Severity.ERROR,
                    "FOLIA_CAPABILITIES_MISSING",
                    "Folia was detected but required scheduler or ownership capabilities are unavailable."));
        }
        boolean minecraftKnown = minecraftVersion != null && !minecraftVersion.isBlank();
        if (!minecraftKnown) {
            issues.add(new CompatibilityReport.Issue(
                    CompatibilityReport.Severity.WARNING,
                    "MINECRAFT_VERSION_UNKNOWN",
                    "The Minecraft server version could not be determined; the runtime is not verified."));
        } else if (compareVersions(minecraftVersion, MINIMUM_MINECRAFT_VERSION) < 0) {
            issues.add(new CompatibilityReport.Issue(
                    CompatibilityReport.Severity.WARNING,
                    "MINECRAFT_TOO_OLD",
                    "Minecraft " + minecraftVersion + " is below the verified baseline "
                            + MINIMUM_MINECRAFT_VERSION + "."));
        } else if (compareVersions(minecraftVersion, VERIFIED_MAXIMUM_EXCLUSIVE) >= 0) {
            issues.add(new CompatibilityReport.Issue(
                    CompatibilityReport.Severity.WARNING,
                    "MINECRAFT_NEWER_UNVERIFIED",
                    "Minecraft " + minecraftVersion + " is newer than the verified range below "
                            + VERIFIED_MAXIMUM_EXCLUSIVE + "."));
        }
        boolean compatible = issues.stream().noneMatch(issue -> issue.severity() == CompatibilityReport.Severity.ERROR);
        boolean verified = compatible && minecraftKnown
                && compareVersions(minecraftVersion, MINIMUM_MINECRAFT_VERSION) >= 0
                && compareVersions(minecraftVersion, VERIFIED_MAXIMUM_EXCLUSIVE) < 0;
        return new CompatibilityReport(
                compatible,
                verified,
                platform,
                minecraftVersion,
                System.getProperty("java.version", ""),
                javaFeature,
                coreLibVersion,
                MINIMUM_MINECRAFT_VERSION,
                VERIFIED_MAXIMUM_EXCLUSIVE,
                MINIMUM_JAVA_FEATURE,
                issues
        );
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

    private String minecraftVersion() {
        if (server == null) {
            return "";
        }
        try {
            return Objects.requireNonNullElse(server.getMinecraftVersion(), "").trim();
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static int compareVersions(String left, String right) {
        int[] leftParts = numericVersionParts(left);
        int[] rightParts = numericVersionParts(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftPart = index < leftParts.length ? leftParts[index] : 0;
            int rightPart = index < rightParts.length ? rightParts[index] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }

    private static int[] numericVersionParts(String value) {
        if (value == null || value.isBlank()) {
            return new int[0];
        }
        String[] tokens = value.trim().split("[^0-9]+");
        int[] parts = new int[tokens.length];
        int count = 0;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            try {
                parts[count++] = Integer.parseInt(token);
            } catch (NumberFormatException ignored) {
                parts[count++] = 0;
            }
        }
        if (count == parts.length) {
            return parts;
        }
        int[] compact = new int[count];
        System.arraycopy(parts, 0, compact, 0, count);
        return compact;
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
