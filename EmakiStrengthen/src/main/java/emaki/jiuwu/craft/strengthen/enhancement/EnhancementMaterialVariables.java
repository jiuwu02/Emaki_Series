package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixLayer;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixLayerCodec;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixState;

public final class EnhancementMaterialVariables {

    public static final String VARIABLE_SHARED_AFFIX_COUNT = "material_shared_affix_count";
    public static final String VARIABLE_SHARED_AFFIX_KEYS = "material_shared_affix_keys";
    public static final String VARIABLE_SAME_AFFIX_SET = "material_same_affix_set";
    public static final String VARIABLE_AFFIX_COUNT = "material_affix_count";
    public static final String VARIABLE_TARGET_AFFIX_COUNT = "target_affix_count";
    public static final String VARIABLE_AFFIX_KEYS = "material_affix_keys";
    public static final String VARIABLE_TARGET_AFFIX_KEYS = "target_affix_keys";
    public static final String VARIABLE_SHARED_AFFIX_LEVEL_MIN = "material_shared_affix_level_min";
    public static final String VARIABLE_SHARED_AFFIX_LEVEL_MAX = "material_shared_affix_level_max";
    public static final String VARIABLE_LEVEL = "material_level";
    public static final String VARIABLE_LEVEL_DELTA = "material_level_delta";
    public static final String VARIABLE_SAME_LEVEL = "material_same_level";
    public static final String VARIABLE_LEVEL_AT_LEAST_TARGET = "material_level_at_least_target";
    public static final String VARIABLE_SAME_ITEM_TYPE = "material_same_item_type";
    public static final String VARIABLE_ITEM_TYPE = "material_item_type";

    private static final List<String> CONTRACT = List.of(
            VARIABLE_AFFIX_COUNT,
            VARIABLE_AFFIX_KEYS,
            VARIABLE_ITEM_TYPE,
            VARIABLE_LEVEL,
            VARIABLE_LEVEL_AT_LEAST_TARGET,
            VARIABLE_LEVEL_DELTA,
            VARIABLE_SAME_AFFIX_SET,
            VARIABLE_SAME_ITEM_TYPE,
            VARIABLE_SAME_LEVEL,
            VARIABLE_SHARED_AFFIX_COUNT,
            VARIABLE_SHARED_AFFIX_KEYS,
            VARIABLE_SHARED_AFFIX_LEVEL_MAX,
            VARIABLE_SHARED_AFFIX_LEVEL_MIN,
            VARIABLE_TARGET_AFFIX_COUNT,
            VARIABLE_TARGET_AFFIX_KEYS);

    private EnhancementMaterialVariables() {
    }

    public static @NotNull List<String> contract() {
        return CONTRACT;
    }

    public static @NotNull VariableContext enrich(@NotNull VariableContext base,
            @Nullable ItemStack candidate,
            @Nullable ItemStack target,
            int targetLevel,
            @Nullable EnhancementTargetProvider provider,
            @Nullable AffixLayerCodec affixCodec) {
        VariableContext.Builder builder = VariableContext.builder(null).withAll(base.toMap());
        Set<String> candidateAffixes = affixKeys(candidate, affixCodec);
        Set<String> targetAffixes = affixKeys(target, affixCodec);
        Set<String> shared = new TreeSet<>(candidateAffixes);
        shared.retainAll(targetAffixes);
        builder.with(VARIABLE_AFFIX_COUNT, candidateAffixes.size());
        builder.with(VARIABLE_TARGET_AFFIX_COUNT, targetAffixes.size());
        builder.with(VARIABLE_AFFIX_KEYS, String.join(",", new TreeSet<>(candidateAffixes)));
        builder.with(VARIABLE_TARGET_AFFIX_KEYS, String.join(",", new TreeSet<>(targetAffixes)));
        builder.with(VARIABLE_SHARED_AFFIX_COUNT, shared.size());
        builder.with(VARIABLE_SHARED_AFFIX_KEYS, String.join(",", shared));
        builder.with(VARIABLE_SAME_AFFIX_SET,
                boolFlag(!candidateAffixes.isEmpty() && candidateAffixes.equals(targetAffixes)));
        builder.with(VARIABLE_SHARED_AFFIX_LEVEL_MIN, sharedLevel(candidate, shared, affixCodec, true));
        builder.with(VARIABLE_SHARED_AFFIX_LEVEL_MAX, sharedLevel(candidate, shared, affixCodec, false));
        int candidateLevel = safeLevel(provider, candidate);
        builder.with(VARIABLE_LEVEL, candidateLevel);
        builder.with(VARIABLE_LEVEL_DELTA, candidateLevel - targetLevel);
        builder.with(VARIABLE_SAME_LEVEL, boolFlag(candidateLevel == targetLevel));
        builder.with(VARIABLE_LEVEL_AT_LEAST_TARGET, boolFlag(candidateLevel >= targetLevel));
        String candidateType = candidate == null ? "" : candidate.getType().name().toLowerCase(Locale.ROOT);
        String targetType = target == null ? "" : target.getType().name().toLowerCase(Locale.ROOT);
        builder.with(VARIABLE_ITEM_TYPE, candidateType);
        builder.with(VARIABLE_SAME_ITEM_TYPE,
                boolFlag(!candidateType.isEmpty() && candidateType.equals(targetType)));
        return builder.build();
    }

    public static @NotNull Map<String, Object> defaults() {
        return enrich(VariableContext.builder(null).build(), null, null, 0, null, null).toMap();
    }

    private static int boolFlag(boolean value) {
        return value ? 1 : 0;
    }

    private static Set<String> affixKeys(@Nullable ItemStack itemStack, @Nullable AffixLayerCodec affixCodec) {
        if (itemStack == null || itemStack.getType().isAir() || affixCodec == null) {
            return Set.of();
        }
        AffixLayer layer = affixCodec.read(itemStack);
        if (layer == null || layer.affixes().isEmpty()) {
            return Set.of();
        }
        Set<String> keys = new LinkedHashSet<>();
        for (AffixState state : layer.affixes().values()) {
            if (state.enhanced() && Texts.isNotBlank(state.attributeKey())) {
                keys.add(state.attributeKey());
            }
        }
        return Set.copyOf(keys);
    }

    private static int sharedLevel(@Nullable ItemStack candidate,
            Set<String> shared,
            @Nullable AffixLayerCodec affixCodec,
            boolean minimum) {
        if (candidate == null || shared.isEmpty() || affixCodec == null) {
            return 0;
        }
        AffixLayer layer = affixCodec.read(candidate);
        if (layer == null) {
            return 0;
        }
        int best = minimum ? Integer.MAX_VALUE : 0;
        for (String key : shared) {
            int level = layer.affix(key).level();
            best = minimum ? Math.min(best, level) : Math.max(best, level);
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    private static int safeLevel(@Nullable EnhancementTargetProvider provider, @Nullable ItemStack itemStack) {
        if (provider == null || itemStack == null || itemStack.getType().isAir()) {
            return 0;
        }
        try {
            return Math.max(0, provider.readLevel(itemStack));
        } catch (RuntimeException | LinkageError exception) {
            return 0;
        }
    }
}
