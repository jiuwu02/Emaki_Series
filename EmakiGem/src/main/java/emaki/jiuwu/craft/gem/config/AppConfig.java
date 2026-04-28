package emaki.jiuwu.craft.gem.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.BaseAppConfig;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.service.GemGuiMode;
import emaki.jiuwu.craft.gem.model.SocketOpenerConfig;

public final class AppConfig extends BaseAppConfig {

    public static final String CURRENT_VERSION = "1.2.0";

    private final boolean releaseDefaultData;
    private final Map<String, SocketOpenerConfig> socketOpeners;
    private final InlaySuccessConfig inlaySuccess;
    private final UpgradeSettings upgrade;
    private final String numberFormat;
    private final boolean opBypass;
    private final GuiSettings gui;
    private final ConditionConfig condition;

    public AppConfig(String language,
            String configVersion,
            boolean releaseDefaultData,
            Map<String, SocketOpenerConfig> socketOpeners,
            InlaySuccessConfig inlaySuccess,
            UpgradeSettings upgrade,
            String numberFormat,
            boolean opBypass,
            GuiSettings gui,
            ConditionConfig condition) {
        super(language, configVersion, CURRENT_VERSION);
        this.releaseDefaultData = releaseDefaultData;
        this.socketOpeners = socketOpeners == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(socketOpeners));
        this.inlaySuccess = inlaySuccess == null ? InlaySuccessConfig.defaults() : inlaySuccess;
        this.upgrade = upgrade == null ? UpgradeSettings.defaults() : upgrade;
        this.numberFormat = numberFormat == null || numberFormat.isBlank() ? "0.##" : numberFormat;
        this.opBypass = opBypass;
        this.gui = gui == null ? GuiSettings.defaults() : gui;
        this.condition = condition == null ? ConditionConfig.defaults() : condition;
    }

    public static AppConfig defaults() {
        return new AppConfig(
                "zh_CN",
                CURRENT_VERSION,
                true,
                Map.of(),
                InlaySuccessConfig.defaults(),
                UpgradeSettings.defaults(),
                "0.##",
                false,
                GuiSettings.defaults(),
                ConditionConfig.defaults()
        );
    }

    public boolean releaseDefaultData() {
        return releaseDefaultData;
    }

    public Map<String, SocketOpenerConfig> socketOpeners() {
        return socketOpeners;
    }

    public InlaySuccessConfig inlaySuccess() {
        return inlaySuccess;
    }

    public UpgradeSettings upgrade() {
        return upgrade;
    }

    public String numberFormat() {
        return numberFormat;
    }

    public boolean opBypass() {
        return opBypass;
    }

    public GuiSettings gui() {
        return gui;
    }

    public ConditionConfig condition() {
        return condition;
    }

    public record InlaySuccessConfig(boolean enabled,
            double defaultChance,
            String rateFormula,
            String failureAction,
            Map<Integer, Double> tierChances) {

        public InlaySuccessConfig {
            defaultChance = Math.max(0D, Math.min(100D, defaultChance));
            rateFormula = Texts.isBlank(rateFormula) ? "{default_chance}" : rateFormula;
            failureAction = Texts.isBlank(failureAction) ? "return_gem" : Texts.lower(failureAction);
            tierChances = tierChances == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(tierChances));
        }

        public static InlaySuccessConfig defaults() {
            return new InlaySuccessConfig(false, 100D, "{default_chance}", "return_gem", Map.of());
        }
    }

    public record UpgradeSettings(Map<Integer, Double> globalSuccessRates,
            String globalFailurePenalty) {

        public UpgradeSettings {
            globalSuccessRates = globalSuccessRates == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(globalSuccessRates));
            globalFailurePenalty = Texts.isBlank(globalFailurePenalty) ? "none" : Texts.lower(globalFailurePenalty);
        }

        public static UpgradeSettings defaults() {
            return new UpgradeSettings(Map.of(), "none");
        }
    }

    public record GuiSettings(GemGuiMode defaultMode, boolean saveOnClose) {

        public GuiSettings {
            defaultMode = defaultMode == null ? GemGuiMode.INLAY : defaultMode;
        }

        public static GuiSettings defaults() {
            return new GuiSettings(GemGuiMode.INLAY, false);
        }
    }

    public record ConditionConfig(List<String> conditions,
            String conditionType,
            int requiredCount,
            boolean invalidAsFailure) {

        public ConditionConfig {
            conditions = conditions == null ? List.of() : List.copyOf(conditions);
            conditionType = Texts.isBlank(conditionType) ? "all_of" : Texts.lower(conditionType);
            requiredCount = Math.max(0, requiredCount);
        }

        public static ConditionConfig defaults() {
            return new ConditionConfig(List.of(), "all_of", 0, true);
        }
    }
}
