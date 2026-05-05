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
        Map<String, EquippedSetState> states = buildStates(equippedPiecesBySet);
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
