package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.assembly.ItemOperationLedger;
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
    private final ItemOperationLedger itemOperationLedger = new ItemOperationLedger();
    private final java.util.function.Supplier<AppConfig> configSupplier;

    public EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            ItemSetLoreRenderer loreRenderer,
            java.util.function.Supplier<AppConfig> configSupplier) {
        this.itemLoader = itemLoader;
        this.setLoader = setLoader;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.pdcWriter = pdcWriter;
        this.loreRenderer = loreRenderer;
        this.configSupplier = configSupplier;
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
        Map<String, Set<String>> equippedPiecesBySet = collectEquippedPieces(equippedItems);
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
                ItemStack rendered = renderSetItem(original, definition, membership, state);
                if (rendered != original || setPresentationChanged(existingSignature, existingLoreLines, rendered)) {
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
        return changed;
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
                    ItemStack rendered = renderSetItem(original, definition, membership, state);
                    if (rendered != original || setPresentationChanged(existingSignature, existingLoreLines, rendered)) {
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

    private ItemStack clearSetPresentation(ItemStack itemStack, EmakiItemDefinition definition) {
        ItemStack updated = itemStack;
        if (definition.setMembership().configured()) {
            itemOperationLedger.revert(updated, setOperationId(definition.setMembership().setId()));
        }
        stripSetLore(updated);
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

    private void stripSetLore(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return;
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        List<String> strippedLore = stripPreviousSetLore(ItemTextBridge.loreLines(itemMeta), identifier.setLoreLines(itemStack));
        ItemTextBridge.setLoreLines(itemMeta, strippedLore);
        itemStack.setItemMeta(itemMeta);
    }

    private List<String> stripPreviousSetLore(List<String> lore, Integer setLoreLines) {
        List<String> result = lore == null || lore.isEmpty() ? new ArrayList<>() : new ArrayList<>(lore);
        int lines = setLoreLines == null ? 0 : Math.max(0, setLoreLines);
        if (lines <= 0 || result.isEmpty()) {
            return result;
        }
        int keep = Math.max(0, result.size() - lines);
        return new ArrayList<>(result.subList(0, keep));
    }

    private ItemStack renderSetItem(ItemStack itemStack,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            EquippedSetState state) {
        if (itemStack == null || definition == null || state == null || state.definition() == null) {
            return itemStack;
        }
        itemOperationLedger.revert(itemStack, setOperationId(membership.setId()));
        List<String> setLore = loreRenderer.render(state);
        ItemMeta itemMeta = itemStack.getItemMeta();
        int appendedLoreLines = 0;
        if (itemMeta != null) {
            List<String> lore = stripPreviousSetLore(ItemTextBridge.loreLines(itemMeta), identifier.setLoreLines(itemStack));
            List<String> mergedLore = new ArrayList<>();
            if (lore != null && !lore.isEmpty()) {
                mergedLore.addAll(lore);
            }
            if (!setLore.isEmpty()) {
                if (!mergedLore.isEmpty()) {
                    mergedLore.add("");
                    appendedLoreLines++;
                }
                mergedLore.addAll(setLore);
                appendedLoreLines += setLore.size();
            }
            ItemTextBridge.setLoreLines(itemMeta, mergedLore);
            itemStack.setItemMeta(itemMeta);
        }
        List<ItemSetThreshold> activeThresholds = state.activeThresholds();
        List<Integer> activeThresholdNumbers = activeThresholds.stream().map(ItemSetThreshold::requiredPieces).toList();
        Object nameActions = state.mergedNameActions();
        Object loreActions = state.mergedLoreActions();
        applySetDisplayActions(itemStack, definition, membership, state, nameActions, loreActions);
        String setSignature = SignatureUtil.stableSignature(List.of(
                definition.definitionSignature(),
                state.definition().id(),
                state.activeCount(),
                state.equippedPieces().stream().sorted().toList(),
                activeThresholdNumbers,
                setLore,
                nameActions,
                loreActions
        ));
        pdcWriter.writeDynamicSet(
                itemStack,
                definition,
                membership.setId(),
                membership.effectivePieceId(definition.id()),
                state.activeCount(),
                state.definition().totalPieces(),
                activeThresholdNumbers,
                appendedLoreLines,
                state.mergedAttributes(),
                state.mergedSkills(),
                setSignature
        );
        return itemStack;
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
        String operationId = setOperationId(membership.setId());
        if (!hasActions(nameActions) && !hasActions(loreActions)) {
            itemOperationLedger.revert(itemStack, operationId);
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

    private String setOperationId(String setId) {
        return "emakiitem:set_display:" + Texts.normalizeId(setId);
    }

    private boolean hasActions(Object raw) {
        if (raw == null) return false;
        if (raw instanceof Map<?, ?> map) return !map.isEmpty();
        if (raw instanceof Iterable<?> iterable) return iterable.iterator().hasNext();
        return Texts.isNotBlank(raw);
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

    private Map<String, Set<String>> collectEquippedPieces(List<EquippedItem> equippedItems) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (EquippedItem equippedItem : equippedItems) {
            String id = identifier.identify(equippedItem.itemStack());
            EmakiItemDefinition definition = Texts.isBlank(id) ? null : itemLoader.get(id);
            if (definition == null || !definition.setMembership().configured()) {
                continue;
            }
            if (!EquipmentSlotMatcher.matches(equippedItem.slot(), definition.equipSlot())) {
                continue;
            }
            ItemSetMembership membership = definition.setMembership();
            ItemSetDefinition setDefinition = setLoader.get(membership.setId());
            if (setDefinition != null) {
                String pieceId = membership.effectivePieceId(definition.id());
                ItemSetPieceDefinition pieceDefinition = setDefinition.pieces().get(pieceId);
                if (pieceDefinition != null && Texts.isNotBlank(pieceDefinition.slot())) {
                    if (!isSlotMatch(equippedItem.slot(), pieceDefinition.slot())) {
                        continue;
                    }
                }
            }
            result.computeIfAbsent(membership.setId(), ignored -> new LinkedHashSet<>())
                    .add(membership.effectivePieceId(definition.id()));
        }
        return result;
    }

    private boolean isSlotMatch(String actualSlot, String requiredSlot) {
        return EquipmentSlotMatcher.matches(actualSlot, requiredSlot);
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
