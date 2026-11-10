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

final class EnhancementAttemptServiceDelegate2 {

    private final EnhancementAttemptService ownerRef;

    EnhancementAttemptServiceDelegate2(EnhancementAttemptService ownerRef) {
        this.ownerRef = ownerRef;
    }

    List<EnhancementAttemptService.PityView> loadPityTracks(EnhancementRecipe recipe, Player player, ItemStack target, EnhancementTargetProvider provider, VariableContext variables) {
        if (ownerRef.pityStateStore == null || recipe.pityTracks().isEmpty()) {
            return List.of();
        }
        List<EnhancementAttemptService.PityView> tracks = new ArrayList<>();
        for (EnhancementRecipe.PityConfig config : recipe.pityTracks()) {
            EnhancementAttemptService.PityView track = ownerRef.loadPityTrack(config, recipe, player, target, provider, variables);
            if (track.bound()) {
                tracks.add(track);
            }
        }
        return List.copyOf(tracks);
    }

    String isolatedGroup(EnhancementRecipe.PityConfig pity, EnhancementRecipe recipe, Player player, ItemStack target, EnhancementTargetProvider provider) {
        String group = pity.counter().group();
        if (pity.isolate().isEmpty()) {
            return group;
        }
        StringBuilder builder = new StringBuilder(group);
        for (PityIsolationEnum dimension : pity.isolate()) {
            builder.append('#').append(dimension.token()).append('=')
                    .append(ownerRef.isolationValue(dimension, recipe, player, target, provider));
        }
        return builder.toString();
    }

    String isolationValue(PityIsolationEnum dimension, EnhancementRecipe recipe, Player player, ItemStack target, EnhancementTargetProvider provider) {
        return switch (dimension) {
            case RECIPE -> Texts.lower(recipe.id());
            case MODE -> Texts.lower(recipe.mode());
            case TARGET -> Texts.lower(recipe.target().provider());
            case AFFIX -> ownerRef.selectedAffixKey(player);
            case LEVEL -> Integer.toString(ownerRef.safeReadLevel(provider, player, target));
        };
    }

    String selectedAffixKey(Player player) {
        if (ownerRef.plugin == null || ownerRef.plugin.affixSelectionService() == null || player == null) {
            return "";
        }
        try {
            return Texts.lower(ownerRef.plugin.affixSelectionService().selected(player.getUniqueId(), List.of()));
        } catch (RuntimeException | LinkageError exception) {
            ownerRef.warn("读取词条选择用于 pity 隔离失败", exception);
            return "";
        }
    }

    int safeReadLevel(EnhancementTargetProvider provider, Player player, ItemStack target) {
        if (provider == null || target == null) {
            return 0;
        }
        try {
            return Math.max(0, provider.readLevel(player, target));
        } catch (RuntimeException | LinkageError exception) {
            ownerRef.warn("读取目标等级用于 pity 隔离失败", exception);
            return 0;
        }
    }

    List<EnhancementAttemptService.PityView> updatePityTracks(List<EnhancementAttemptService.PityView> tracks, boolean success) {
        if (ownerRef.pityStateStore == null || tracks.isEmpty()) {
            return tracks;
        }
        List<EnhancementAttemptService.PityView> updated = new ArrayList<>(tracks.size());
        for (EnhancementAttemptService.PityView track : tracks) {
            updated.add(ownerRef.updatePity(track, success));
        }
        return List.copyOf(updated);
    }

    EnhancementAttemptService.PityView updatePity(EnhancementAttemptService.PityView pity, boolean success) {
        if (!pity.bound() || ownerRef.pityStateStore == null) {
            return pity;
        }
        EnhancementRecipe.PityConfig config = pity.config();
        int counter = pity.counter();
        if (success) {
            PityDecayTypeEnum decayType = config == null || config.decay() == null
                    ? PityDecayTypeEnum.RESET
                    : config.decay().type();
            double decayValue = config == null || config.decay() == null ? 0D : config.decay().value();
            counter = switch (decayType) {
                case RESET -> 0;
                case FIXED_DECAY -> (int) Math.max(0L,
                        (long) counter - Math.min((long) Integer.MAX_VALUE, Math.round(decayValue)));
                case PROPORTIONAL -> Math.max(0, (int) Math.floor(counter * (1D - decayValue)));
            };
        } else {
            counter = counter >= Integer.MAX_VALUE ? Integer.MAX_VALUE : counter + 1;
        }
        PityState next = new PityState(counter, System.currentTimeMillis(), pity.triggered());
        ownerRef.pityStateStore.save(pity.scope().name(), pity.group(), pity.key(), next);
        return new EnhancementAttemptService.PityView(pity.scope(), pity.group(), pity.key(), counter, pity.triggered(), config);
    }

    EnhancementPityResult toPityResult(@Nullable List<EnhancementAttemptService.PityView> tracks) {
        if (tracks == null || tracks.isEmpty()) {
            return EnhancementPityResult.empty();
        }
        List<EnhancementPityTrack> published = new ArrayList<>(tracks.size());
        for (EnhancementAttemptService.PityView pity : tracks) {
            if (!pity.bound()) {
                continue;
            }
            published.add(new EnhancementPityTrack(
                    pity.scope().name().toLowerCase(Locale.ROOT),
                    pity.group(),
                    pity.counter(),
                    pity.triggered()));
        }
        return EnhancementPityResult.ofTracks(published);
    }

    boolean requiresItemPityOwner(EnhancementRecipe recipe) {
        for (EnhancementRecipe.PityConfig track : recipe.pityTracks()) {
            if (track.counter().scope() == PityScopeEnum.ITEM) {
                return true;
            }
        }
        return false;
    }

    String pityKey(PityScopeEnum scope, Player player, ItemStack target, EnhancementTargetProvider provider) {
        if (scope == PityScopeEnum.PLAYER) {
            return player.getUniqueId().toString();
        }
        ItemMeta meta = target == null ? null : target.getItemMeta();
        String ownerId = meta == null ? null
                : meta.getPersistentDataContainer().get(EnhancementAttemptService.PITY_OWNER_KEY, PersistentDataType.STRING);
        return Texts.isBlank(ownerId) ? "" : ownerId;
    }

}
