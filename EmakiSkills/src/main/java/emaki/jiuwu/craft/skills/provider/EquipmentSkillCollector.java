package emaki.jiuwu.craft.skills.provider;

import java.util.ArrayList;
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

import emaki.jiuwu.craft.corelib.text.Texts;
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
    private final Supplier<Map<String, SkillDefinition>> skillDefinitionsSupplier;

    public EquipmentSkillCollector(JavaPlugin plugin,
            Supplier<Map<String, SkillDefinition>> skillDefinitionsSupplier) {
        this.pdcKey = new NamespacedKey("emaki_skills", "item.skills.ids");
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
            String slotName = SLOT_NAMES.getOrDefault(slot, slot.name().toLowerCase());
            collectFromPdc(item, slotName, result);
            collectFromLore(item, slotName, definitions, result);
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
        for (String skillId : raw.split(";")) {
            String trimmed = skillId.trim();
            if (!trimmed.isEmpty()) {
                sink.add(new UnlockedSkillEntry(trimmed, "equipment", SkillSourceType.EQUIPMENT, slotName, null));
            }
        }
    }

    private void collectFromLore(ItemStack item, String slotName,
            Map<String, SkillDefinition> definitions, List<UnlockedSkillEntry> sink) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return;
        }
        List<String> lore = meta.getLore();
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
