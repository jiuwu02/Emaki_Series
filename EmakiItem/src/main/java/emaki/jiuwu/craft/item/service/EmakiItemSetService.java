package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationEntry;
import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.config.AppConfig;
import emaki.jiuwu.craft.item.loader.EmakiItemLoader;
import emaki.jiuwu.craft.item.loader.EmakiItemSetLoader;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.EquippedSetState;
import emaki.jiuwu.craft.item.model.ItemSetDefinition;
import emaki.jiuwu.craft.item.model.ItemSetMembership;
import emaki.jiuwu.craft.item.model.ItemSetPieceDefinition;
import emaki.jiuwu.craft.item.model.ItemSetThreshold;

public final class EmakiItemSetService {

    private static final String SET_DISPLAY_NAMESPACE = "emakiitem:set_display";

    private final EmakiItemLoader itemLoader;
    private final EmakiItemSetLoader setLoader;
    private final EmakiItemFactory itemFactory;
    private final EmakiItemIdentifier identifier;
    private final EmakiItemPdcWriter pdcWriter;
    private final ItemSetLoreRenderer loreRenderer;
    private final ItemOperationLedger itemOperationLedger;
    private final Supplier<AppConfig> configSupplier;
    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final Logger logger;
    private final Set<String> warnedMissingSetDefinitions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // 记录每个玩家上次各套装的激活件数，用于边沿触发 ItemSetBonusChangeEvent，避免每次背包刷新都派发。
    private final Map<java.util.UUID, Map<String, Integer>> lastActiveCounts = new java.util.concurrent.ConcurrentHashMap<>();

    public EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            Supplier<AppConfig> configSupplier) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, () -> null, null, null);
    }

    public EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            Supplier<AppConfig> configSupplier,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, debugLoggerSupplier, null, null);
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
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, debugLoggerSupplier, logger, null);
    }

    EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            Supplier<AppConfig> configSupplier,
            ItemOperationLedger itemOperationLedger) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, () -> null, null, itemOperationLedger);
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
            ItemOperationLedger itemOperationLedger) {
        this.itemLoader = itemLoader;
        this.setLoader = setLoader;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.pdcWriter = pdcWriter;
        this.loreRenderer = loreRenderer;
        this.configSupplier = configSupplier;
        this.debugLoggerSupplier = debugLoggerSupplier == null ? () -> null : debugLoggerSupplier;
        this.logger = logger;
        this.itemOperationLedger = itemOperationLedger == null ? new ItemOperationLedger(this.debugLoggerSupplier) : itemOperationLedger;
    }

    public int refreshEquippedSets(Player player, String trigger) {
        if (player == null) {
            return 0;
        }
        AppConfig config = configSupplier.get();
        if (config != null && !config.setBonus().triggerEnabled(trigger)) {
            return 0;
        }
        List<EquippedItem> equippedItems = readEquippedItems(player);
        Set<String> visibleSetIds = collectVisibleSetIds(player, equippedItems);
        Map<String, Set<String>> equippedPiecesBySet = collectEquippedPieces(player, trigger, equippedItems);
        SetStateSnapshot stateSnapshot = buildStates(visibleSetIds, equippedPiecesBySet, setLoader::get);
        Map<String, EquippedSetState> states = stateSnapshot.states();
        debugSetStateSnapshot(player, trigger, visibleSetIds, equippedPiecesBySet, stateSnapshot);
        int changed = 0;
        for (EquippedItem equippedItem : equippedItems) {
            ItemStack snapshot = equippedItem.itemStack();
            if (snapshot == null || snapshot.getType().isAir()) {
                continue;
            }
            String id = identifier.identify(snapshot);
            EmakiItemDefinition definition = Texts.isBlank(id) ? null : itemLoader.get(id);
            if (definition == null) {
                continue;
            }
            ItemSetMembership membership = definition.setMembership();
            String existingSignature = identifier.setSignature(snapshot);
            Integer existingLoreLines = identifier.setLoreLines(snapshot);
            if (membership.configured()) {
                EquippedSetState state = states.get(membership.setId());
                if (state == null) {
                    diagnoseMissingSetDefinition(player, trigger, equippedItem.slot(), definition, membership);
                    continue;
                }
                int previousLoreSize = loreSize(snapshot);
                SetPresentationTarget target = buildPresentationTarget(definition, state);
                SetPresentationInspection beforeInspection = inspectSetPresentation(snapshot, membership.setId(), target);
                ItemStack rendered = renderSetItem(snapshot.clone(), definition, membership, state, target, beforeInspection);
                boolean presentationChanged = !snapshot.equals(rendered);
                boolean committed = !presentationChanged
                        || equippedItem.writeIfUnchanged(player.getInventory(), snapshot, rendered);
                debugSetLore(player, trigger, equippedItem.slot(), definition.id(), existingSignature, existingLoreLines,
                        previousLoreSize, rendered, presentationChanged, committed, beforeInspection,
                        membership.setId(), target);
                if (presentationChanged && committed) {
                    changed++;
                }
            } else if (hasSetPresentation(snapshot)) {
                ItemStack cleared = clearSetPresentation(snapshot.clone(), definition);
                boolean committed = equippedItem.writeIfUnchanged(player.getInventory(), snapshot, cleared);
                debugSetWrite(player, trigger, equippedItem.slot(), definition.id(), "clear", committed);
                if (committed) {
                    changed++;
                }
            }
        }
        changed += cleanInventorySetLore(player, states, trigger);
        fireSetBonusChangeEvents(player, states, stateSnapshot.missingDefinitions(), trigger);
        return changed;
    }

    private void fireSetBonusChangeEvents(Player player,
            Map<String, EquippedSetState> states,
            Set<String> missingDefinitions,
            String trigger) {
        java.util.UUID uuid = player.getUniqueId();
        Map<String, Integer> previous = lastActiveCounts.getOrDefault(uuid, Map.of());
        Map<String, Integer> current = new LinkedHashMap<>();
        for (Map.Entry<String, EquippedSetState> entry : states.entrySet()) {
            EquippedSetState state = entry.getValue();
            if (state != null && state.activeCount() > 0) {
                current.put(entry.getKey(), state.activeCount());
            }
        }
        for (String setId : missingDefinitions == null ? Set.<String>of() : missingDefinitions) {
            Integer preservedCount = previous.get(setId);
            if (preservedCount != null && preservedCount > 0) {
                current.putIfAbsent(setId, preservedCount);
            }
        }
        // 套装激活件数变化对外开放，after 边沿通知；仅在主线程派发。
        boolean primaryThread = org.bukkit.Bukkit.isPrimaryThread();
        Set<String> setIds = new LinkedHashSet<>(previous.keySet());
        setIds.addAll(current.keySet());
        for (String setId : setIds) {
            int oldCount = previous.getOrDefault(setId, 0);
            int newCount = current.getOrDefault(setId, 0);
            if (oldCount == newCount) {
                continue;
            }
            if (primaryThread) {
                EquippedSetState state = states.get(setId);
                int totalPieces = state != null && state.definition() != null ? state.definition().totalPieces() : 0;
                List<Integer> activeThresholds = state == null
                        ? List.of()
                        : state.activeThresholds().stream().map(ItemSetThreshold::requiredPieces).toList();
                org.bukkit.Bukkit.getPluginManager().callEvent(new emaki.jiuwu.craft.item.api.event.ItemSetBonusChangeEvent(
                        player, setId, oldCount, newCount, totalPieces, activeThresholds, trigger));
            }
        }
        if (current.isEmpty()) {
            lastActiveCounts.remove(uuid);
        } else {
            lastActiveCounts.put(uuid, current);
        }
    }

    /** Clears cached set state for a player (e.g. on quit) to avoid leaks. */
    public void clearCachedState(java.util.UUID uuid) {
        if (uuid != null) {
            lastActiveCounts.remove(uuid);
        }
    }

    private int cleanInventorySetLore(Player player,
            Map<String, EquippedSetState> states,
            String trigger) {
        PlayerInventory inventory = player.getInventory();
        int changed = 0;
        Set<Integer> equippedSlots = new java.util.HashSet<>(Set.of(40, 39, 38, 37, 36));
        equippedSlots.add(inventory.getHeldItemSlot());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (equippedSlots.contains(slot)) {
                continue;
            }
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType().isAir()) {
                continue;
            }
            ItemStack snapshot = current.clone();
            String id = identifier.identify(snapshot);
            EmakiItemDefinition definition = Texts.isBlank(id) ? null : itemLoader.get(id);
            if (definition == null) {
                continue;
            }
            ItemSetMembership membership = definition.setMembership();
            if (membership.configured()) {
                EquippedSetState state = states.get(membership.setId());
                if (state == null) {
                    diagnoseMissingSetDefinition(player, trigger, "slot_" + slot, definition, membership);
                    continue;
                }
                String existingSignature = identifier.setSignature(snapshot);
                Integer existingLoreLines = identifier.setLoreLines(snapshot);
                int previousLoreSize = loreSize(snapshot);
                SetPresentationTarget target = buildPresentationTarget(definition, state);
                SetPresentationInspection beforeInspection = inspectSetPresentation(snapshot, membership.setId(), target);
                ItemStack rendered = renderSetItem(snapshot.clone(), definition, membership, state, target, beforeInspection);
                boolean presentationChanged = !snapshot.equals(rendered);
                boolean committed = !presentationChanged
                        || writeInventoryIfUnchanged(inventory, slot, snapshot, rendered);
                debugSetLore(player, "inventory", "slot_" + slot, definition.id(), existingSignature, existingLoreLines,
                        previousLoreSize, rendered, presentationChanged, committed, beforeInspection,
                        membership.setId(), target);
                if (presentationChanged && committed) {
                    changed++;
                }
            } else if (hasSetPresentation(snapshot)) {
                ItemStack cleared = clearSetPresentation(snapshot.clone(), definition);
                boolean committed = writeInventoryIfUnchanged(inventory, slot, snapshot, cleared);
                debugSetWrite(player, "inventory", "slot_" + slot, definition.id(), "clear", committed);
                if (committed) {
                    changed++;
                }
            }
        }
        return changed;
    }

    ItemStack clearSetPresentation(ItemStack itemStack, EmakiItemDefinition definition) {
        ItemStack updated = itemStack;
        Integer previousSetLoreLines = identifier.setLoreLines(updated);
        String setId = definition != null && definition.setMembership().configured()
                ? definition.setMembership().setId()
                : identifier.setId(updated);
        boolean staticLoreReverted = false;
        List<String> recordedStaticBlock = List.of();
        if (Texts.isNotBlank(setId)) {
            recordedStaticBlock = staticOperationBlock(itemOperationLedger.find(updated, staticLoreOperationId(setId)));
            itemOperationLedger.revert(updated, thresholdOperationId(setId));
            staticLoreReverted = itemOperationLedger.revert(updated, staticLoreOperationId(setId));
        }
        if (!staticLoreReverted) {
            stripSetLore(updated, previousSetLoreLines);
        }
        stripTrailingExactLoreBlocks(updated, recordedStaticBlock);
        pdcWriter.clearDynamicSet(updated, definition);
        return updated;
    }

    private boolean hasSetPresentation(ItemStack itemStack) {
        return Texts.isNotBlank(identifier.setSignature(itemStack)) || identifier.setLoreLines(itemStack) != null;
    }

    private void stripSetLore(ItemStack itemStack, Integer setLoreLines) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<String> strippedLore = stripPreviousSetLore(ItemTextBridge.loreLines(itemMeta), setLoreLines);
        ItemTextBridge.setLoreLines(itemMeta, strippedLore);
        itemStack.setItemMeta(itemMeta);
    }

    static List<String> stripPreviousSetLore(List<String> lore, Integer setLoreLines) {
        List<String> result = lore == null || lore.isEmpty() ? new ArrayList<>() : new ArrayList<>(lore);
        int lines = setLoreLines == null ? 0 : Math.max(0, setLoreLines);
        if (lines <= 0 || result.isEmpty()) {
            return result;
        }
        int keep = Math.max(0, result.size() - lines);
        return new ArrayList<>(result.subList(0, keep));
    }

    private void stripMatchingSetLore(ItemStack itemStack, List<String> setLore) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<String> currentLore = ItemTextBridge.loreLines(itemMeta);
        List<String> strippedLore = stripMatchingLoreBlocks(currentLore, setLore, true);
        if (currentLore.equals(strippedLore)) {
            return;
        }
        ItemTextBridge.setLoreLines(itemMeta, strippedLore);
        itemStack.setItemMeta(itemMeta);
    }

    static List<String> stripTrailingSetLoreBlocks(List<String> lore, List<String> setLore) {
        List<String> result = lore == null || lore.isEmpty() ? new ArrayList<>() : new ArrayList<>(lore);
        List<String> canonicalSetLore = canonicalLoreLines(setLore);
        if (canonicalSetLore.isEmpty()) {
            return result;
        }
        while (endsWith(result, canonicalSetLore)) {
            result.subList(result.size() - canonicalSetLore.size(), result.size()).clear();
            removeSeparatorBefore(result, result.size());
        }
        return result;
    }

    static List<String> stripMatchingLoreBlocks(List<String> lore, List<String> block, boolean removeSeparator) {
        List<String> result = lore == null || lore.isEmpty() ? new ArrayList<>() : new ArrayList<>(lore);
        List<String> canonicalBlock = canonicalLoreLines(block);
        if (canonicalBlock.isEmpty()) {
            return result;
        }
        for (int start = result.size() - canonicalBlock.size(); start >= 0; start--) {
            if (!matchesAt(result, canonicalBlock, start)) {
                continue;
            }
            result.subList(start, start + canonicalBlock.size()).clear();
            if (removeSeparator) {
                removeSeparatorBefore(result, start);
            }
        }
        return result;
    }

    private void stripTrailingExactLoreBlocks(ItemStack itemStack, List<String> block) {
        if (itemStack == null || itemStack.getType().isAir() || block == null || block.isEmpty()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<String> currentLore = ItemTextBridge.loreLines(itemMeta);
        List<String> strippedLore = stripMatchingLoreBlocks(currentLore, block, false);
        if (!currentLore.equals(strippedLore)) {
            ItemTextBridge.setLoreLines(itemMeta, strippedLore);
            itemStack.setItemMeta(itemMeta);
        }
    }

    private List<String> staticOperationBlock(ItemOperationEntry operation) {
        if (operation == null || operation.loreRecords().size() != 1) {
            return List.of();
        }
        ItemOperationEntry.LoreOperationRecord record = operation.loreRecords().getFirst();
        return "append".equals(record.action()) ? record.renderedLines() : List.of();
    }

    private static boolean endsWith(List<String> lines, List<String> suffix) {
        return lines != null
                && suffix != null
                && !suffix.isEmpty()
                && lines.size() >= suffix.size()
                && matchesAt(lines, suffix, lines.size() - suffix.size());
    }

    private static boolean matchesAt(List<String> lines, List<String> canonicalBlock, int start) {
        if (lines == null || canonicalBlock == null || canonicalBlock.isEmpty()
                || start < 0 || start + canonicalBlock.size() > lines.size()) {
            return false;
        }
        for (int offset = 0; offset < canonicalBlock.size(); offset++) {
            if (!canonicalLoreLine(lines.get(start + offset)).equals(canonicalBlock.get(offset))) {
                return false;
            }
        }
        return true;
    }

    private static void removeSeparatorBefore(List<String> lines, int start) {
        int separatorIndex = start - 1;
        if (separatorIndex >= 0 && MiniMessages.plainText(lines.get(separatorIndex)).isBlank()) {
            lines.remove(separatorIndex);
        }
    }

    static List<String> canonicalLoreLines(List<String> lore) {
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        List<String> canonical = new ArrayList<>(lore.size());
        for (String line : lore) {
            canonical.add(canonicalLoreLine(line));
        }
        return List.copyOf(canonical);
    }

    private static String canonicalLoreLine(String line) {
        return MiniMessages.serialize(MiniMessages.parse(Texts.toStringSafe(line)));
    }

    static boolean isSetPresentationCurrent(String existingSignature,
            Integer existingLoreLines,
            String expectedSignature,
            List<String> expectedSetLore,
            int actualSetLoreBlocks,
            int staticOperationLoreLines,
            boolean thresholdOperationCurrent) {
        if (!java.util.Objects.equals(Texts.toStringSafe(existingSignature), Texts.toStringSafe(expectedSignature))) {
            return false;
        }
        boolean expectsStaticLore = expectedSetLore != null && !expectedSetLore.isEmpty();
        int expectedBlocks = expectsStaticLore ? 1 : 0;
        int expectedLoreLines = expectsStaticLore ? staticOperationLoreLines : 0;
        return staticOperationLoreLines >= 0
                && existingLoreLines != null
                && existingLoreLines == expectedLoreLines
                && actualSetLoreBlocks == expectedBlocks
                && thresholdOperationCurrent;
    }

    static List<String> staticLoreBlock(List<String> baseLore, List<String> setLore) {
        List<String> canonicalSetLore = canonicalLoreLines(setLore);
        if (canonicalSetLore.isEmpty()) {
            return List.of();
        }
        List<String> block = new ArrayList<>();
        if (baseLore != null && !baseLore.isEmpty()) {
            block.add("");
        }
        block.addAll(canonicalSetLore);
        return List.copyOf(block);
    }

    ItemStack renderSetItem(ItemStack itemStack,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            EquippedSetState state) {
        if (itemStack == null || definition == null || state == null || state.definition() == null) {
            return itemStack;
        }
        SetPresentationTarget target = buildPresentationTarget(definition, state);
        SetPresentationInspection inspection = inspectSetPresentation(itemStack, membership.setId(), target);
        return renderSetItem(itemStack, definition, membership, state, target, inspection);
    }

    private ItemStack renderSetItem(ItemStack itemStack,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            EquippedSetState state,
            SetPresentationTarget target,
            SetPresentationInspection inspection) {
        Integer previousSetLoreLines = identifier.setLoreLines(itemStack);
        if (inspection.current()) {
            return itemStack;
        }

        String setId = membership.setId();
        itemOperationLedger.revert(itemStack, thresholdOperationId(setId));
        boolean staticLoreReverted = itemOperationLedger.revert(itemStack, staticLoreOperationId(setId));
        if (!staticLoreReverted) {
            stripSetLore(itemStack, previousSetLoreLines);
        }
        // 即使账本声称回滚成功，也要清理任意位置残留的完整套装块，避免异常状态被再次追加或固化。
        stripMatchingSetLore(itemStack, target.setLore());

        int staticLoreLines = applyStaticSetLore(itemStack, setId, target.setLore());
        applySetDisplayActions(itemStack, definition, membership, state, target.nameActions(), target.loreActions());
        pdcWriter.writeDynamicSet(
                itemStack,
                definition,
                membership.setId(),
                membership.effectivePieceId(definition.id()),
                state.activeCount(),
                state.definition().totalPieces(),
                target.activeThresholdNumbers(),
                staticLoreLines,
                state.mergedAttributes(),
                state.mergedSkills(),
                target.signature()
        );
        return itemStack;
    }

    private SetPresentationTarget buildPresentationTarget(EmakiItemDefinition definition, EquippedSetState state) {
        List<String> setLore = canonicalLoreLines(loreRenderer.render(state));
        List<Integer> activeThresholdNumbers = state.activeThresholds().stream()
                .map(ItemSetThreshold::requiredPieces)
                .toList();
        Object nameActions = state.mergedNameActions();
        Object loreActions = state.mergedLoreActions();
        boolean expectsThresholdLoreOperation = hasActions(loreActions);
        boolean expectsThresholdOperation = hasActions(nameActions) || expectsThresholdLoreOperation;
        String signature = buildSetSignature(
                definition,
                state,
                activeThresholdNumbers,
                setLore,
                nameActions,
                loreActions
        );
        return new SetPresentationTarget(
                List.copyOf(setLore),
                activeThresholdNumbers,
                nameActions,
                loreActions,
                signature,
                expectsThresholdOperation,
                expectsThresholdLoreOperation
        );
    }

    private SetPresentationInspection inspectSetPresentation(ItemStack itemStack,
            String setId,
            SetPresentationTarget target) {
        List<String> lore = loreLines(itemStack);
        ItemOperationEntry staticOperation = itemOperationLedger.find(itemStack, staticLoreOperationId(setId));
        ItemOperationEntry thresholdOperation = itemOperationLedger.find(itemStack, thresholdOperationId(setId));
        int staticOperationLoreLines = staticOperationLoreLines(staticOperation, target.setLore());
        boolean thresholdOperationPresent = thresholdOperation != null && !thresholdOperation.isEmpty();
        List<String> blockInspectionLore = lore;
        boolean blockCountFromThresholdSnapshot = false;
        boolean thresholdLoreSnapshotCurrent = true;
        if (target.expectsThresholdLoreOperation()) {
            ItemOperationEntry.LoreOperationRecord firstLoreRecord = thresholdOperation == null
                    || thresholdOperation.loreRecords().isEmpty()
                    ? null
                    : thresholdOperation.loreRecords().getFirst();
            thresholdLoreSnapshotCurrent = firstLoreRecord != null && firstLoreRecord.beforeRecorded();
            if (thresholdLoreSnapshotCurrent) {
                blockInspectionLore = firstLoreRecord.beforeLines();
                blockCountFromThresholdSnapshot = true;
            }
        }
        int actualSetLoreBlocks = countLoreBlocks(blockInspectionLore, target.setLore());
        boolean thresholdOperationCurrent = thresholdOperationPresent == target.expectsThresholdOperation()
                && thresholdLoreSnapshotCurrent;
        Integer marker = identifier.setLoreLines(itemStack);
        String signature = identifier.setSignature(itemStack);
        boolean current = isSetPresentationCurrent(
                signature,
                marker,
                target.signature(),
                target.setLore(),
                actualSetLoreBlocks,
                staticOperationLoreLines,
                thresholdOperationCurrent
        );
        return new SetPresentationInspection(
                actualSetLoreBlocks,
                staticOperationLoreLines,
                thresholdOperationPresent,
                blockCountFromThresholdSnapshot,
                current
        );
    }

    private int staticOperationLoreLines(ItemOperationEntry operation, List<String> expectedSetLore) {
        boolean expectsStaticLore = expectedSetLore != null && !expectedSetLore.isEmpty();
        if (!expectsStaticLore) {
            return operation == null ? 0 : -1;
        }
        if (operation == null || operation.loreRecords().size() != 1) {
            return -1;
        }
        ItemOperationEntry.LoreOperationRecord record = operation.loreRecords().getFirst();
        if (!"append".equals(record.action()) || !record.beforeRecorded() || !endsWith(record.renderedLines(), expectedSetLore)) {
            return -1;
        }
        return record.renderedLines().size();
    }

    static int countLoreBlocks(List<String> lore, List<String> block) {
        List<String> canonicalBlock = canonicalLoreLines(block);
        if (lore == null || canonicalBlock.isEmpty() || lore.size() < canonicalBlock.size()) {
            return 0;
        }
        int count = 0;
        for (int start = 0; start <= lore.size() - canonicalBlock.size(); start++) {
            if (matchesAt(lore, canonicalBlock, start)) {
                count++;
                start += canonicalBlock.size() - 1;
            }
        }
        return count;
    }

    private String buildSetSignature(EmakiItemDefinition definition,
            EquippedSetState state,
            List<Integer> activeThresholdNumbers,
            List<String> setLore,
            Object nameActions,
            Object loreActions) {
        return SignatureUtil.stableSignature(List.of(
                definition.definitionSignature(),
                state.definition().id(),
                state.activeCount(),
                state.equippedPieces().stream().sorted().toList(),
                activeThresholdNumbers,
                setLore,
                nameActions,
                loreActions
        ));
    }

    private int applyStaticSetLore(ItemStack itemStack, String setId, List<String> setLore) {
        ItemMeta itemMeta = itemStack.getItemMeta();
        List<String> baseLore = itemMeta == null ? List.of() : ItemTextBridge.loreLines(itemMeta);
        List<String> block = staticLoreBlock(baseLore, setLore);
        if (block.isEmpty()) {
            return 0;
        }
        boolean applied = itemOperationLedger.apply(
                itemStack,
                staticLoreOperationId(setId),
                SET_DISPLAY_NAMESPACE,
                List.of(),
                List.of(Map.of("action", "append", "content", block)),
                Map.of()
        );
        return applied ? block.size() : 0;
    }

    private void applySetDisplayActions(ItemStack itemStack,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            EquippedSetState state,
            Object nameActions,
            Object loreActions) {
        if (itemStack == null || definition == null || membership == null || state == null) {
            return;
        }
        String operationId = thresholdOperationId(membership.setId());
        if (!hasActions(nameActions) && !hasActions(loreActions)) {
            return;
        }
        itemOperationLedger.apply(
                itemStack,
                operationId,
                SET_DISPLAY_NAMESPACE,
                nameActions,
                loreActions,
                setActionVariables(definition, membership, state)
        );
    }

    private Map<String, Object> setActionVariables(EmakiItemDefinition definition, ItemSetMembership membership, EquippedSetState state) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("item_id", definition.id());
        variables.put("set_id", membership.setId());
        variables.put("piece_id", membership.effectivePieceId(definition.id()));
        variables.put("set_name", state.definition().displayName());
        variables.put("active", state.activeCount());
        variables.put("active_count", state.activeCount());
        variables.put("total", state.definition().totalPieces());
        variables.put("total_pieces", state.definition().totalPieces());
        variables.put("active_thresholds", state.activeThresholds().stream().map(ItemSetThreshold::requiredPieces).toList());
        return variables;
    }

    static String thresholdOperationId(String setId) {
        return "emakiitem:set_display:" + Texts.normalizeId(setId);
    }

    static String staticLoreOperationId(String setId) {
        return "emakiitem:set_static_lore:" + Texts.normalizeId(setId);
    }

    private boolean hasActions(Object raw) {
        if (raw == null) return false;
        if (raw instanceof Map<?, ?> map) return !map.isEmpty();
        if (raw instanceof Iterable<?> iterable) return iterable.iterator().hasNext();
        return Texts.isNotBlank(raw);
    }

    private int loreSize(ItemStack itemStack) {
        return loreLines(itemStack).size();
    }

    private List<String> loreLines(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return List.of();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta == null ? List.of() : ItemTextBridge.loreLines(itemMeta);
    }

    private void diagnoseMissingSetDefinition(Player player,
            String trigger,
            String slot,
            EmakiItemDefinition itemDefinition,
            ItemSetMembership membership) {
        String setId = membership == null ? "" : membership.setId();
        String itemId = itemDefinition == null ? "" : itemDefinition.id();
        if (logger != null && Texts.isNotBlank(setId) && warnedMissingSetDefinitions.add(setId)) {
            logger.warning("Set definition '" + setId + "' referenced by EmakiItem '" + itemId
                    + "' is unavailable; preserving the existing set presentation and cached state.");
        }
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.logRaw("set", player, "[DEBUG:SET_STATE] trigger=" + Texts.toStringSafe(trigger)
                + " slot=" + Texts.toStringSafe(slot)
                + " item=" + itemId
                + " set=" + setId
                + " state=missing_definition"
                + " action=preserve");
    }

    private void debugSetStateSnapshot(Player player,
            String trigger,
            Set<String> visibleSetIds,
            Map<String, Set<String>> equippedPiecesBySet,
            SetStateSnapshot snapshot) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.logRaw("set", player, "[DEBUG:SET_STATE] trigger=" + Texts.toStringSafe(trigger)
                + " visible=" + (visibleSetIds == null ? Set.of() : visibleSetIds)
                + " active_pieces=" + (equippedPiecesBySet == null ? Map.of() : equippedPiecesBySet)
                + " states=" + snapshot.states().keySet()
                + " missing=" + snapshot.missingDefinitions());
    }

    private void debugSetSlot(Player player,
            String trigger,
            String actualSlot,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            ItemSetPieceDefinition pieceDefinition,
            boolean definitionSlotMatch,
            boolean setSlotMatch,
            boolean accepted) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.logRaw("set", player, "[DEBUG:SET_SLOT] trigger=" + Texts.toStringSafe(trigger)
                + " item=" + definition.id()
                + " actual=" + Texts.toStringSafe(actualSlot)
                + " item_slot=" + Texts.toStringSafe(definition.equipSlot())
                + " set=" + membership.setId()
                + " piece=" + (pieceDefinition == null ? "<unresolved>" : pieceDefinition.pieceId())
                + " set_slot=" + (pieceDefinition == null ? "<unresolved>" : pieceDefinition.slot())
                + " item_match=" + definitionSlotMatch
                + " set_match=" + setSlotMatch
                + " accepted=" + accepted);
    }

    private void debugSetLore(Player player,
            String trigger,
            String slot,
            String itemId,
            String previousSignature,
            Integer previousLoreLines,
            int previousLoreSize,
            ItemStack rendered,
            boolean changed,
            boolean committed,
            SetPresentationInspection before,
            String setId,
            SetPresentationTarget target) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        SetPresentationInspection after = inspectSetPresentation(rendered, setId, target);
        debugLogger.logRaw("set", player, "[DEBUG:SET_LORE] trigger=" + Texts.toStringSafe(trigger)
                + " slot=" + Texts.toStringSafe(slot)
                + " item=" + Texts.toStringSafe(itemId)
                + " signature=" + Texts.toStringSafe(previousSignature) + "->" + Texts.toStringSafe(identifier.setSignature(rendered))
                + " marker=" + previousLoreLines + "->" + identifier.setLoreLines(rendered)
                + " lore_size=" + previousLoreSize + "->" + loreSize(rendered)
                + " blocks=" + before.actualSetLoreBlocks() + "->" + after.actualSetLoreBlocks()
                + " block_source=" + (before.blockCountFromThresholdSnapshot() ? "threshold_before" : "item")
                + "->" + (after.blockCountFromThresholdSnapshot() ? "threshold_before" : "item")
                + " ledger_lines=" + before.staticOperationLoreLines() + "->" + after.staticOperationLoreLines()
                + " threshold_ledger=" + before.thresholdOperationPresent() + "->" + after.thresholdOperationPresent()
                + " current=" + before.current() + "->" + after.current()
                + " changed=" + changed
                + " committed=" + committed
                + " folia=" + FoliaSchedulerAdapter.isFolia()
                + " primary=" + Bukkit.isPrimaryThread()
                + " owner=" + Bukkit.isOwnedByCurrentRegion(player)
                + " thread=" + Thread.currentThread().getName());
    }

    private void debugSetWrite(Player player,
            String trigger,
            String slot,
            String itemId,
            String operation,
            boolean committed) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.logRaw("set", player, "[DEBUG:SET_WRITE] trigger=" + Texts.toStringSafe(trigger)
                + " slot=" + Texts.toStringSafe(slot)
                + " item=" + Texts.toStringSafe(itemId)
                + " operation=" + Texts.toStringSafe(operation)
                + " committed=" + committed
                + " owner=" + Bukkit.isOwnedByCurrentRegion(player)
                + " thread=" + Thread.currentThread().getName());
    }

    private List<EquippedItem> readEquippedItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        return List.of(
                new EquippedItem("main_hand", cloneItem(inventory.getItemInMainHand())),
                new EquippedItem("off_hand", cloneItem(inventory.getItemInOffHand())),
                new EquippedItem("helmet", cloneItem(inventory.getHelmet())),
                new EquippedItem("chestplate", cloneItem(inventory.getChestplate())),
                new EquippedItem("leggings", cloneItem(inventory.getLeggings())),
                new EquippedItem("boots", cloneItem(inventory.getBoots()))
        );
    }

    private Map<String, Set<String>> collectEquippedPieces(Player player,
            String trigger,
            List<EquippedItem> equippedItems) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (EquippedItem equippedItem : equippedItems) {
            String id = identifier.identify(equippedItem.itemStack());
            EmakiItemDefinition definition = Texts.isBlank(id) ? null : itemLoader.get(id);
            if (definition == null || !definition.setMembership().configured()) {
                continue;
            }
            ItemSetMembership membership = definition.setMembership();
            ItemSetDefinition setDefinition = setLoader.get(membership.setId());
            ItemSetPieceDefinition pieceDefinition = resolveSetPiece(setDefinition, membership, definition.id());
            boolean definitionSlotMatch = EquipmentSlotMatcher.matches(equippedItem.slot(), definition.equipSlot());
            boolean setSlotMatch = pieceDefinition != null
                    && EquipmentSlotMatcher.matches(equippedItem.slot(), pieceDefinition.slot());
            boolean accepted = definitionSlotMatch && setSlotMatch;
            debugSetSlot(player, trigger, equippedItem.slot(), definition, membership, pieceDefinition,
                    definitionSlotMatch, setSlotMatch, accepted);
            if (!accepted) {
                continue;
            }
            result.computeIfAbsent(membership.setId(), ignored -> new LinkedHashSet<>())
                    .add(pieceDefinition.pieceId());
        }
        return result;
    }

    static ItemSetPieceDefinition resolveSetPiece(ItemSetDefinition setDefinition,
            ItemSetMembership membership,
            String itemId) {
        if (setDefinition == null || membership == null || !membership.configured()) {
            return null;
        }
        if (Texts.isNotBlank(membership.pieceId())) {
            return setDefinition.pieces().get(membership.pieceId());
        }
        ItemSetPieceDefinition matched = null;
        for (ItemSetPieceDefinition pieceDefinition : setDefinition.pieces().values()) {
            if (pieceDefinition == null || !Texts.normalizeId(itemId).equals(Texts.normalizeId(pieceDefinition.itemId()))) {
                continue;
            }
            if (matched != null) {
                return null;
            }
            matched = pieceDefinition;
        }
        return matched;
    }

    private Set<String> collectVisibleSetIds(Player player, List<EquippedItem> equippedItems) {
        Set<String> result = new LinkedHashSet<>();
        for (EquippedItem equippedItem : equippedItems == null ? List.<EquippedItem>of() : equippedItems) {
            collectVisibleSetId(result, equippedItem.itemStack());
        }
        PlayerInventory inventory = player.getInventory();
        Set<Integer> equippedSlots = new java.util.HashSet<>(Set.of(40, 39, 38, 37, 36));
        equippedSlots.add(inventory.getHeldItemSlot());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!equippedSlots.contains(slot)) {
                collectVisibleSetId(result, inventory.getItem(slot));
            }
        }
        return result;
    }

    private void collectVisibleSetId(Set<String> sink, ItemStack itemStack) {
        if (sink == null || itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        String id = identifier.identify(itemStack);
        EmakiItemDefinition definition = Texts.isBlank(id) ? null : itemLoader.get(id);
        if (definition != null && definition.setMembership().configured()) {
            sink.add(definition.setMembership().setId());
        }
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

    private boolean writeInventoryIfUnchanged(PlayerInventory inventory,
            int slot,
            ItemStack expected,
            ItemStack updated) {
        ItemStack current = inventory.getItem(slot);
        if (!sameItem(current, expected)) {
            return false;
        }
        inventory.setItem(slot, updated);
        return true;
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        if (first == null || first.getType().isAir()) {
            return second == null || second.getType().isAir();
        }
        return first.equals(second);
    }

    record SetStateSnapshot(Map<String, EquippedSetState> states, Set<String> missingDefinitions) {

        SetStateSnapshot {
            states = states == null || states.isEmpty() ? Map.of() : Map.copyOf(states);
            missingDefinitions = missingDefinitions == null || missingDefinitions.isEmpty()
                    ? Set.of()
                    : Set.copyOf(missingDefinitions);
        }
    }

    private record SetPresentationTarget(
            List<String> setLore,
            List<Integer> activeThresholdNumbers,
            Object nameActions,
            Object loreActions,
            String signature,
            boolean expectsThresholdOperation,
            boolean expectsThresholdLoreOperation) {
    }

    private record SetPresentationInspection(
            int actualSetLoreBlocks,
            int staticOperationLoreLines,
            boolean thresholdOperationPresent,
            boolean blockCountFromThresholdSnapshot,
            boolean current) {
    }

    private record EquippedItem(String slot, ItemStack itemStack) {

        boolean writeIfUnchanged(PlayerInventory inventory, ItemStack expected, ItemStack updated) {
            ItemStack current = read(inventory);
            if (!sameItem(current, expected)) {
                return false;
            }
            write(inventory, updated);
            return true;
        }

        private ItemStack read(PlayerInventory inventory) {
            return switch (slot) {
                case "main_hand" -> inventory.getItemInMainHand();
                case "off_hand" -> inventory.getItemInOffHand();
                case "helmet" -> inventory.getHelmet();
                case "chestplate" -> inventory.getChestplate();
                case "leggings" -> inventory.getLeggings();
                case "boots" -> inventory.getBoots();
                default -> null;
            };
        }

        private void write(PlayerInventory inventory, ItemStack itemStack) {
            switch (slot) {
                case "main_hand" -> inventory.setItemInMainHand(itemStack);
                case "off_hand" -> inventory.setItemInOffHand(itemStack);
                case "helmet" -> inventory.setHelmet(itemStack);
                case "chestplate" -> inventory.setChestplate(itemStack);
                case "leggings" -> inventory.setLeggings(itemStack);
                case "boots" -> inventory.setBoots(itemStack);
                default -> {
                }
            }
        }
    }
}
