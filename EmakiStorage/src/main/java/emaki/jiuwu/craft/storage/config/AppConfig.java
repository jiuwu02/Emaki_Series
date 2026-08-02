package emaki.jiuwu.craft.storage.config;

import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;
import emaki.jiuwu.craft.storage.model.SearchQuery;
import emaki.jiuwu.craft.storage.model.SortMode;

/**
 * Typed view of {@code config.yml}.
 *
 * <p>Immutable value object; YAML parsing lives in {@code StorageLifecycleCoordinator} so a
 * malformed file can fall back to {@link #defaults()} without leaving a half-applied config
 * active.
 */
public final class AppConfig extends BaseAppConfig {

    /** Structure version of {@code config.yml}, independent of the plugin version. */
    public static final String CURRENT_VERSION = "1.0.6";

    /** Feedback style after a successful deposit. */
    public enum DepositFeedback {
        ACTIONBAR,
        MESSAGE,
        NONE;

        public static DepositFeedback fromId(String raw, DepositFeedback fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "actionbar" -> ACTIONBAR;
                case "message" -> MESSAGE;
                case "none" -> NONE;
                default -> fallback;
            };
        }
    }

    /** How the slot item's {@code amount} expresses occupancy. */
    public enum AmountMode {
        PERCENT,
        ONE;

        public static AmountMode fromId(String raw, AmountMode fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "percent" -> PERCENT;
                case "one" -> ONE;
                default -> fallback;
            };
        }
    }

    /** What happens to items the player's inventory cannot accept on withdrawal. */
    public enum WithdrawOverflow {
        RETURN,
        DROP;

        public static WithdrawOverflow fromId(String raw, WithdrawOverflow fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "return" -> RETURN;
                case "drop" -> DROP;
                default -> fallback;
            };
        }
    }

    /** How occupancy beyond the current capacity is handled. */
    public enum OverflowPolicy {
        LOCK_READONLY,
        COMPACT,
        RETURN_INVENTORY,
        REJECT_CHANGE;

        public static OverflowPolicy fromId(String raw, OverflowPolicy fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "lock_readonly" -> LOCK_READONLY;
                case "compact" -> COMPACT;
                case "return_inventory" -> RETURN_INVENTORY;
                case "reject_change" -> REJECT_CHANGE;
                default -> fallback;
            };
        }
    }

    /** Deposit filter mode. */
    public enum FilterMode {
        BLACKLIST,
        WHITELIST,
        OFF;

        public static FilterMode fromId(String raw, FilterMode fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "blacklist" -> BLACKLIST;
                case "whitelist" -> WHITELIST;
                case "off" -> OFF;
                default -> fallback;
            };
        }
    }

    /** Where the generated display lines are inserted into the rendered lore. */
    public enum LorePosition {
        TOP,
        BOTTOM;

        public static LorePosition fromId(String raw, LorePosition fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "top" -> TOP;
                case "bottom" -> BOTTOM;
                default -> fallback;
            };
        }
    }

    /** GUI layout and interaction settings. */
    public record GuiConfig(int storageRows,
            DepositFeedback depositFeedback,
            boolean requireEmptyCursorForWithdraw) {

        public static GuiConfig defaults() {
            return new GuiConfig(5, DepositFeedback.ACTIONBAR, false);
        }
    }

    /** Capacity settings; the four slot sources are summed then clamped. */
    public record CapacityConfig(int baseSlots,
            int maxSlots,
            int warnEntryCount,
            long defaultStackLimit) {

        public static CapacityConfig defaults() {
            return new CapacityConfig(45, 1000, 5000, 100L);
        }
    }

    /** Unlock and overflow settings. */
    public record UnlockConfig(OverflowPolicy overflowPolicy,
            boolean purchaseEnabled,
            String costFile) {

        public static UnlockConfig defaults() {
            return new UnlockConfig(OverflowPolicy.LOCK_READONLY, true, "unlock_costs.yml");
        }
    }

    /** Slot item rendering settings. */
    public record DisplayConfig(AmountMode amountMode,
            int percentScale,
            List<String> compactUnits,
            int compactDecimals,
            boolean showExactAmount,
            LorePosition lorePosition) {

        public DisplayConfig(AmountMode amountMode,
                int percentScale,
                List<String> compactUnits,
                int compactDecimals,
                boolean showExactAmount,
                LorePosition lorePosition) {
            this.amountMode = amountMode;
            this.percentScale = percentScale;
            this.compactUnits = List.copyOf(compactUnits);
            this.compactDecimals = compactDecimals;
            this.showExactAmount = showExactAmount;
            this.lorePosition = lorePosition;
        }

        public static DisplayConfig defaults() {
            return new DisplayConfig(AmountMode.PERCENT, 99,
                    List.of("K", "M", "B", "T", "P", "E"), 2, true, LorePosition.BOTTOM);
        }
    }

    /** Withdrawal amounts bound to the four click tiers. */
    public record WithdrawAmounts(long left, long right, long shiftLeft, long shiftRight) {

        public static WithdrawAmounts defaults() {
            return new WithdrawAmounts(1L, 16L, 32L, 64L);
        }
    }

    /** Deposit filter settings. */
    public record DepositFilter(FilterMode mode, List<String> entries) {

        public DepositFilter(FilterMode mode, List<String> entries) {
            this.mode = mode;
            this.entries = List.copyOf(entries);
        }

        public static DepositFilter defaults() {
            return new DepositFilter(FilterMode.BLACKLIST, List.of());
        }
    }

    /** 自定义取出数量输入项的 key；对话框配置必须提供同名输入框。 */
    public static final String WITHDRAW_INPUT_KEY = "amount";

    /** 搜索关键词输入项的 key；对话框配置必须提供同名输入框。 */
    public static final String SEARCH_INPUT_KEY = "query";

    /**
     * Transaction behaviour settings.
     *
     * @param multiSlotStacking whether one item kind may span several slots once the per-slot
     *                          ceiling is reached; {@code false} keeps the legacy behaviour of
     *                          refusing the surplus outright
     * @param batchMaxOps       hard cap on how many increments one API batch may carry, clamped to
     *                          {@code 1..2000}; a larger request is rejected outright rather than
     *                          truncated, because a silently shortened batch is a half-applied recipe
     */
    public record BehaviorConfig(WithdrawOverflow overflowOnWithdraw,
            WithdrawAmounts withdrawAmounts,
            boolean withdrawPromptEnabled,
            InputModeConfig withdrawInput,
            DepositFilter depositFilter,
            boolean allowUniqueItems,
            boolean multiSlotStacking,
            int batchMaxOps,
            SortMode defaultSort,
            boolean playerSortEnabled) {

        /** Inclusive lower bound for {@link #batchMaxOps()}. */
        public static final int BATCH_MAX_OPS_MIN = 1;

        /** Inclusive upper bound for {@link #batchMaxOps()}. */
        public static final int BATCH_MAX_OPS_MAX = 2000;

        public BehaviorConfig {
            withdrawInput = withdrawInput == null
                    ? InputModeConfig.defaults(WITHDRAW_INPUT_KEY)
                    : withdrawInput;
            batchMaxOps = Math.clamp(batchMaxOps, BATCH_MAX_OPS_MIN, BATCH_MAX_OPS_MAX);
        }

        public static BehaviorConfig defaults() {
            return new BehaviorConfig(WithdrawOverflow.RETURN, WithdrawAmounts.defaults(), true,
                    InputModeConfig.defaults(WITHDRAW_INPUT_KEY),
                    DepositFilter.defaults(), true, false, 200, SortMode.AMOUNT_DESC, true);
        }
    }

    /** Search settings. No regex option exists at any permission level. */
    public record SearchConfig(boolean enabled,
            SearchQuery.Operators operators,
            InputModeConfig input,
            long inputTimeoutSeconds,
            List<String> cancelKeywords) {

        public SearchConfig(boolean enabled,
                SearchQuery.Operators operators,
                InputModeConfig input,
                long inputTimeoutSeconds,
                List<String> cancelKeywords) {
            this.enabled = enabled;
            this.operators = operators;
            this.input = input == null ? InputModeConfig.defaults(SEARCH_INPUT_KEY) : input;
            this.inputTimeoutSeconds = inputTimeoutSeconds;
            this.cancelKeywords = List.copyOf(cancelKeywords);
        }

        public static SearchConfig defaults() {
            return new SearchConfig(true, SearchQuery.Operators.defaults(),
                    InputModeConfig.defaults(SEARCH_INPUT_KEY), 30L,
                    List.of("取消", "cancel"));
        }
    }

    /** Persistence timing settings. */
    public record PersistenceConfig(long autosaveIntervalSeconds, long drainTimeoutSeconds) {

        public static PersistenceConfig defaults() {
            return new PersistenceConfig(300L, 10L);
        }
    }

    /** Operation log settings. The log is write-only; business logic never reads it. */
    public record LoggingConfig(boolean enabled, int retentionDays, List<String> sources) {

        public LoggingConfig(boolean enabled, int retentionDays, List<String> sources) {
            this.enabled = enabled;
            this.retentionDays = retentionDays;
            this.sources = List.copyOf(sources);
        }

        public static LoggingConfig defaults() {
            return new LoggingConfig(true, 30, List.of());
        }
    }

    private final boolean releaseDefaultData;
    private final boolean opBypass;
    private final GuiConfig gui;
    private final CapacityConfig capacity;
    private final UnlockConfig unlock;
    private final DisplayConfig display;
    private final BehaviorConfig behavior;
    private final AutoPickupConfig autoPickup;
    private final SearchConfig search;
    private final PersistenceConfig persistence;
    private final LoggingConfig logging;
    private final boolean debug;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            boolean opBypass,
            GuiConfig gui,
            CapacityConfig capacity,
            UnlockConfig unlock,
            DisplayConfig display,
            BehaviorConfig behavior,
            AutoPickupConfig autoPickup,
            SearchConfig search,
            PersistenceConfig persistence,
            LoggingConfig logging,
            boolean debug) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.opBypass = opBypass;
        this.gui = gui == null ? GuiConfig.defaults() : gui;
        this.capacity = capacity == null ? CapacityConfig.defaults() : capacity;
        this.unlock = unlock == null ? UnlockConfig.defaults() : unlock;
        this.display = display == null ? DisplayConfig.defaults() : display;
        this.behavior = behavior == null ? BehaviorConfig.defaults() : behavior;
        this.autoPickup = autoPickup == null ? AutoPickupConfig.defaults() : autoPickup;
        this.search = search == null ? SearchConfig.defaults() : search;
        this.persistence = persistence == null ? PersistenceConfig.defaults() : persistence;
        this.logging = logging == null ? LoggingConfig.defaults() : logging;
        this.debug = debug;
    }

    public static AppConfig defaults() {
        return new AppConfig("zh_CN", CURRENT_VERSION, true, false,
                GuiConfig.defaults(), CapacityConfig.defaults(), UnlockConfig.defaults(),
                DisplayConfig.defaults(), BehaviorConfig.defaults(),
                AutoPickupConfig.defaults(), SearchConfig.defaults(),
                PersistenceConfig.defaults(), LoggingConfig.defaults(), false);
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public boolean opBypass() {
        return opBypass;
    }

    public GuiConfig gui() {
        return gui;
    }

    public CapacityConfig capacity() {
        return capacity;
    }

    public UnlockConfig unlock() {
        return unlock;
    }

    public DisplayConfig display() {
        return display;
    }

    public BehaviorConfig behavior() {
        return behavior;
    }

    /** {@return 自动拾取设置} */
    public AutoPickupConfig autoPickup() {
        return autoPickup;
    }

    public SearchConfig search() {
        return search;
    }

    public PersistenceConfig persistence() {
        return persistence;
    }

    public LoggingConfig logging() {
        return logging;
    }

    public boolean debug() {
        return debug;
    }
}
