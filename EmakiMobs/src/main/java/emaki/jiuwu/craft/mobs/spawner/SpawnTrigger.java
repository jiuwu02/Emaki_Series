package emaki.jiuwu.craft.mobs.spawner;

public enum SpawnTrigger {

    INTERVAL,

    PLAYER_FOLLOW,

    DAY_INTERVAL,

    CRON;

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
