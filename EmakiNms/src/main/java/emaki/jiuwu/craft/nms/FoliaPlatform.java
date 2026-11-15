package emaki.jiuwu.craft.nms;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;

final class FoliaPlatform {

    private static final String REGIONIZED_SERVER = "io.papermc.paper.threadedregions.RegionizedServer";
    private static final String CRAFT_SERVER = "org.bukkit.craftbukkit.CraftServer";

    private FoliaPlatform() {
    }

    static boolean isFolia() {
        return NmsAccess.typeOrNull(REGIONIZED_SERVER) != null || createWorldIsStubbed();
    }

    static String serverVersion() {
        return Bukkit.getBukkitVersion();
    }

    static String serverVersionLine() {
        String[] segments = serverVersion().split("[.\\-]");
        return segments.length < 2 ? serverVersion() : segments[0] + "." + segments[1];
    }

    private static boolean createWorldIsStubbed() {
        try {
            if (Bukkit.getGlobalRegionScheduler() == null) {
                return false;
            }
            Class<?> craftServer = NmsAccess.requireType(CRAFT_SERVER);
            Method createWorld = craftServer.getMethod("createWorld", WorldCreator.class);
            createWorld.invoke(Bukkit.getServer(), new Object[]{null});
            return false;
        } catch (InvocationTargetException rejected) {
            return rejected.getCause() instanceof UnsupportedOperationException;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError probeFailure) {
            return false;
        }
    }
}
