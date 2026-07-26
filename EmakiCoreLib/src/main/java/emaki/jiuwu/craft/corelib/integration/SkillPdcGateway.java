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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.item.EquipmentSlotMatcher;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class SkillPdcGateway {

    private static final NamespacedKey SKILL_IDS_KEY = new NamespacedKey("emaki_skills", "item.skills.ids");
    private static final NamespacedKey SKILL_ACTIVE_SLOT_KEY = new NamespacedKey("emaki_skills", "item.skills.active_slot");
    private static final NamespacedKey SKILL_TRIGGERS_KEY = new NamespacedKey("emaki_skills", "item.skills.triggers");

    private final DebugLogger debugLogger;

    public SkillPdcGateway() {
        this(null);
    }

    public SkillPdcGateway(DebugLogger debugLogger) {
        this.debugLogger = debugLogger;
    }

    public void write(ItemStack itemStack, Collection<String> skillIds) {
        write(itemStack, skillIds, EquipmentSlotMatcher.SLOT_ALL);
    }

    public void write(ItemStack itemStack, Collection<String> skillIds, String activeSlot) {
        write(itemStack, skillIds, activeSlot, Map.of());
    }








    public void write(ItemStack itemStack, Collection<String> skillIds, String activeSlot, Map<String, String> boundTriggers) {
        if (itemStack == null) {
            logMutation(null, "skill_write", Map.of(), Map.of(), false, "item_missing");
            return;
        }
        Map<String, String> before = snapshot(itemStack);
        Map<String, String> normalizedTriggers = normalizeTriggers(boundTriggers);
        List<String> normalized = normalize(skillIds, normalizedTriggers.keySet());
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            logMutation(itemStack, "skill_write", before, Map.of(), false, "item_meta_missing");
            return;
        }
        if (normalized.isEmpty()) {
            if (!hasSkillPayload(itemMeta)) {
                logMutation(itemStack, "skill_clear", before, before, false, "payload_absent");
                return;
            }
            itemMeta.getPersistentDataContainer().remove(SKILL_IDS_KEY);
            itemMeta.getPersistentDataContainer().remove(SKILL_ACTIVE_SLOT_KEY);
            itemMeta.getPersistentDataContainer().remove(SKILL_TRIGGERS_KEY);
            boolean committed = itemStack.setItemMeta(itemMeta);
            logMutation(itemStack, "skill_clear", before, snapshot(itemStack), committed, "");
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
        boolean committed = itemStack.setItemMeta(itemMeta);
        logMutation(itemStack, "skill_write", before, snapshot(itemStack), committed, "");
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
            logMutation(rebuilt, "skill_copy", Map.of(), Map.of(), false, "item_missing");
            return;
        }
        Map<String, String> before = snapshot(rebuilt);
        ItemMeta originalMeta = original.getItemMeta();
        ItemMeta rebuiltMeta = rebuilt.getItemMeta();
        if (originalMeta == null || rebuiltMeta == null) {
            logMutation(rebuilt, "skill_copy", before, Map.of(), false, "item_meta_missing");
            return;
        }
        String raw = originalMeta.getPersistentDataContainer().get(SKILL_IDS_KEY, PersistentDataType.STRING);
        String activeSlot = originalMeta.getPersistentDataContainer().get(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING);
        String triggers = originalMeta.getPersistentDataContainer().get(SKILL_TRIGGERS_KEY, PersistentDataType.STRING);
        if (Texts.isBlank(raw)) {
            if (!hasSkillPayload(rebuiltMeta)) {
                logMutation(rebuilt, "skill_copy", before, before, false, "payload_absent");
                return;
            }
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_IDS_KEY);
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_ACTIVE_SLOT_KEY);
            rebuiltMeta.getPersistentDataContainer().remove(SKILL_TRIGGERS_KEY);
            boolean committed = rebuilt.setItemMeta(rebuiltMeta);
            logMutation(rebuilt, "skill_copy", before, snapshot(rebuilt), committed, "");
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
        boolean committed = rebuilt.setItemMeta(rebuiltMeta);
        logMutation(rebuilt, "skill_copy", before, snapshot(rebuilt), committed, "");
    }

    private Map<String, String> snapshot(ItemStack itemStack) {
        if (!isDebugEnabled() || itemStack == null) {
            return Map.of();
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        addSnapshotValue(result, container, SKILL_IDS_KEY);
        addSnapshotValue(result, container, SKILL_ACTIVE_SLOT_KEY);
        addSnapshotValue(result, container, SKILL_TRIGGERS_KEY);
        return result.isEmpty() ? Map.of() : Map.copyOf(result);
    }

    private void addSnapshotValue(Map<String, String> sink, PersistentDataContainer container, NamespacedKey key) {
        String value = container.get(key, PersistentDataType.STRING);
        if (value != null) {
            sink.put(key.toString(), value);
        }
    }

    private boolean isDebugEnabled() {
        return debugLogger != null && debugLogger.shouldLog("pdc", (java.util.UUID) null);
    }

    private void logMutation(ItemStack itemStack,
            String operation,
            Map<String, String> before,
            Map<String, String> after,
            boolean committed,
            String reason) {
        if (!isDebugEnabled()) {
            return;
        }
        debugLogger.log("pdc", (java.util.UUID) null, "pdc.skill_payload", Map.of(
                "operation", operation,
                "item", itemStack == null ? "null" : itemStack.getType(),
                "amount", itemStack == null ? 0 : itemStack.getAmount(),
                "before", before,
                "after", after,
                "committed", committed,
                "reason", reason == null ? "" : reason
        ));
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
