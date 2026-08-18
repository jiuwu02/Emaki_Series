package emaki.jiuwu.craft.strengthen.enhancement.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.progression.Progression;
import emaki.jiuwu.craft.corelib.quantity.Quantity;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.enhancement.cost.CurrencyConfig;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;

/**
 * Resolves every level-dependent value used by one generic enhancement attempt.
 *
 * <p>The resolver freezes the previous, current and target level variable contexts once, then derives
 * chance, material quantities and currency costs from those immutable snapshots. Preview and execution
 * can therefore share one resolution instead of evaluating formulas independently.
 */
public final class EnhancementProgressionResolver {

    public @NotNull Resolution resolve(@NotNull EnhancementRecipe recipe,
            int currentLevel,
            @NotNull IntFunction<VariableContext> contextFactory) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(contextFactory, "contextFactory");

        LevelWindow levels = LevelWindow.at(currentLevel);
        Map<Integer, VariableContext> contexts = freezeContexts(levels, contextFactory);

        ResolvedQuantity chance = resolveQuantity(recipe.chance(), levels, contexts);
        List<ResolvedQuantity> materials = new ArrayList<>(recipe.materials().size());
        for (MaterialSlotConfig material : recipe.materials()) {
            materials.add(resolveQuantity(material.quantity(), levels, contexts));
        }

        List<ResolvedCurrency> costs = new ArrayList<>(recipe.costs().size());
        for (CurrencyConfig currency : recipe.costs()) {
            costs.add(new ResolvedCurrency(
                    currency.provider(),
                    currency.currencyId(),
                    resolveQuantity(currency.amount(), levels, contexts)));
        }

        return new Resolution(levels, contexts.get(levels.currentLevel()), chance, materials, costs);
    }

    private Map<Integer, VariableContext> freezeContexts(LevelWindow levels,
            IntFunction<VariableContext> contextFactory) {
        Map<Integer, VariableContext> contexts = new LinkedHashMap<>();
        freezeContext(contexts, levels.previousLevel(), contextFactory);
        freezeContext(contexts, levels.currentLevel(), contextFactory);
        freezeContext(contexts, levels.targetLevel(), contextFactory);
        return Map.copyOf(contexts);
    }

    private void freezeContext(Map<Integer, VariableContext> contexts,
            int level,
            IntFunction<VariableContext> contextFactory) {
        contexts.computeIfAbsent(level, key -> {
            VariableContext source = Objects.requireNonNull(contextFactory.apply(key), "variable context");
            return VariableContext.builder(null).withAll(source.toMap()).build();
        });
    }

    private ResolvedQuantity resolveQuantity(Quantity quantity,
            LevelWindow levels,
            Map<Integer, VariableContext> contexts) {
        Quantity source = quantity == null ? Quantity.fixed(0D) : quantity;
        Progression<Double> progression = level -> finite(source.resolve(contexts.get(level)));
        return new ResolvedQuantity(
                progression.valueAt(levels.previousLevel()),
                progression.valueAt(levels.currentLevel()),
                progression.valueAt(levels.targetLevel()));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0D;
    }

    public record LevelWindow(int previousLevel, int currentLevel, int targetLevel) {

        public LevelWindow {
            previousLevel = Math.max(0, previousLevel);
            currentLevel = Math.max(0, currentLevel);
            targetLevel = Math.max(currentLevel, targetLevel);
        }

        public static @NotNull LevelWindow at(int currentLevel) {
            int current = Math.max(0, currentLevel);
            int previous = Math.max(0, current - 1);
            int target = current == Integer.MAX_VALUE ? Integer.MAX_VALUE : current + 1;
            return new LevelWindow(previous, current, target);
        }
    }

    public record ResolvedQuantity(double previous, double current, double target) {

        public int currentInt() {
            return (int) current;
        }

        public long currentLong() {
            return (long) current;
        }
    }

    public record ResolvedCurrency(@NotNull String provider,
            @NotNull String currencyId,
            @NotNull ResolvedQuantity amount) {

        public ResolvedCurrency {
            provider = provider == null ? "" : provider;
            currencyId = currencyId == null ? "" : currencyId;
            amount = amount == null ? new ResolvedQuantity(0D, 0D, 0D) : amount;
        }
    }

    public record Resolution(@NotNull LevelWindow levels,
            @NotNull VariableContext variables,
            @NotNull ResolvedQuantity chance,
            @NotNull List<ResolvedQuantity> materials,
            @NotNull List<ResolvedCurrency> currencies) {

        public Resolution {
            Objects.requireNonNull(levels, "levels");
            Objects.requireNonNull(variables, "variables");
            chance = chance == null ? new ResolvedQuantity(0D, 0D, 0D) : chance;
            materials = materials == null ? List.of() : List.copyOf(materials);
            currencies = currencies == null ? List.of() : List.copyOf(currencies);
        }

        public @NotNull List<Integer> currentMaterialAmounts() {
            return materials.stream().map(ResolvedQuantity::currentInt).toList();
        }

        public @NotNull List<AttemptCost> currentCosts() {
            List<AttemptCost> result = new ArrayList<>();
            for (ResolvedCurrency currency : currencies) {
                long amount = Math.max(0L, currency.amount().currentLong());
                if (amount > 0L) {
                    result.add(new AttemptCost(
                            currency.provider(),
                            currency.currencyId(),
                            currency.currencyId(),
                            amount));
                }
            }
            return List.copyOf(result);
        }
    }
}
