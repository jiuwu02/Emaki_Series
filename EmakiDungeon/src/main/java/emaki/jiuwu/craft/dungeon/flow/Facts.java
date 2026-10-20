package emaki.jiuwu.craft.dungeon.flow;

public final class Facts {

    public static final String RANGE_PLAYERS = "range_players";
    public static final String RANGE_MOBS = "range_mobs";
    public static final String REGION_ENTERED = "region_entered";
    public static final String REGION_LEFT = "region_left";
    public static final String CLICK = "click";
    public static final String BLOCK_ACTIVATE = "block_activate";
    public static final String REDSTONE_LEVEL = "redstone_level";
    public static final String ITEM_INTERACTED = "item_interacted";
    public static final String PLAYER_STATE = "player_state";
    public static final String REMAINING_LIVES = "remaining_lives";
    public static final String TIMER_REMAINING = "timer_remaining";
    public static final String COMMAND_RESULT = "command_result";
    public static final String SCRIPT_RESULT = "script_result";

    public static String waveCleared(String spawnId) {
        return "wave_cleared:" + spawnId;
    }

    public static String bossKilled(String bossId) {
        return "boss_killed:" + bossId;
    }

    public static String killCount(String target) {
        return "kill_count:" + target;
    }

    public static String timerMark(String moduleId) {
        return "timer_mark:" + moduleId;
    }

    private Facts() {
    }
}
