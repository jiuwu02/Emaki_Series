package emaki.jiuwu.craft.corelib.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class SkillPdcGateway {

    private static final NamespacedKey SKILL_IDS_KEY = new NamespacedKey("emaki_skills", "item.skills.ids");
    private static final NamespacedKey SKILL_ACTIVE_SLOT_KEY = new NamespacedKey("emaki_skills", "item.skills.active_slot");

    public void write(ItemStack itemStack, Collection<String> skillIds) {
        write(itemStack, skillIds, EquipmentSlotMatcher.SLOT_ALL);
    }

    public void write(ItemStack itemStack, Collection<String> skillIds, String activeSlot) {
        if (itemStack == null) {
            return;
        }
        List<String> normalized = normalize(skillIds);
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        if (normalized.isEmpty()) {
            itemMeta.getPersistentDataContainer().remove(SKILL_IDS_KEY);
            itemMeta.getPersistentDataContainer().remove(SKILL_ACTIVE_SLOT_KEY);
        } else {
            itemMeta.getPersistentDataContainer().set(SKILL_IDS_KEY, PersistentDataType.STRING, String.join(";", normalized));
            itemMeta.getPersistentDataContainer().set(
                    SKILL_ACTIVE_SLOT_KEY,
                    PersistentDataType.STRING,
                    EquipmentSlotMatcher.normalizeRequired(activeSlot)
            );
        }
        itemStack.setItemMeta(itemMeta);
    }

    public void clear(ItemStack itemStack) {
        write(itemStack, List.of());
    }

    public void copy(ItemStack original, ItemStack rebuilt) {
        if (original == null || rebuilt == null) {
            return;
        }
        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta rebuiltMeta = rebuilt.getItemMeta();
        if (originalMeta == null || rebuiltMeta == null) {
            return;
        }
        String raw = originalMeta.getPersistentDataContainer().get(SKILL_IDS_KEY, PersistentDataType.STRING);
        String activeSlot = originalMeta.getPersistentDataContainer().get(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_IDS_KEY);
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_ACTIVE_SLOT_KEY);
        } else {
            rebuiltMeta.getPersistentDataContainer().set(SKILL_IDS_KEY, PersistentDataType.STRING, raw);
            rebuiltMeta.getPersistentDataContainer().set(
                    SKILL_ACTIVE_SLOT_KEY,
                    PersistentDataType.STRING,
                    EquipmentSlotMatcher.normalizeRequired(activeSlot)
            );
        }
        rebuilt.setItemMeta(rebuiltMeta);
    }

    private List<String> normalize(Collection<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String skillId : skillIds) {
            String normalized = Texts.normalizeId(skillId);
            if (Texts.isNotBlank(normalized)) {
                values.add(normalized);
            }
        }
        List<String> result = new ArrayList<>(values);
        result.sort(String::compareTo);
        return result;
    }
}
