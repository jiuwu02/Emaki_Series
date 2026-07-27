package emaki.jiuwu.craft.storage.config;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * 自动拾取设置。
 *
 * <p>需要 {@code emakistorage.autopickup} 权限，并由玩家自行开启；
 * 开关状态按玩家保存在 {@code meta.yml}。
 */
public record AutoPickupConfig(boolean enabled,
        Mode mode,
        int radius,
        int maxRadius,
        int scanIntervalTicks,
        boolean defaultEnabled,
        long notifyCooldownMs) {

    public AutoPickupConfig {
        mode = mode == null ? Mode.ON_PICKUP : mode;
        maxRadius = Math.max(1, maxRadius);
        radius = Math.clamp(radius, 1, maxRadius);
        scanIntervalTicks = Math.max(1, scanIntervalTicks);
        notifyCooldownMs = Math.max(0L, notifyCooldownMs);
    }

    /** 拾取模式。 */
    public enum Mode {
        /** 玩家拾取物品时决定是否转入仓库。 */
        ON_PICKUP,
        /** 周期扫描玩家附近的掉落物并吸入仓库。 */
        RADIUS;

        public static Mode parse(String raw, Mode fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "on_pickup", "pickup" -> ON_PICKUP;
                case "radius", "range" -> RADIUS;
                default -> fallback;
            };
        }
    }

    public static AutoPickupConfig defaults() {
        return new AutoPickupConfig(true, Mode.ON_PICKUP, 8, 16, 10, false, 3000L);
    }

    public static AutoPickupConfig fromConfig(YamlSection section) {
        if (section == null) {
            return defaults();
        }
        AutoPickupConfig defaults = defaults();
        return new AutoPickupConfig(
                section.getBoolean("enabled", defaults.enabled()),
                Mode.parse(section.getString("mode", null), defaults.mode()),
                section.getInt("radius", defaults.radius()),
                section.getInt("max_radius", defaults.maxRadius()),
                section.getInt("scan_interval_ticks", defaults.scanIntervalTicks()),
                section.getBoolean("default_enabled", defaults.defaultEnabled()),
                section.getInt("notify_cooldown_ms", (int) defaults.notifyCooldownMs())
        );
    }

    public boolean radiusMode() {
        return mode == Mode.RADIUS;
    }
}
