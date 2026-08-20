package emaki.jiuwu.craft.item.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record ProficiencyGuardConfig(boolean dedupePerEvent,
        int maxDispatchesPerEvent,
        long sameTargetCooldownMillis,
        int dailySoftCap,
        double decayFactor,
        double decayMinimumRatio,
        Set<String> guardedTriggers) {

    public static final String TRIGGER_DAMAGE_DEALT = "damage_dealt";
    public static final String TRIGGER_KILL_ENTITY = "kill_entity";
    public static final String TRIGGER_KILL_PLAYER = "kill_player";

    private static final Set<String> DEFAULT_TRIGGERS =
            Set.of(TRIGGER_DAMAGE_DEALT, TRIGGER_KILL_ENTITY, TRIGGER_KILL_PLAYER);

    public ProficiencyGuardConfig {
        maxDispatchesPerEvent = Math.max(0, maxDispatchesPerEvent);
        sameTargetCooldownMillis = Math.max(0L, sameTargetCooldownMillis);
        dailySoftCap = Math.max(0, dailySoftCap);
        decayFactor = decayFactor <= 0D || decayFactor > 1D || !Double.isFinite(decayFactor) ? 1D : decayFactor;
        decayMinimumRatio = !Double.isFinite(decayMinimumRatio)
                ? 0D
                : Math.max(0D, Math.min(1D, decayMinimumRatio));
        guardedTriggers = guardedTriggers == null || guardedTriggers.isEmpty()
                ? DEFAULT_TRIGGERS
                : Set.copyOf(guardedTriggers);
    }

    public static ProficiencyGuardConfig defaults() {
        return new ProficiencyGuardConfig(true, 0, 0L, 0, 1D, 0D, DEFAULT_TRIGGERS);
    }

    public static ProficiencyGuardConfig parse(YamlSection section) {
        ProficiencyGuardConfig fallback = defaults();
        if (section == null || section.isEmpty()) {
            return fallback;
        }
        return new ProficiencyGuardConfig(
                section.getBoolean("dedupe_per_event", fallback.dedupePerEvent()),
                section.getInt("max_dispatches_per_event", fallback.maxDispatchesPerEvent()),
                Math.round(section.getDouble("same_target_cooldown_seconds", 0D) * 1000D),
                section.getInt("daily_soft_cap", fallback.dailySoftCap()),
                section.getDouble("decay_factor", fallback.decayFactor()),
                section.getDouble("decay_minimum_ratio", fallback.decayMinimumRatio()),
                parseTriggers(section.getStringList("guarded_triggers")));
    }

    public boolean guards(String trigger) {
        return guardedTriggers.contains(Texts.toStringSafe(trigger).trim().toLowerCase(Locale.ROOT));
    }

    public boolean dispatchLimited() {
        return maxDispatchesPerEvent > 0;
    }

    public boolean cooldownEnabled() {
        return sameTargetCooldownMillis > 0L;
    }

    public boolean softCapEnabled() {
        return dailySoftCap > 0;
    }

    public boolean decayEnabled() {
        return softCapEnabled() && decayFactor < 1D;
    }

    private static Set<String> parseTriggers(List<String> values) {
        if (values == null || values.isEmpty()) {
            return DEFAULT_TRIGGERS;
        }
        Set<String> triggers = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT);
            if (!normalized.isBlank()) {
                triggers.add(normalized);
            }
        }
        return triggers.isEmpty() ? DEFAULT_TRIGGERS : Set.copyOf(triggers);
    }
}
