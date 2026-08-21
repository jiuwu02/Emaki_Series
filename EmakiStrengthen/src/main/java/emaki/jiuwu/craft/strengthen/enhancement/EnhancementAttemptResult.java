package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityResult;

public record EnhancementAttemptResult(boolean committed,
        boolean success,
        @NotNull String errorKey,
        @NotNull Map<String, String> placeholders,
        int previousLevel,
        int resultingLevel,
        double successRate,
        int pityCounter,
        boolean pityTriggered,
        @NotNull EnhancementPityResult pityResult) {

    public EnhancementAttemptResult {
        errorKey = errorKey == null ? "" : errorKey;
        placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        pityResult = pityResult == null ? EnhancementPityResult.empty() : pityResult;
    }

    public EnhancementAttemptResult(boolean committed,
            boolean success,
            @NotNull String errorKey,
            @NotNull Map<String, String> placeholders,
            int previousLevel,
            int resultingLevel,
            double successRate,
            int pityCounter,
            boolean pityTriggered) {
        this(committed, success, errorKey, placeholders, previousLevel, resultingLevel,
                successRate, pityCounter, pityTriggered, EnhancementPityResult.empty());
    }

    public EnhancementAttemptResult(boolean committed,
            boolean success,
            @NotNull String errorKey,
            @NotNull Map<String, String> placeholders,
            int previousLevel,
            int resultingLevel,
            double successRate,
            @NotNull EnhancementPityResult pityResult) {
        this(committed, success, errorKey, placeholders, previousLevel, resultingLevel, successRate,
                pityResult == null ? 0 : pityResult.primaryCounter(),
                pityResult != null && pityResult.triggered(), pityResult);
    }

    public static @NotNull EnhancementAttemptResult rejected(@NotNull String errorKey) {
        return rejected(errorKey, Map.of());
    }

    public static @NotNull EnhancementAttemptResult rejected(@NotNull String errorKey,
            @Nullable Map<String, String> placeholders) {
        return new EnhancementAttemptResult(false, false, errorKey,
                placeholders == null ? Map.of() : placeholders, 0, 0, 0D, 0, false);
    }

    public @NotNull Map<String, String> toPlaceholders() {
        Map<String, String> values = new LinkedHashMap<>(placeholders);
        values.put("previous_level", String.valueOf(previousLevel));
        values.put("resulting_level", String.valueOf(resultingLevel));
        values.put("success", String.valueOf(success));
        values.put("success_rate", String.valueOf(successRate));
        values.put("pity_counter", String.valueOf(pityCounter));
        values.put("pity_triggered", String.valueOf(pityTriggered));
        return Map.copyOf(values);
    }

    public @NotNull List<String> actionPhaseKeys() {
        return success ? List.of("on_success") : List.of("on_failure");
    }
}
