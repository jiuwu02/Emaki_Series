package emaki.jiuwu.craft.corelib.script.exports;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.NotNull;

public final class BukkitPlayerExport {

    private final String name;
    private final String uniqueId;
    private final double health;
    private final double maxHealth;
    private final int foodLevel;
    private final int level;
    private final float exp;
    private final boolean online;
    private final boolean sneaking;
    private final boolean sprinting;
    private final boolean flying;
    private final boolean allowFlight;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final List<Consumer<Player>> patches = new CopyOnWriteArrayList<>();

    public BukkitPlayerExport(@NotNull Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player 不能为 null");
        }
        Location location = player.getLocation();
        this.name = player.getName();
        this.uniqueId = player.getUniqueId().toString();
        this.health = player.getHealth();
        this.maxHealth = player.getMaxHealth();
        this.foodLevel = player.getFoodLevel();
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.online = player.isOnline();
        this.sneaking = player.isSneaking();
        this.sprinting = player.isSprinting();
        this.flying = player.isFlying();
        this.allowFlight = player.getAllowFlight();
        this.worldName = player.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
    }

    @HostAccess.Export
    public @NotNull String getName() {
        return name;
    }

    @HostAccess.Export
    public @NotNull String getUniqueId() {
        return uniqueId;
    }

    @HostAccess.Export
    public double getHealth() {
        return health;
    }

    @HostAccess.Export
    public void setHealth(double health) {
        patches.add(target -> target.setHealth(Math.max(0, Math.min(health, target.getMaxHealth()))));
    }

    @HostAccess.Export
    public double getMaxHealth() {
        return maxHealth;
    }

    @HostAccess.Export
    public int getFoodLevel() {
        return foodLevel;
    }

    @HostAccess.Export
    public void setFoodLevel(int level) {
        patches.add(target -> target.setFoodLevel(Math.max(0, Math.min(level, 20))));
    }

    @HostAccess.Export
    public int getLevel() {
        return level;
    }

    @HostAccess.Export
    public void setLevel(int level) {
        patches.add(target -> target.setLevel(Math.max(0, level)));
    }

    @HostAccess.Export
    public float getExp() {
        return exp;
    }

    @HostAccess.Export
    public void setExp(float exp) {
        patches.add(target -> target.setExp(Math.max(0.0f, Math.min(exp, 1.0f))));
    }

    @HostAccess.Export
    public boolean isOnline() {
        return online;
    }

    @HostAccess.Export
    public boolean isSneaking() {
        return sneaking;
    }

    @HostAccess.Export
    public boolean isSprinting() {
        return sprinting;
    }

    @HostAccess.Export
    public boolean isFlying() {
        return flying;
    }

    @HostAccess.Export
    public boolean getAllowFlight() {
        return allowFlight;
    }

    @HostAccess.Export
    public @NotNull String getWorldName() {
        return worldName;
    }

    @HostAccess.Export
    public double getX() {
        return x;
    }

    @HostAccess.Export
    public double getY() {
        return y;
    }

    @HostAccess.Export
    public double getZ() {
        return z;
    }

    public boolean hasPatches() {
        return !patches.isEmpty();
    }

    public void applyPatches(Player player) {
        if (player == null) {
            return;
        }
        for (Consumer<Player> patch : patches) {
            patch.accept(player);
        }
        patches.clear();
    }

    @Override
    public String toString() {
        return "BukkitPlayerExport{name=" + name + ", uuid=" + uniqueId + "}";
    }
}
