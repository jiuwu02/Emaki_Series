package emaki.jiuwu.craft.corelib.script.exports;

import org.bukkit.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.NotNull;

public final class BukkitPlayerExport {

    private final Player player;

    public BukkitPlayerExport(@NotNull Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        this.player = player;
    }

    @HostAccess.Export
    public @NotNull String getName() {
        return player.getName();
    }

    @HostAccess.Export
    public @NotNull String getUniqueId() {
        return player.getUniqueId().toString();
    }

    @HostAccess.Export
    public double getHealth() {
        return player.getHealth();
    }

    @HostAccess.Export
    public void setHealth(double health) {
        player.setHealth(Math.max(0, Math.min(health, player.getMaxHealth())));
    }

    @HostAccess.Export
    public double getMaxHealth() {
        return player.getMaxHealth();
    }

    @HostAccess.Export
    public int getFoodLevel() {
        return player.getFoodLevel();
    }

    @HostAccess.Export
    public void setFoodLevel(int level) {
        player.setFoodLevel(Math.max(0, Math.min(level, 20)));
    }

    @HostAccess.Export
    public int getLevel() {
        return player.getLevel();
    }

    @HostAccess.Export
    public void setLevel(int level) {
        player.setLevel(Math.max(0, level));
    }

    @HostAccess.Export
    public float getExp() {
        return player.getExp();
    }

    @HostAccess.Export
    public void setExp(float exp) {
        player.setExp(Math.max(0.0f, Math.min(exp, 1.0f)));
    }

    @HostAccess.Export
    public boolean isOnline() {
        return player.isOnline();
    }

    @HostAccess.Export
    public boolean isSneaking() {
        return player.isSneaking();
    }

    @HostAccess.Export
    public boolean isSprinting() {
        return player.isSprinting();
    }

    @HostAccess.Export
    public boolean isFlying() {
        return player.isFlying();
    }

    @HostAccess.Export
    public boolean getAllowFlight() {
        return player.getAllowFlight();
    }

    @HostAccess.Export
    public @NotNull String getWorldName() {
        return player.getWorld().getName();
    }

    @HostAccess.Export
    public double getX() {
        return player.getLocation().getX();
    }

    @HostAccess.Export
    public double getY() {
        return player.getLocation().getY();
    }

    @HostAccess.Export
    public double getZ() {
        return player.getLocation().getZ();
    }

    @Override
    public String toString() {
        return "BukkitPlayerExport{name=" + player.getName() + ", uuid=" + player.getUniqueId() + "}";
    }
}
