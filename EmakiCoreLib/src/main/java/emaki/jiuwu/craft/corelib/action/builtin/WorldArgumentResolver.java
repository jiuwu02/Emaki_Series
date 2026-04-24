package emaki.jiuwu.craft.corelib.action.builtin;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import emaki.jiuwu.craft.corelib.text.Texts;

final class WorldArgumentResolver {

    private WorldArgumentResolver() {
    }

    static World resolve(String requestedWorld, World fallback) {
        String candidate = Texts.toStringSafe(requestedWorld).trim();
        if (candidate.isEmpty()) {
            return fallback;
        }
        World direct = Bukkit.getWorld(candidate);
        if (direct != null) {
            return direct;
        }
        for (World world : Bukkit.getWorlds()) {
            if (matches(world, candidate)) {
                return world;
            }
        }
        return null;
    }

    private static boolean matches(World world, String candidate) {
        if (world == null || Texts.isBlank(candidate)) {
            return false;
        }
        if (candidate.equalsIgnoreCase(world.getName())) {
            return true;
        }
        NamespacedKey key = world.getKey();
        if (key == null) {
            return false;
        }
        if (candidate.equalsIgnoreCase(key.toString()) || candidate.equalsIgnoreCase(key.getKey())) {
            return true;
        }
        int separator = candidate.indexOf(':');
        if (separator <= 0 || separator + 1 >= candidate.length()) {
            return false;
        }
        String keyPath = candidate.substring(separator + 1);
        return keyPath.equalsIgnoreCase(world.getName()) || keyPath.equalsIgnoreCase(key.getKey());
    }
}
