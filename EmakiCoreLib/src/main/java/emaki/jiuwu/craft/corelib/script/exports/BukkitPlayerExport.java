package emaki.jiuwu.craft.corelib.script.exports;

import org.bukkit.entity.Player;
import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit {@link Player} 的 JavaScript 白名单导出。
 *
 * <p>该类只暴露标注 {@code @HostAccess.Export} 的方法，
 * 其余方法（如 {@code setOp}、{@code kickPlayer}）对脚本不可见。</p>
 *
 * <p>设计原则：只暴露读取状态和基础操作，不暴露权限提升、踢出、封禁等危险操作。</p>
 */
public final class BukkitPlayerExport {

    private final Player player;

    public BukkitPlayerExport(@NotNull Player player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }
        this.player = player;
    }

    /**
     * 获取玩家名称。
     *
     * @return 玩家名称
     */
    @HostAccess.Export
    public @NotNull String getName() {
        return player.getName();
    }

    /**
     * 获取玩家 UUID 字符串。
     *
     * @return UUID 字符串
     */
    @HostAccess.Export
    public @NotNull String getUniqueId() {
        return player.getUniqueId().toString();
    }

    /**
     * 获取玩家当前生命值。
     *
     * @return 生命值
     */
    @HostAccess.Export
    public double getHealth() {
        return player.getHealth();
    }

    /**
     * 设置玩家生命值。
     *
     * <p>值会被钳制在 [0, maxHealth] 范围内。</p>
     *
     * @param health 目标生命值
     */
    @HostAccess.Export
    public void setHealth(double health) {
        player.setHealth(Math.max(0, Math.min(health, player.getMaxHealth())));
    }

    /**
     * 获取玩家最大生命值。
     *
     * @return 最大生命值
     */
    @HostAccess.Export
    public double getMaxHealth() {
        return player.getMaxHealth();
    }

    /**
     * 获取玩家饱食度。
     *
     * @return 饱食度 (0-20)
     */
    @HostAccess.Export
    public int getFoodLevel() {
        return player.getFoodLevel();
    }

    /**
     * 设置玩家饱食度。
     *
     * <p>值会被钳制在 [0, 20] 范围内。</p>
     *
     * @param level 目标饱食度
     */
    @HostAccess.Export
    public void setFoodLevel(int level) {
        player.setFoodLevel(Math.max(0, Math.min(level, 20)));
    }

    /**
     * 获取玩家经验等级。
     *
     * @return 经验等级
     */
    @HostAccess.Export
    public int getLevel() {
        return player.getLevel();
    }

    /**
     * 设置玩家经验等级。
     *
     * @param level 目标等级
     */
    @HostAccess.Export
    public void setLevel(int level) {
        player.setLevel(Math.max(0, level));
    }

    /**
     * 获取玩家当前经验值（当前等级内的进度，0.0-1.0）。
     *
     * @return 经验进度
     */
    @HostAccess.Export
    public float getExp() {
        return player.getExp();
    }

    /**
     * 设置玩家当前经验进度。
     *
     * <p>值会被钳制在 [0.0, 1.0] 范围内。</p>
     *
     * @param exp 目标经验进度
     */
    @HostAccess.Export
    public void setExp(float exp) {
        player.setExp(Math.max(0.0f, Math.min(exp, 1.0f)));
    }

    /**
     * 获取玩家是否在线。
     *
     * @return 是否在线
     */
    @HostAccess.Export
    public boolean isOnline() {
        return player.isOnline();
    }

    /**
     * 获取玩家是否潜行。
     *
     * @return 是否潜行
     */
    @HostAccess.Export
    public boolean isSneaking() {
        return player.isSneaking();
    }

    /**
     * 获取玩家是否疾跑。
     *
     * @return 是否疾跑
     */
    @HostAccess.Export
    public boolean isSprinting() {
        return player.isSprinting();
    }

    /**
     * 获取玩家是否在飞行。
     *
     * @return 是否在飞行
     */
    @HostAccess.Export
    public boolean isFlying() {
        return player.isFlying();
    }

    /**
     * 获取玩家是否允许飞行。
     *
     * @return 是否允许飞行
     */
    @HostAccess.Export
    public boolean getAllowFlight() {
        return player.getAllowFlight();
    }

    /**
     * 获取玩家世界名称。
     *
     * @return 世界名称
     */
    @HostAccess.Export
    public @NotNull String getWorldName() {
        return player.getWorld().getName();
    }

    /**
     * 获取玩家 X 坐标。
     *
     * @return X 坐标
     */
    @HostAccess.Export
    public double getX() {
        return player.getLocation().getX();
    }

    /**
     * 获取玩家 Y 坐标。
     *
     * @return Y 坐标
     */
    @HostAccess.Export
    public double getY() {
        return player.getLocation().getY();
    }

    /**
     * 获取玩家 Z 坐标。
     *
     * @return Z 坐标
     */
    @HostAccess.Export
    public double getZ() {
        return player.getLocation().getZ();
    }

    @Override
    public String toString() {
        return "BukkitPlayerExport{name=" + player.getName() + ", uuid=" + player.getUniqueId() + "}";
    }
}
