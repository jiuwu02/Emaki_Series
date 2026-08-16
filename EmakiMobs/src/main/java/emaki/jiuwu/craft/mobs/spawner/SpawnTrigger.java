package emaki.jiuwu.craft.mobs.spawner;

/**
 * autonomous 类型刷新规则的调度触发方式。
 */
public enum SpawnTrigger {

    /** 固定 tick 间隔，逐个扫描所有在线玩家。需配置 {@code interval_ticks}。 */
    INTERVAL,

    /** 固定 tick 间隔，每次随机选一名在线玩家。需配置 {@code interval_ticks}。 */
    PLAYER_FOLLOW,

    /** 游戏日计时触发。需配置 {@code interval_days}；可选 {@code on_day_start}。 */
    DAY_INTERVAL,

    /** 系统真实时间 Cron 触发（6 段 Quartz 格式）。需配置 {@code cron}。 */
    CRON;

    /**
     * 从配置字符串解析 trigger 类型，大小写不敏感。
     * 未知值返回 {@code null}。
     */
    public static SpawnTrigger fromString(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toLowerCase()) {
            case "interval"      -> INTERVAL;
            case "player_follow" -> PLAYER_FOLLOW;
            case "day_interval"  -> DAY_INTERVAL;
            case "cron"          -> CRON;
            default              -> null;
        };
    }
}
