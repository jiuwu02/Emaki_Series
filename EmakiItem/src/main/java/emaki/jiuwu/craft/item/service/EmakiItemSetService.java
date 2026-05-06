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

    private final EmakiItemLoader itemLoader;
    private final EmakiItemSetLoader setLoader;
    private final EmakiItemFactory itemFactory;
    private final EmakiItemIdentifier identifier;
    private final EmakiItemPdcWriter pdcWriter;
    private final EmakiItemUpdateService updateService;
    private final ItemSetLoreRenderer loreRenderer;
    private final java.util.function.Supplier<AppConfig> configSupplier;

    public EmakiItemSetService(EmakiItemLoader itemLoader,
            EmakiItemSetLoader setLoader,
            EmakiItemFactory itemFactory,
            EmakiItemIdentifier identifier,
            EmakiItemPdcWriter pdcWriter,
            EmakiItemUpdateService updateService,
            ItemSetLoreRenderer loreRenderer,
            java.util.function.Supplier<AppConfig> configSupplier) {
        this.itemLoader = itemLoader;
        this.setLoader = setLoader;
        this.itemFactory = itemFactory;
        this.identifier = identifier;
        this.pdcWriter = pdcWriter;
        this.updateService = updateService;
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
        // 套装状态基于真实装备槽（决定 ✔/✘ 和激活加成）
        Map<String, EquippedSetState> states = buildStates(equippedPiecesBySet);
        // 确保背包中存在套装件的 setId 也有对应 state（用于渲染 lore，但 activeCount 基于装备槽）
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
            ItemStack updated = updateService.forceUpdate(original);
            if (membership.configured()) {
                EquippedSetState state = states.get(membership.setId());
                ItemStack rendered = renderSetItem(updated, definition, membership, state);
                if (rendered != original) {
                    equippedItem.write(player.getInventory(), rendered);
                    changed++;
                }
            } else if (Texts.isNotBlank(identifier.setSignature(original))) {
                pdcWriter.clearDynamicSet(updated, definition);
                equippedItem.write(player.getInventory(), updated);
                changed++;
            } else if (updated != original) {
                equippedItem.write(player.getInventory(), updated);
                changed++;
            }
        }
        // 扫描背包中非装备槽的物品，清除残留的套装 lore
        changed += cleanInventorySetLore(player, states);
        return changed;
    }

    private int cleanInventorySetLore(Player player,
            Map<String, EquippedSetState> states) {
        PlayerInventory inventory = player.getInventory();
        int changed = 0;
        // 背包主区域 0-35，排除装备槽对应的 index
        // 装备槽: 主手=getHeldItemSlot(), 副手=40, 头盔=39, 胸甲=38, 护腿=37, 靴子=36
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
            // 如果物品是套装成员且在背包中（非装备槽），需要显示套装 lore（仅展示信息，不激活属性）
            if (membership.configured()) {
                EquippedSetState state = states.get(membership.setId());
                if (state != null) {
                    // 背包中的套装物品也应该显示套装 lore（与装备槽一致）
                    String existingSignature = identifier.setSignature(original);
                    ItemStack updated = updateService.forceUpdate(original);
                    ItemStack rendered = renderSetItem(updated, definition, membership, state);
                    String newSignature = identifier.setSignature(rendered);
                    if (!newSignature.equals(existingSignature)) {
                        inventory.setItem(slot, rendered);
                        changed++;
                    }
                } else {
                    // 套装定义不存在或没有任何装备件数，清除残留套装 lore
                    if (Texts.isNotBlank(identifier.setSignature(original))) {
                        ItemStack updated = updateService.forceUpdate(original);
                        pdcWriter.clearDynamicSet(updated, definition);
                        inventory.setItem(slot, updated);
                        changed++;
                    }
                }
            } else if (Texts.isNotBlank(identifier.setSignature(original))) {
                // 物品不再是套装成员但仍有套装签名，清除
                ItemStack updated = updateService.forceUpdate(original);
                pdcWriter.clearDynamicSet(updated, definition);
                inventory.setItem(slot, updated);
                changed++;
            }
        }
        return changed;
    }

    private ItemStack renderSetItem(ItemStack itemStack,
            EmakiItemDefinition definition,
            ItemSetMembership membership,
            EquippedSetState state) {
        if (itemStack == null || definition == null || state == null || state.definition() == null) {
            return itemStack;
        }
        List<String> setLore = loreRenderer.render(state);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null) {
            List<String> lore = ItemTextBridge.loreLines(itemMeta);
            List<String> mergedLore = new ArrayList<>();
            if (lore != null && !lore.isEmpty()) {
                mergedLore.addAll(lore);
            }
            if (!setLore.isEmpty()) {
                if (!mergedLore.isEmpty()) {
                    mergedLore.add("");
                }
                mergedLore.addAll(setLore);
            }
            ItemTextBridge.setLoreLines(itemMeta, mergedLore);
            itemStack.setItemMeta(itemMeta);
        }
        List<ItemSetThreshold> activeThresholds = state.activeThresholds();
        List<Integer> activeThresholdNumbers = activeThresholds.stream().map(ItemSetThreshold::requiredPieces).toList();
        String setSignature = SignatureUtil.stableSignature(List.of(
                definition.definitionSignature(),
                state.definition().id(),
                state.activeCount(),
                state.equippedPieces().stream().sorted().toList(),
                activeThresholdNumbers,
                setLore
        ));
        pdcWriter.writeDynamicSet(
                itemStack,
                definition,
                membership.setId(),
                membership.effectivePieceId(definition.id()),
                state.activeCount(),
                state.definition().totalPieces(),
                activeThresholdNumbers,
                state.mergedAttributes(),
                state.mergedSkills(),
                setSignature
        );
        return itemStack;
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
            ItemSetMembership membership = definition.setMembership();
            // 验证物品是否在正确的穿戴槽位
            ItemSetDefinition setDefinition = setLoader.get(membership.setId());
            if (setDefinition != null) {
                String pieceId = membership.effectivePieceId(definition.id());
                ItemSetPieceDefinition pieceDefinition = setDefinition.pieces().get(pieceId);
                if (pieceDefinition != null && Texts.isNotBlank(pieceDefinition.slot())) {
                    // 如果套装件定义了 slot，则必须在对应槽位才算装备
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

    /**
     * 判断物品实际所在的装备槽是否匹配套装件定义的 slot。
     * 例如：头盔必须在 helmet 槽，胸甲必须在 chestplate 槽。
     * 如果套装件的 slot 是 "any" 或与 pieceId 相同（未显式配置），则任何装备槽都算匹配。
     */
    private boolean isSlotMatch(String actualSlot, String requiredSlot) {
        if (Texts.isBlank(requiredSlot) || Texts.isBlank(actualSlot)) {
            return true;
        }
        String normalizedRequired = Texts.normalizeId(requiredSlot);
        String normalizedActual = Texts.normalizeId(actualSlot);
        // "any" 表示任何槽位都可以
        if ("any".equals(normalizedRequired)) {
            return true;
        }
        // 直接匹配
        if (normalizedRequired.equals(normalizedActual)) {
            return true;
        }
        // 支持 "hand" 匹配 main_hand 和 off_hand
        if ("hand".equals(normalizedRequired)) {
            return "main_hand".equals(normalizedActual) || "off_hand".equals(normalizedActual);
        }
        return false;
    }

    /**
     * 收集玩家整个背包（含装备槽）中所有套装物品的 pieceId，用于构建完整的套装状态。
     * 这样即使物品在背包中未装备，也能正确显示套装 lore。
     */
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
