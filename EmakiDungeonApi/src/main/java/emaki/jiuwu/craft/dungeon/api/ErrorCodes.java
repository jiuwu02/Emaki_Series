package emaki.jiuwu.craft.dungeon.api;

/**
 * Stable string error codes carried by {@link Result#code()}.
 *
 * <p>Every failure returned by a facade call names one of these identifiers;
 * the paired {@code messageKey} of the same {@link Result} selects the
 * language entry used to render it.
 */
public final class ErrorCodes {

    /** Entry denied because the per-player cooldown has not elapsed. */
    public static final String DENY_COOLDOWN = "DENY_COOLDOWN";
    /** Entry denied because the daily entry limit has been reached. */
    public static final String DENY_DAILY_LIMIT = "DENY_DAILY_LIMIT";
    /** Entry denied because the weekly entry limit has been reached. */
    public static final String DENY_WEEKLY_LIMIT = "DENY_WEEKLY_LIMIT";
    /** Entry denied because the monthly entry limit has been reached. */
    public static final String DENY_MONTHLY_LIMIT = "DENY_MONTHLY_LIMIT";
    /** Entry denied by the configured availability window. */
    public static final String DENY_WINDOW = "DENY_WINDOW";
    /** Entry denied because no instance slot is free across all dungeons. */
    public static final String DENY_GLOBAL_FULL = "DENY_GLOBAL_FULL";
    /** Entry denied because no instance slot is free for this dungeon. */
    public static final String DENY_DUNGEON_FULL = "DENY_DUNGEON_FULL";
    /** The caller's party does not satisfy the entry requirements. */
    public static final String DENY_PARTY_INVALID = "DENY_PARTY_INVALID";
    /** The player is not the leader of their party. */
    public static final String DENY_NOT_LEADER = "DENY_NOT_LEADER";
    /** The player lacks the permission required for this action. */
    public static final String DENY_PERMISSION = "DENY_PERMISSION";
    /** The player already belongs to a party. */
    public static final String PARTY_ALREADY_EXISTS = "PARTY_ALREADY_EXISTS";
    /** The party has reached its member limit. */
    public static final String PARTY_FULL = "PARTY_FULL";
    /** A party invite is already pending for the target player. */
    public static final String INVITE_PENDING = "INVITE_PENDING";
    /** The referenced player is not online. */
    public static final String TARGET_OFFLINE = "TARGET_OFFLINE";
    /** The player's party has no active challenge session. */
    public static final String CHALLENGE_NOT_ACTIVE = "CHALLENGE_NOT_ACTIVE";
    /** The player has already confirmed readiness. */
    public static final String ALREADY_READY = "ALREADY_READY";
    /** The requested entry window is closed. */
    public static final String WINDOW_CLOSED = "WINDOW_CLOSED";
    /** No instance with the given id is registered. */
    public static final String INSTANCE_NOT_FOUND = "INSTANCE_NOT_FOUND";
    /** The instance is already shutting down or has been shut down. */
    public static final String ALREADY_DISPOSING = "ALREADY_DISPOSING";
    /** No dungeon configuration exists for the requested id. */
    public static final String DUNGEON_NOT_FOUND = "DUNGEON_NOT_FOUND";
    /** The script engine is unavailable or has not finished starting. */
    public static final String ENGINE_UNAVAILABLE = "ENGINE_UNAVAILABLE";
    /** The submitted script failed to compile. */
    public static final String SCRIPT_COMPILE_ERROR = "SCRIPT_COMPILE_ERROR";
    /** A trigger with the same id is already registered. */
    public static final String TRIGGER_DUPLICATE = "TRIGGER_DUPLICATE";
    /** No flow script is loaded, so triggers cannot be registered. */
    public static final String SCRIPT_NOT_LOADED = "SCRIPT_NOT_LOADED";
    /** The player is not a member of any party. */
    public static final String MEMBER_NOT_FOUND = "MEMBER_NOT_FOUND";
    /** The acting player is not the party leader. */
    public static final String NOT_LEADER = "NOT_LEADER";
    /** EmakiDungeon is not installed, so the call could not be served. */
    public static final String NOT_INSTALLED = "NOT_INSTALLED";

    private ErrorCodes() {
    }
}
