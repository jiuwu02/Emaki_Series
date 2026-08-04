package emaki.jiuwu.craft.item.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EquippedSetState;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetMembership;
import emaki.jiuwu.craft.item.model.ItemSetPieceDefinition;
import emaki.jiuwu.craft.item.model.RefreshFullReason;

/**
 * 套装服务门面：对外签名不变，内部把「套装计算」委托给 {@link ItemSetPresentationCalculator}，
 * 把「监听域刷新」委托给 {@link ItemSetListenerScopeRefresher}。
 */
public final class EmakiItemSetService {

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final EmakiItemFactory itemFactory;
    private final ItemSetPresentationCalculator calculator;
    private final ItemSetListenerScopeRefresher refresher;

    public EmakiItemSetService(EmakiItemLoader itemLoader,
                               EmakiItemSetLoader setLoader,
                               EmakiItemFactory itemFactory,
                               EmakiItemIdentifier identifier,
                               EmakiItemPdcWriter pdcWriter,
                               ItemSetLoreRenderer loreRenderer,
                               Supplier<AppConfig> configSupplier) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, () -> null, null, null, null);
    }

    public EmakiItemSetService(EmakiItemLoader itemLoader,
                               EmakiItemSetLoader setLoader,
                               EmakiItemFactory itemFactory,
                               EmakiItemIdentifier identifier,
                               EmakiItemPdcWriter pdcWriter,
                               ItemSetLoreRenderer loreRenderer,
                               Supplier<AppConfig> configSupplier,
                               Supplier<DebugLogger> debugLoggerSupplier) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, debugLoggerSupplier, null, null, null);
    }

    public EmakiItemSetService(EmakiItemLoader itemLoader,
                               EmakiItemSetLoader setLoader,
                               EmakiItemFactory itemFactory,
                               EmakiItemIdentifier identifier,
                               EmakiItemPdcWriter pdcWriter,
                               ItemSetLoreRenderer loreRenderer,
                               Supplier<AppConfig> configSupplier,
                               Supplier<DebugLogger> debugLoggerSupplier,
                               Logger logger) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier,
                debugLoggerSupplier, logger, null, null);
    }

    public EmakiItemSetService(EmakiItemLoader itemLoader,
                               EmakiItemSetLoader setLoader,
                               EmakiItemFactory itemFactory,
                               EmakiItemIdentifier identifier,
                               EmakiItemPdcWriter pdcWriter,
                               ItemSetLoreRenderer loreRenderer,
                               Supplier<AppConfig> configSupplier,
                               Supplier<DebugLogger> debugLoggerSupplier,
                               Logger logger,
                               ThreadOwnership threadOwnership) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier,
                debugLoggerSupplier, logger, null, threadOwnership);
    }

    EmakiItemSetService(EmakiItemLoader itemLoader,
                        EmakiItemSetLoader setLoader,
                        EmakiItemFactory itemFactory,
                        EmakiItemIdentifier identifier,
                        EmakiItemPdcWriter pdcWriter,
                        ItemSetLoreRenderer loreRenderer,
                        Supplier<AppConfig> configSupplier,
                        ItemOperationLedger itemOperationLedger) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier,
                () -> null, null, itemOperationLedger, null);
    }

    private EmakiItemSetService(EmakiItemLoader itemLoader,
                                EmakiItemSetLoader setLoader,
                                EmakiItemFactory itemFactory,
                                EmakiItemIdentifier identifier,
                                EmakiItemPdcWriter pdcWriter,
                                ItemSetLoreRenderer loreRenderer,
                                Supplier<AppConfig> configSupplier,
                                Supplier<DebugLogger> debugLoggerSupplier,
                                Logger logger,
                                ItemOperationLedger itemOperationLedger,
                                ThreadOwnership threadOwnership) {
        this.itemFactory = itemFactory;
        Supplier<DebugLogger> effectiveDebugLoggerSupplier = debugLoggerSupplier == null ? () -> null : debugLoggerSupplier;
        ItemOperationLedger effectiveLedger = itemOperationLedger == null
                ? new ItemOperationLedger(effectiveDebugLoggerSupplier)
                : itemOperationLedger;
        this.calculator = new ItemSetPresentationCalculator(identifier, pdcWriter, loreRenderer, effectiveLedger);
        this.refresher = new ItemSetListenerScopeRefresher(
                itemLoader,
                setLoader,
                identifier,
                effectiveLedger,
                this.calculator,
                configSupplier,
                effectiveDebugLoggerSupplier,
                logger,
                threadOwnership);
    }

    public ItemRefreshBatch createRefreshBatch(Player player) {
        return refresher.createRefreshBatch(player);
    }

    public int refreshEquippedSets(Player player, String trigger) {
        return refresher.refreshEquippedSets(player, trigger);
    }

    public int refreshListenerScope(Player player,
                                    String trigger,
                                    Set<Integer> dirtySlots,
                                    boolean forceFull,
                                    boolean contributionDirty) {
        return refresher.refreshListenerScope(player, trigger, dirtySlots, forceFull, contributionDirty);
    }

    public ItemRefreshResult refreshListenerScopeDetailed(Player player,
                                                           Iterable<String> triggers,
                                                           Set<Integer> dirtySlots,
                                                           boolean forceFull,
                                                           boolean contributionDirty,
                                                           Set<RefreshFullReason> requestedFullReasons) {
        return refresher.refreshListenerScopeDetailed(
                player, triggers, dirtySlots, forceFull, contributionDirty, requestedFullReasons);
    }

    public ItemRefreshResult refreshListenerScopeDetailed(Player player,
                                                           Iterable<String> triggers,
                                                           Set<Integer> dirtySlots,
                                                           boolean forceFull,
                                                           boolean contributionDirty,
                                                           Set<RefreshFullReason> requestedFullReasons,
                                                           ItemRefreshBatch sharedBatch) {
        return refresher.refreshListenerScopeDetailed(
                player, triggers, dirtySlots, forceFull, contributionDirty, requestedFullReasons, sharedBatch);
    }

    public void clearCachedState(java.util.UUID uuid) {
        refresher.clearCachedState(uuid);
    }

    public void invalidateCachedState(java.util.UUID uuid) {
        refresher.invalidateCachedState(uuid);
    }

    public void clearAllCachedState() {
        refresher.clearAllCachedState();
    }

    ItemStack clearSetPresentation(ItemStack itemStack, EmakiItemDefinition definition) {
        return calculator.clearSetPresentation(itemStack, definition);
    }

    ItemStack renderSetItem(ItemStack itemStack,
                            EmakiItemDefinition definition,
                            ItemSetMembership membership,
                            EquippedSetState state) {
        return calculator.renderSetItem(itemStack, definition, membership, state);
    }

    static List<String> stripTrailingSetLoreBlocks(List<String> lore, List<String> setLore) {
        return ItemSetPresentationCalculator.stripTrailingSetLoreBlocks(lore, setLore);
    }

    static List<String> canonicalLoreLines(List<String> lore) {
        return ItemSetPresentationCalculator.canonicalLoreLines(lore);
    }

    static List<String> staticLoreBlock(List<String> baseLore, List<String> setLore) {
        return ItemSetPresentationCalculator.staticLoreBlock(baseLore, setLore);
    }

    static String thresholdOperationId(String setId) {
        return ItemSetPresentationCalculator.thresholdOperationId(setId);
    }

    static String staticLoreOperationId(String setId) {
        return ItemSetPresentationCalculator.staticLoreOperationId(setId);
    }

    static ItemSetPieceDefinition resolveSetPiece(ItemSetDefinition setDefinition,
                                                  ItemSetMembership membership,
                                                  String itemId) {
        return ItemSetListenerScopeRefresher.resolveSetPiece(setDefinition, membership, itemId);
    }

    static SetStateSnapshot buildStates(Set<String> visibleSetIds,
                                        Map<String, Set<String>> equippedPiecesBySet,
                                        Function<String, ItemSetDefinition> definitionResolver) {
        Map<String, EquippedSetState> states = new LinkedHashMap<>();
        Set<String> missingDefinitions = new LinkedHashSet<>();
        for (String setId : visibleSetIds == null ? Set.<String>of() : visibleSetIds) {
            if (Texts.isBlank(setId)) {
                continue;
            }
            ItemSetDefinition definition = definitionResolver == null ? null : definitionResolver.apply(setId);
            if (definition == null) {
                missingDefinitions.add(setId);
                continue;
            }
            Set<String> equippedPieces = equippedPiecesBySet == null
                    ? Set.of()
                    : equippedPiecesBySet.getOrDefault(setId, Set.of());
            states.put(setId, new EquippedSetState(definition, equippedPieces));
        }
        return new SetStateSnapshot(states, missingDefinitions);
    }

    record SetStateSnapshot(Map<String, EquippedSetState> states, Set<String> missingDefinitions) {

        SetStateSnapshot {
            states = states == null || states.isEmpty() ? Map.of() : Map.copyOf(states);
            missingDefinitions = missingDefinitions == null || missingDefinitions.isEmpty()
                    ? Set.of()
                    : Set.copyOf(missingDefinitions);
        }
    }

}
