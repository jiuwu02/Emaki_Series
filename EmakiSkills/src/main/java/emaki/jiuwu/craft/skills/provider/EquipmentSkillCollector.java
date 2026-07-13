package emaki.jiuwu.craft.skills.provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.BoundSkillTrigger;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSourceType;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;

public final class EquipmentSkillCollector {

    private static final Map<EquipmentSlot, String> SLOT_NAMES = Map.of(
            EquipmentSlot.HAND, "main_hand",
            EquipmentSlot.OFF_HAND, "off_hand",
            EquipmentSlot.HEAD, "helmet",
            EquipmentSlot.CHEST, "chestplate",
            EquipmentSlot.LEGS, "leggings",
            EquipmentSlot.FEET, "boots"
    );

    private static final EquipmentSlot[] SCANNED_SLOTS = {
            EquipmentSlot.HAND,
            EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final NamespacedKey pdcKey;
    private final NamespacedKey activeSlotKey;
    private final NamespacedKey triggerBindingsKey;
    private final Supplier<Map<String, SkillDefinition>> skillDefinitionsSupplier;

    public EquipmentSkillCollector(JavaPlugin plugin,
            Supplier<Map<String, SkillDefinition>> skillDefinitionsSupplier) {
        this.pdcKey = new NamespacedKey("emaki_skills", "item.skills.ids");
        this.activeSlotKey = new NamespacedKey("emaki_skills", "item.skills.active_slot");
        this.triggerBindingsKey = new NamespacedKey("emaki_skills", "item.skills.triggers");
        this.skillDefinitionsSupplier = skillDefinitionsSupplier;
    }

    public List<UnlockedSkillEntry> collect(Player player) {
        if (player == null) {
            return List.of();
        }
        List<UnlockedSkillEntry> result = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        Map<String, SkillDefinition> definitions = skillDefinitionsSupplier.get();

        for (EquipmentSlot slot : SCANNED_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String slotName = SLOT_NAMES.getOrDefault(slot, slot.name().toLowerCase(java.util.Locale.ROOT));
            collectFromPdc(item, slotName, result);
            collectFromLore(item, slotName, definitions, result);
        }
        return result;
    }

    public List<BoundSkillTrigger> collectBoundTriggers(Player player, String triggerId) {
        if (player == null || Texts.isBlank(triggerId)) {
            return List.of();
        }
        List<BoundSkillTrigger> result = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        String normalizedTrigger = Texts.normalizeId(triggerId).replace('-', '_');
        for (EquipmentSlot slot : SCANNED_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            String slotName = SLOT_NAMES.getOrDefault(slot, slot.name().toLowerCase(java.util.Locale.ROOT));
            for (Map.Entry<String, String> entry : readTriggerBindings(item, slotName).entrySet()) {
                if (normalizedTrigger.equals(entry.getValue())) {
                    result.add(new BoundSkillTrigger(entry.getKey(), entry.getValue(), slotName));
                }
            }
        }
        return result;
    }

    private void collectFromPdc(ItemStack item, String slotName, List<UnlockedSkillEntry> sink) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String raw = pdc.get(pdcKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return;
        }
        String requiredSlot = pdc.get(activeSlotKey, PersistentDataType.STRING);
        if (!EquipmentSlotMatcher.matches(slotName, requiredSlot)) {
            return;
        }
        for (String skillId : raw.split(";")) {
            String trimmed = skillId.trim();
            if (!trimmed.isEmpty()) {
                sink.add(new UnlockedSkillEntry(trimmed, "equipment", SkillSourceType.EQUIPMENT, slotName, null));
            }
        }
    }

    private Map<String, String> readTriggerBindings(ItemStack item, String slotName) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null) {
            return Map.of();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String requiredSlot = pdc.get(activeSlotKey, PersistentDataType.STRING);
        if (!EquipmentSlotMatcher.matches(slotName, requiredSlot)) {
            return Map.of();
        }
        String raw = pdc.get(triggerBindingsKey, PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String entry : raw.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator >= entry.length() - 1) {
                continue;
            }
            String skillId = Texts.normalizeId(entry.substring(0, separator));
            String triggerId = Texts.normalizeId(entry.substring(separator + 1)).replace('-', '_');
            if (Texts.isNotBlank(skillId) && Texts.isNotBlank(triggerId)) {
                result.put(skillId, triggerId);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private void collectFromLore(ItemStack item, String slotName,
            Map<String, SkillDefinition> definitions, List<UnlockedSkillEntry> sink) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return;
        }
        List<String> lore = ItemTextBridge.loreLines(meta);
        if (lore == null || lore.isEmpty() || definitions == null || definitions.isEmpty()) {
            return;
        }
        List<String> normalizedLines = new ArrayList<>(lore.size());
        for (String line : lore) {
            normalizedLines.add(Texts.normalizeWhitespace(Texts.stripMiniTags(line)));
        }

        for (SkillDefinition definition : definitions.values()) {
            if (definition.loreAliases().isEmpty()) {
                continue;
            }
            for (String alias : definition.loreAliases()) {
                if (Texts.isBlank(alias)) {
                    continue;
                }
                boolean matched = false;
                for (String normalizedLine : normalizedLines) {
                    if (normalizedLine.contains(alias)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) {
                    sink.add(new UnlockedSkillEntry(definition.id(), "equipment", SkillSourceType.EQUIPMENT, slotName, alias));
                    break;
                }
            }
        }
    }
}
