package emaki.jiuwu.craft.corelib.action.select;

import org.jetbrains.annotations.NotNull;

public record TargetSelectorServices(@NotNull ConfiguredSelectorRepository selectors,
        @NotNull TargetFactsReader factsReader,
        @NotNull TargetConditionEvaluator evaluator) {
}