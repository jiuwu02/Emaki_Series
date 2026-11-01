package emaki.jiuwu.craft.corelib.script.bridge;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ScriptLocationExport {

    private final Location location;

    public ScriptLocationExport(@NotNull Location location) {
        this.location = location;
    }

    @Nullable
    public String getWorldName() {
        return location.getWorld() == null ? null : location.getWorld().getName();
    }

    public double getX() {
        return location.getX();
    }

    public double getY() {
        return location.getY();
    }

    public double getZ() {
        return location.getZ();
    }

    public int getBlockX() {
        return location.getBlockX();
    }

    public int getBlockY() {
        return location.getBlockY();
    }

    public int getBlockZ() {
        return location.getBlockZ();
    }

    public float getYaw() {
        return location.getYaw();
    }

    public float getPitch() {
        return location.getPitch();
    }

    @Override
    public String toString() {
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return "location[" + world + " " + location.getBlockX() + " "
                + location.getBlockY() + " " + location.getBlockZ() + "]";
    }
}
