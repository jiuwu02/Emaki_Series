package emaki.jiuwu.craft.level.model;

public final class LevelFailureReason {

    public static final String SUCCESS = "success";
    public static final String PLAYER_NOT_FOUND = "player_not_found";
    public static final String PLAYER_DATA_UNAVAILABLE = "player_data_unavailable";
    public static final String TYPE_NOT_FOUND = "type_not_found";
    public static final String TYPE_DISABLED = "type_disabled";
    public static final String INVALID_AMOUNT = "invalid_amount";
    public static final String DAILY_CAP_REACHED = "daily_cap_reached";
    public static final String UPGRADE_DISABLED = "upgrade_disabled";
    public static final String MANUAL_UPGRADE_DISABLED = "manual_upgrade_disabled";
    public static final String MAX_LEVEL = "max_level";
    public static final String NOT_ENOUGH_EXP = "not_enough_exp";
    public static final String NOT_ENOUGH_MONEY = "not_enough_money";
    public static final String NOT_ENOUGH_MATERIAL = "not_enough_material";
    public static final String COST_COMPENSATION_FAILED = "cost_compensation_failed";
    public static final String INVALID_REQUIREMENT = "invalid_requirement";

    private LevelFailureReason() {
    }
}
