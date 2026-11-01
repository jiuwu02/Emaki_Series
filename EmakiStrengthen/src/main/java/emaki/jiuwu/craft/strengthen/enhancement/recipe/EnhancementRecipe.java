package emaki.jiuwu.craft.strengthen.enhancement.recipe;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.quantity.Quantity;
import emaki.jiuwu.craft.strengthen.enhancement.cost.CurrencyConfig;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityCounterConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectConfig;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityIsolationEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityTriggerConfig;

public record EnhancementRecipe(
        @NotNull String id,
        @NotNull String mode,
        @NotNull TargetConfig target,
        @NotNull List<MaterialSlotConfig> materials,
        @NotNull List<CurrencyConfig> costs,
        @NotNull Quantity chance,
        @Nullable PityConfig pity,
        @NotNull Map<String, List<String>> actions,
        @NotNull ConditionBlock conditions,
        @NotNull List<PityConfig> pityTracks
) {

    public static final String MODE_EQUIPMENT = "equipment";
    public static final String MODE_AFFIX = "affix";
    public static final String MODE_GEM = "gem";

    public static final Set<String> LEGAL_MODES = Set.of(MODE_EQUIPMENT, MODE_AFFIX, MODE_GEM);

    public EnhancementRecipe {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("配方 id 不能为空");
        }
        mode = Texts.normalizeId(mode);
        if (mode.isBlank()) {
            throw new IllegalArgumentException("配方 mode 不能为空");
        }
        if (!LEGAL_MODES.contains(mode)) {
            throw new IllegalArgumentException(
                    "配方 mode 必须是 " + LEGAL_MODES + " 之一，实际为 '" + mode + "'");
        }
        if (target == null) {
            throw new IllegalArgumentException("目标配置不能为空");
        }
        materials = materials == null ? List.of() : List.copyOf(materials);
        costs = costs == null ? List.of() : List.copyOf(costs);
        if (chance == null) {
            throw new IllegalArgumentException("成功率不能为空");
        }
        actions = actions == null ? Map.of() : Map.copyOf(actions);
        conditions = conditions == null ? ConditionBlock.empty() : conditions;
        pityTracks = normalizeTracks(pity, pityTracks);
        pity = pityTracks.isEmpty() ? null : pityTracks.get(0);
    }

    public EnhancementRecipe(@NotNull String id,
            @NotNull String mode,
            @NotNull TargetConfig target,
            @NotNull List<MaterialSlotConfig> materials,
            @NotNull List<CurrencyConfig> costs,
            @NotNull Quantity chance,
            @Nullable PityConfig pity,
            @NotNull Map<String, List<String>> actions) {
        this(id, mode, target, materials, costs, chance, pity, actions, ConditionBlock.empty(), List.of());
    }

    public EnhancementRecipe(@NotNull String id,
            @NotNull String mode,
            @NotNull TargetConfig target,
            @NotNull List<MaterialSlotConfig> materials,
            @NotNull List<CurrencyConfig> costs,
            @NotNull Quantity chance,
            @Nullable PityConfig pity,
            @NotNull Map<String, List<String>> actions,
            @NotNull ConditionBlock conditions) {
        this(id, mode, target, materials, costs, chance, pity, actions, conditions, List.of());
    }

    public boolean conditionsConfigured() {
        return conditions.configured();
    }

    public boolean pityConfigured() {
        return !pityTracks.isEmpty();
    }

    private static List<PityConfig> normalizeTracks(@Nullable PityConfig primary,
            @Nullable List<PityConfig> tracks) {
        List<PityConfig> merged = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendTrack(merged, seen, primary);
        if (tracks != null) {
            for (PityConfig track : tracks) {
                appendTrack(merged, seen, track);
            }
        }
        return List.copyOf(merged);
    }

    private static void appendTrack(List<PityConfig> merged, Set<String> seen, @Nullable PityConfig track) {
        if (track == null) {
            return;
        }
        String identity = track.counter().scope().name() + "|" + Texts.lower(track.counter().group());
        if (seen.add(identity)) {
            merged.add(track);
        }
    }

    public record TargetConfig(
            @NotNull String provider,
            @Nullable Map<String, Object> filter,
            @Nullable Matcher filterMatcher
    ) {
        public TargetConfig {
            if (provider == null || provider.isBlank()) {
                throw new IllegalArgumentException("目标 provider 不能为空");
            }
            filter = filter == null ? null : Map.copyOf(filter);
        }

        public TargetConfig(@NotNull String provider, @Nullable Map<String, Object> filter) {
            this(provider, filter, null);
        }

        public boolean filterConfigured() {
            return filterMatcher != null || (filter != null && !filter.isEmpty());
        }
    }

    public record PityConfig(
            @NotNull PityCounterConfig counter,
            @NotNull PityTriggerConfig trigger,
            @NotNull PityEffectConfig effect,
            @Nullable PityDecayConfig decay,
            @NotNull List<PityIsolationEnum> isolate
    ) {
        public PityConfig {
            if (counter == null) {
                throw new IllegalArgumentException("保底计数器不能为空");
            }
            if (trigger == null) {
                throw new IllegalArgumentException("保底触发器不能为空");
            }
            if (effect == null) {
                throw new IllegalArgumentException("保底效果不能为空");
            }
            isolate = isolate == null ? List.of() : List.copyOf(isolate);
        }

        public PityConfig(@NotNull PityCounterConfig counter,
                @NotNull PityTriggerConfig trigger,
                @NotNull PityEffectConfig effect,
                @Nullable PityDecayConfig decay) {
            this(counter, trigger, effect, decay, List.of());
        }
    }
}
