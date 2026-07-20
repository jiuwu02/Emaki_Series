package emaki.jiuwu.craft.corelib.integration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final NamespacedKey SKILL_TRIGGERS_KEY = new NamespacedKey("emaki_skills", "item.skills.triggers");

    public void write(ItemStack itemStack, Collection<String> skillIds) {
        write(itemStack, skillIds, EquipmentSlotMatcher.SLOT_ALL);
    }

    public void write(ItemStack itemStack, Collection<String> skillIds, String activeSlot) {
        write(itemStack, skillIds, activeSlot, Map.of());
    }








    public void write(ItemStack itemStack, Collection<String> skillIds, String activeSlot, Map<String, String> boundTriggers) {
        if (itemStack == null) {
            return;
        }
        Map<String, String> normalizedTriggers = normalizeTriggers(boundTriggers);
        List<String> normalized = normalize(skillIds, normalizedTriggers.keySet());
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        if (normalized.isEmpty()) {
            if (!hasSkillPayload(itemMeta)) {
                return;
            }
            itemMeta.getPersistentDataContainer().remove(SKILL_IDS_KEY);
            itemMeta.getPersistentDataContainer().remove(SKILL_ACTIVE_SLOT_KEY);
            itemMeta.getPersistentDataContainer().remove(SKILL_TRIGGERS_KEY);
            itemStack.setItemMeta(itemMeta);
            return;
        }
        itemMeta.getPersistentDataContainer().set(SKILL_IDS_KEY, PersistentDataType.STRING, String.join(";", normalized));
        itemMeta.getPersistentDataContainer().set(
                SKILL_ACTIVE_SLOT_KEY,
                PersistentDataType.STRING,
                EquipmentSlotMatcher.normalizeRequired(activeSlot)
        );
        if (normalizedTriggers.isEmpty()) {
            itemMeta.getPersistentDataContainer().remove(SKILL_TRIGGERS_KEY);
        } else {
            itemMeta.getPersistentDataContainer().set(SKILL_TRIGGERS_KEY, PersistentDataType.STRING, encodeTriggers(normalizedTriggers));
        }
        itemStack.setItemMeta(itemMeta);
    }

    public void clear(ItemStack itemStack) {
        write(itemStack, List.of());
    }

    public List<String> readSkillIds(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return List.of();
        }
        String raw = itemMeta.getPersistentDataContainer().get(SKILL_IDS_KEY, PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            return List.of();
        }
        return decodeSkillIds(raw);
    }

    public String readActiveSlot(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return EquipmentSlotMatcher.SLOT_ALL;
        }
        String raw = itemMeta.getPersistentDataContainer().get(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING);
        return EquipmentSlotMatcher.normalizeRequired(raw);
    }

    public Map<String, String> readBoundTriggers(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return Map.of();
        }
        String raw = itemMeta.getPersistentDataContainer().get(SKILL_TRIGGERS_KEY, PersistentDataType.STRING);
        return decodeTriggers(raw);
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
        String triggers = originalMeta.getPersistentDataContainer().get(SKILL_TRIGGERS_KEY, PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            if (!hasSkillPayload(rebuiltMeta)) {
                return;
            }
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_IDS_KEY);
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_ACTIVE_SLOT_KEY);
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_TRIGGERS_KEY);
            rebuilt.setItemMeta(rebuiltMeta);
            return;
        }
        rebuiltMeta.getPersistentDataContainer().set(SKILL_IDS_KEY, PersistentDataType.STRING, raw);
        rebuiltMeta.getPersistentDataContainer().set(
                SKILL_ACTIVE_SLOT_KEY,
                PersistentDataType.STRING,
                EquipmentSlotMatcher.normalizeRequired(activeSlot)
        );
        if (Texts.isBlank(triggers)) {
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_TRIGGERS_KEY);
        } else {
            rebuiltMeta.getPersistentDataContainer().set(SKILL_TRIGGERS_KEY, PersistentDataType.STRING, triggers);
        }
        rebuilt.setItemMeta(rebuiltMeta);
    }

    private boolean hasSkillPayload(ItemMeta itemMeta) {
        return itemMeta != null
                && (itemMeta.getPersistentDataContainer().get(SKILL_IDS_KEY, PersistentDataType.STRING) != null
                || itemMeta.getPersistentDataContainer().get(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING) != null
                || itemMeta.getPersistentDataContainer().get(SKILL_TRIGGERS_KEY, PersistentDataType.STRING) != null);
    }

    private List<String> normalize(Collection<String> skillIds, Collection<String> triggerSkillIds) {
        Set<String> values = new LinkedHashSet<>();
        addNormalized(values, skillIds);
        addNormalized(values, triggerSkillIds);
        List<String> result = new ArrayList<>(values);
        result.sort(String::compareTo);
        return result;
    }

    private void addNormalized(Set<String> values, Collection<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return;
        }
        for (String skillId : skillIds) {
            String normalized = Texts.normalizeId(skillId);
            if (Texts.isNotBlank(normalized)) {
                values.add(normalized);
            }
        }
    }

    private Map<String, String> normalizeTriggers(Map<String, String> boundTriggers) {
        if (boundTriggers == null || boundTriggers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : boundTriggers.entrySet()) {
            String skillId = Texts.normalizeId(entry.getKey());
            String triggerId = Texts.normalizeId(entry.getValue()).replace('-', '_');
            if (Texts.isNotBlank(skillId) && Texts.isNotBlank(triggerId)) {
                result.put(skillId, triggerId);
            }
        }
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private String encodeTriggers(Map<String, String> triggers) {
        List<String> entries = new ArrayList<>();
        triggers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.add(entry.getKey() + "=" + entry.getValue()));
        return String.join(";", entries);
    }

    private List<String> decodeSkillIds(String raw) {
        if (Texts.isBlank(raw)) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String entry : raw.split(";")) {
            String skillId = Texts.normalizeId(entry);
            if (Texts.isNotBlank(skillId)) {
                result.add(skillId);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    private Map<String, String> decodeTriggers(String raw) {
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
}
