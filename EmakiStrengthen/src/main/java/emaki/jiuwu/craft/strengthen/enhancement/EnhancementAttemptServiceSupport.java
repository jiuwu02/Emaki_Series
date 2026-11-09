package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import emaki.jiuwu.craft.corelib.api.condition.ConditionContext;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.math.CraftRollEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.craft.CraftOperationJournal;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.corelib.matcher.MatchContext;
import emaki.jiuwu.craft.corelib.matcher.Matcher;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRenderer;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementOperationView;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityResult;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityTrack;
import emaki.jiuwu.craft.strengthen.api.target.EnhancementTargetProvider;
import emaki.jiuwu.craft.strengthen.enhancement.affix.AffixLayerCodec;
import emaki.jiuwu.craft.strengthen.enhancement.cost.ConsumeTimingEnum;
import emaki.jiuwu.craft.strengthen.enhancement.cost.MaterialSlotConfig;
import emaki.jiuwu.craft.strengthen.enhancement.cost.TargetCompareEnum;
import emaki.jiuwu.craft.strengthen.enhancement.mastery.MasteryProgressService;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityDecayTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityIsolationEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityEffectTypeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityScopeEnum;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityState;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.progression.EnhancementProgressionResolver;
import emaki.jiuwu.craft.strengthen.enhancement.recipe.EnhancementRecipe;
import emaki.jiuwu.craft.strengthen.enhancement.target.EnhancementTargetRegistry;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;

final class EnhancementAttemptServiceSupport {

    static String operationIdOf(String journalKey) {
        if (Texts.isBlank(journalKey)) {
            return "";
        }
        int separator = journalKey.indexOf(':');
        return separator < 0 ? journalKey : journalKey.substring(separator + 1);
}

    static String safeProviderId(EnhancementTargetProvider provider) {
        try {
            return Texts.toStringSafe(provider.id());
        } catch (RuntimeException | LinkageError exception) {
            return "";
        }
}

    static boolean satisfiesTargetCompare(TargetCompareEnum compare, VariableContext variables) {
        Map<String, Object> values = variables.toMap();
        return switch (compare) {
            case NONE -> true;
            case SAME_AFFIX -> flag(values, EnhancementMaterialVariables.VARIABLE_SHARED_AFFIX_COUNT) > 0;
            case SAME_AFFIX_SET -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_AFFIX_SET) == 1;
            case SAME_ITEM_TYPE -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_ITEM_TYPE) == 1;
            case SAME_LEVEL -> flag(values, EnhancementMaterialVariables.VARIABLE_SAME_LEVEL) == 1;
            case LEVEL_AT_LEAST -> flag(values, EnhancementMaterialVariables.VARIABLE_LEVEL_AT_LEAST_TARGET) == 1;
        };
}

    static int flag(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : 0;
}

    static boolean sameFilterValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return Double.compare(actualNumber.doubleValue(), expectedNumber.doubleValue()) == 0;
        }
        return String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
}

    static int saturatedAdd(int left, int right) {
        long value = (long) Math.max(0, left) + Math.max(0, right);
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
}

    static boolean shouldConsume(ConsumeTimingEnum timing, boolean success) {
        return switch (timing) {
            case ALWAYS -> true;
            case SUCCESS -> success;
            case FAILURE -> !success;
            case NEVER -> false;
        };
}

    static double clampChance(double value) {
        if (!Double.isFinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
}

    static int finitePositiveInt(double value, int fallback) {
        if (!Double.isFinite(value) || value <= 0D) {
            return fallback;
        }
        if (value >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.ceil(value);
}
}
