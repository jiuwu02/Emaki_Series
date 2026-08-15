package emaki.jiuwu.craft.storage.config;

import java.util.List;
import java.util.Locale;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;
import emaki.jiuwu.craft.storage.model.SearchQuery;
import emaki.jiuwu.craft.storage.model.SortMode;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "1.0.6";

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

    public record GuiConfig(int storageRows,
            DepositFeedback depositFeedback,
            boolean requireEmptyCursorForWithdraw) {

        public static GuiConfig defaults() {
            return new GuiConfig(5, DepositFeedback.ACTIONBAR, false);
        }
    }

    public record CapacityConfig(int baseSlots,
            int maxSlots,
            int warnEntryCount,
            long defaultStackLimit) {

        public static CapacityConfig defaults() {
            return new CapacityConfig(45, 1000, 5000, 100L);
        }
    }

    public record UnlockConfig(OverflowPolicy overflowPolicy,
            boolean purchaseEnabled,
            String costFile) {

        public static UnlockConfig defaults() {
            return new UnlockConfig(OverflowPolicy.LOCK_READONLY, true, "unlock_costs.yml");
        }
    }

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

    public record WithdrawAmounts(long left, long right, long shiftLeft, long shiftRight) {

        public static WithdrawAmounts defaults() {
            return new WithdrawAmounts(1L, 16L, 32L, 64L);
        }
    }

    public record DepositFilter(FilterMode mode, List<String> entries) {

        public DepositFilter(FilterMode mode, List<String> entries) {
            this.mode = mode;
            this.entries = List.copyOf(entries);
        }

        public static DepositFilter defaults() {
            return new DepositFilter(FilterMode.BLACKLIST, List.of());
        }
    }

    public static final String WITHDRAW_INPUT_KEY = "amount";

    public static final String SEARCH_INPUT_KEY = "query";

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

        public static final int BATCH_MAX_OPS_MIN = 1;

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

    public record PersistenceConfig(long autosaveIntervalSeconds, long drainTimeoutSeconds) {

        public static PersistenceConfig defaults() {
            return new PersistenceConfig(300L, 10L);
        }
    }

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
