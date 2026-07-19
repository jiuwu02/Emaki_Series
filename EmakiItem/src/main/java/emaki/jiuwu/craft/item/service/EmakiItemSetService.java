package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.pdc.SignatureUtil;
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
    // 记录每个玩家上次各套装的激活件数，用于边沿触发 ItemSetBonusChangeEvent，避免每次背包刷新都派发。
    private final Map<java.util.UUID, Map<String, Integer>> lastActiveCounts = new java.util.concurrent.ConcurrentHashMap<>();

    public EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            Supplier<AppConfig> configSupplier) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, () -> null, null);
    }

    public EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            Supplier<AppConfig> configSupplier,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, debugLoggerSupplier, null);
    }

    EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            Supplier<AppConfig> configSupplier,
            ItemOperationLedger itemOperationLedger) {
        this(itemLoader, setLoader, itemFactory, identifier, pdcWriter, loreRenderer, configSupplier, () -> null, itemOperationLedger);
    }

    private EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            Supplier<AppConfig> configSupplier,
            Supplier<DebugLogger> debugLoggerSupplier,
            ItemOperationLedger itemOperationLedger) {
        this.itemLoader = itemLoader;
        this.setLoader = setLoader;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.pdcWriter = pdcWriter;
        this.loreRenderer = loreRenderer;
        this.configSupplier = configSupplier;
        this.debugLoggerSupplier = debugLoggerSupplier == null ? () -> null : debugLoggerSupplier;
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
        Map<String, Set<String>> equippedPiecesBySet = collectEquippedPieces(player, trigger, equippedItems);
        Map<String, Set<String>> allPiecesBySet = collectAllPieces(player, equippedPiecesBySet);
        Map<String, EquippedSetState> states = buildStates(equippedPiecesBySet);
        for (String setId : allPiecesBySet.keySet()) {
            states.computeIfAbsent(setId, id -> {
                ItemSetDefinition definition = setLoader.get(id);
                return definition != null ? new EquippedSetState(definition, Set.of()) : null;
            });
        }
        states.values().removeIf(java.util.Objects::isNull);
        int changed = 0;
        for (EquippedItem equippedItem : equippedItems) {
            ItemStack original = equippedItem.itemStack();
            if (original == null || original.getType().isAir()) {
                continue;
            }
            String id = identifier.identify(original);
            EmakiItemDefinition definition = Texts.isBlank(id) ? null : itemLoader.get(id);
            if (definition == null) {
                continue;
            }
            ItemSetMembership membership = definition.setMembership();
            String existingSignature = identifier.setSignature(original);
            Integer existingLoreLines = identifier.setLoreLines(original);
            if (membership.configured()) {
                EquippedSetState state = states.get(membership.setId());
                int previousLoreSize = loreSize(original);
                ItemStack rendered = renderSetItem(original, definition, membership, state);
                boolean presentationChanged = rendered != original || setPresentationChanged(existingSignature, existingLoreLines, rendered);
                debugSetLore(player, trigger, equippedItem.slot(), definition.id(), existingSignature, existingLoreLines,
                        previousLoreSize, rendered, presentationChanged);
                if (presentationChanged) {
                    equippedItem.write(player.getInventory(), rendered);
                    changed++;
                }
            } else if (hasSetPresentation(original)) {
                ItemStack cleared = clearSetPresentation(original, definition);
                equippedItem.write(player.getInventory(), cleared);
                changed++;
            }
        }
        changed += cleanInventorySetLore(player, states);
        fireSetBonusChangeEvents(player, states, trigger);
        return changed;
    }

    private void fireSetBonusChangeEvents(Player player,
            Map<String, EquippedSetState> states,
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
            Map<String, EquippedSetState> states) {
        PlayerInventory inventory = player.getInventory();
        int changed = 0;
        Set<Integer> equippedSlots = new java.util.HashSet<>(Set.of(40, 39, 38, 37, 36));
        equippedSlots.add(inventory.getHeldItemSlot());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (equippedSlots.contains(slot)) {
                continue;
            }
            ItemStack original = inventory.getItem(slot);
            if (original == null || original.getType().isAir()) {
                continue;
            }
            String id = identifier.identify(original);
            EmakiItemDefinition definition = Texts.isBlank(id) ? null : itemLoader.get(id);
            if (definition == null) {
                continue;
            }
            ItemSetMembership membership = definition.setMembership();
            if (membership.configured()) {
                EquippedSetState state = states.get(membership.setId());
                if (state != null) {
                    String existingSignature = identifier.setSignature(original);
                    Integer existingLoreLines = identifier.setLoreLines(original);
                    int previousLoreSize = loreSize(original);
                    ItemStack rendered = renderSetItem(original, definition, membership, state);
                    boolean presentationChanged = rendered != original || setPresentationChanged(existingSignature, existingLoreLines, rendered);
                    debugSetLore(player, "inventory", "slot_" + slot, definition.id(), existingSignature, existingLoreLines,
                            previousLoreSize, rendered, presentationChanged);
                    if (presentationChanged) {
                        inventory.setItem(slot, rendered);
                        changed++;
                    }
                } else {
                    if (hasSetPresentation(original)) {
                        ItemStack cleared = clearSetPresentation(original, definition);
                        inventory.setItem(slot, cleared);
                        changed++;
                    }
                }
            } else if (hasSetPresentation(original)) {
                ItemStack cleared = clearSetPresentation(original, definition);
                inventory.setItem(slot, cleared);
                changed++;
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
        if (Texts.isNotBlank(setId)) {
            itemOperationLedger.revert(updated, thresholdOperationId(setId));
            staticLoreReverted = itemOperationLedger.revert(updated, staticLoreOperationId(setId));
        }
        if (!staticLoreReverted) {
            stripSetLore(updated, previousSetLoreLines);
        }
        pdcWriter.clearDynamicSet(updated, definition);
        return updated;
    }

    private boolean hasSetPresentation(ItemStack itemStack) {
        return Texts.isNotBlank(identifier.setSignature(itemStack)) || identifier.setLoreLines(itemStack) != null;
    }

    private boolean setPresentationChanged(String previousSignature, Integer previousLoreLines, ItemStack itemStack) {
        return !java.util.Objects.equals(previousSignature == null ? "" : previousSignature, identifier.setSignature(itemStack))
                || !java.util.Objects.equals(previousLoreLines, identifier.setLoreLines(itemStack));
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

    private void stripTrailingSetLore(ItemStack itemStack, List<String> setLore) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<String> currentLore = ItemTextBridge.loreLines(itemMeta);
        List<String> strippedLore = stripTrailingSetLoreBlocks(currentLore, setLore);
        if (currentLore.equals(strippedLore)) {
            return;
        }
        ItemTextBridge.setLoreLines(itemMeta, strippedLore);
        itemStack.setItemMeta(itemMeta);
    }

    static List<String> stripTrailingSetLoreBlocks(List<String> lore, List<String> setLore) {
        List<String> result = lore == null || lore.isEmpty() ? new ArrayList<>() : new ArrayList<>(lore);
        if (setLore == null || setLore.isEmpty()) {
            return result;
        }
        while (endsWith(result, setLore)) {
            result.subList(result.size() - setLore.size(), result.size()).clear();
            if (!result.isEmpty() && result.getLast().isEmpty()) {
                result.removeLast();
            }
        }
        return result;
    }

    private static boolean endsWith(List<String> lines, List<String> suffix) {
        if (lines == null || suffix == null || suffix.isEmpty() || lines.size() < suffix.size()) {
            return false;
        }
        int offset = lines.size() - suffix.size();
        for (int index = 0; index < suffix.size(); index++) {
            if (!java.util.Objects.equals(lines.get(offset + index), suffix.get(index))) {
                return false;
            }
        }
        return true;
    }

    static boolean isSetPresentationCurrent(String existingSignature,
            Integer existingLoreLines,
            String expectedSignature,
            List<String> expectedSetLore) {
        if (!java.util.Objects.equals(Texts.toStringSafe(existingSignature), Texts.toStringSafe(expectedSignature))) {
            return false;
        }
        return expectedSetLore == null || expectedSetLore.isEmpty()
                || existingLoreLines != null && existingLoreLines > 0;
    }

    static List<String> staticLoreBlock(List<String> baseLore, List<String> setLore) {
        if (setLore == null || setLore.isEmpty()) {
            return List.of();
        }
        List<String> block = new ArrayList<>();
        if (baseLore != null && !baseLore.isEmpty()) {
            block.add("");
        }
        block.addAll(setLore);
        return List.copyOf(block);
    }

    ItemStack renderSetItem(ItemStack itemStack,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            EquippedSetState state) {
        if (itemStack == null || definition == null || state == null || state.definition() == null) {
            return itemStack;
        }
        List<String> setLore = loreRenderer.render(state);
        List<ItemSetThreshold> activeThresholds = state.activeThresholds();
        List<Integer> activeThresholdNumbers = activeThresholds.stream().map(ItemSetThreshold::requiredPieces).toList();
        Object nameActions = state.mergedNameActions();
        Object loreActions = state.mergedLoreActions();
        String setSignature = buildSetSignature(definition, state, activeThresholdNumbers, setLore, nameActions, loreActions);
        Integer previousSetLoreLines = identifier.setLoreLines(itemStack);
        if (isSetPresentationCurrent(identifier.setSignature(itemStack), previousSetLoreLines, setSignature, setLore)) {
            return itemStack;
        }

        String setId = membership.setId();
        itemOperationLedger.revert(itemStack, thresholdOperationId(setId));
        boolean staticLoreReverted = itemOperationLedger.revert(itemStack, staticLoreOperationId(setId));
        if (!staticLoreReverted) {
            stripSetLore(itemStack, previousSetLoreLines);
            stripTrailingSetLore(itemStack, setLore);
        }

        int staticLoreLines = applyStaticSetLore(itemStack, setId, setLore);
        applySetDisplayActions(itemStack, definition, membership, state, nameActions, loreActions);
        pdcWriter.writeDynamicSet(
                itemStack,
                definition,
                membership.setId(),
                membership.effectivePieceId(definition.id()),
                state.activeCount(),
                state.definition().totalPieces(),
                activeThresholdNumbers,
                staticLoreLines,
                state.mergedAttributes(),
                state.mergedSkills(),
                setSignature
        );
        return itemStack;
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
        if (itemStack == null || itemStack.getType().isAir()) {
            return 0;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta == null ? 0 : ItemTextBridge.loreLines(itemMeta).size();
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
            boolean changed) {
        DebugLogger debugLogger = debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.logRaw("set", player, "[DEBUG:SET_LORE] trigger=" + Texts.toStringSafe(trigger)
                + " slot=" + Texts.toStringSafe(slot)
                + " item=" + Texts.toStringSafe(itemId)
                + " signature=" + Texts.toStringSafe(previousSignature) + "->" + Texts.toStringSafe(identifier.setSignature(rendered))
                + " marker=" + previousLoreLines + "->" + identifier.setLoreLines(rendered)
                + " lore_size=" + previousLoreSize + "->" + loreSize(rendered)
                + " changed=" + changed);
    }

    private List<EquippedItem> readEquippedItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        return List.of(
                new EquippedItem("main_hand", inventory.getItemInMainHand()),
                new EquippedItem("off_hand", inventory.getItemInOffHand()),
                new EquippedItem("helmet", inventory.getHelmet()),
                new EquippedItem("chestplate", inventory.getChestplate()),
                new EquippedItem("leggings", inventory.getLeggings()),
                new EquippedItem("boots", inventory.getBoots())
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

    private Map<String, Set<String>> collectAllPieces(Player player, Map<String, Set<String>> equippedPieces) {
        Map<String, Set<String>> result = new LinkedHashMap<>(equippedPieces.size());
        equippedPieces.forEach((setId, pieces) -> result.put(setId, new LinkedHashSet<>(pieces)));
        PlayerInventory inventory = player.getInventory();
        Set<Integer> equippedSlots = new java.util.HashSet<>(Set.of(40, 39, 38, 37, 36));
        equippedSlots.add(inventory.getHeldItemSlot());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (equippedSlots.contains(slot)) {
                continue;
            }
            ItemStack itemStack = inventory.getItem(slot);
            if (itemStack == null || itemStack.getType().isAir()) {
                continue;
            }
            String id = identifier.identify(itemStack);
            EmakiItemDefinition definition = Texts.isBlank(id) ? null : itemLoader.get(id);
            if (definition == null || !definition.setMembership().configured()) {
                continue;
            }
            ItemSetMembership membership = definition.setMembership();
            result.computeIfAbsent(membership.setId(), ignored -> new LinkedHashSet<>())
                    .add(membership.effectivePieceId(definition.id()));
        }
        return result;
    }

    private Map<String, EquippedSetState> buildStates(Map<String, Set<String>> equippedPiecesBySet) {
        Map<String, EquippedSetState> result = new LinkedHashMap<>();
        equippedPiecesBySet.forEach((setId, pieces) -> {
            ItemSetDefinition definition = setLoader.get(setId);
            if (definition != null) {
                result.put(setId, new EquippedSetState(definition, pieces));
            }
        });
        return result;
    }

    private record EquippedItem(String slot, ItemStack itemStack) {

        void write(PlayerInventory inventory, ItemStack itemStack) {
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
