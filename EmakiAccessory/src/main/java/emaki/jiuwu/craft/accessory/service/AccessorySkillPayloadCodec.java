package emaki.jiuwu.craft.accessory.service;

import java.util.LinkedHashSet;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class AccessorySkillPayloadCodec {

    public static final String SLOT_ALL = "all";
    public static final String SLOT_HAND = "hand";
    public static final String SLOT_MAIN_HAND = "main_hand";
    public static final String SLOT_OFF_HAND = "off_hand";
    public static final String SLOT_HELMET = "helmet";
    public static final String SLOT_CHESTPLATE = "chestplate";
    public static final String SLOT_LEGGINGS = "leggings";
    public static final String SLOT_BOOTS = "boots";

    private static final String NAMESPACE = "emaki_skills";
    private static final NamespacedKey SKILL_IDS_KEY = new NamespacedKey(NAMESPACE, "item_skills_ids");
    private static final NamespacedKey SKILL_ACTIVE_SLOT_KEY =
            new NamespacedKey(NAMESPACE, "item_skills_active_slot");
    private static final NamespacedKey SKILL_TRIGGERS_KEY = new NamespacedKey(NAMESPACE, "item_skills_triggers");
    private AccessorySkillPayloadCodec() {
    }

    public static Payload read(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        if (itemMeta == null) {
            return Payload.empty();
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        String skillIds = container.get(SKILL_IDS_KEY, PersistentDataType.STRING);
        String activeSlot = container.get(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING);
        return new Payload(
                decodeSkillIds(skillIds),
                normalizeRequiredSlot(activeSlot),
                hasPayload(container));
    }

    public static boolean hasPayload(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta != null && hasPayload(itemMeta.getPersistentDataContainer());
    }

    public static String normalizeRequiredSlot(String slot) {
        String normalized = normalizeSlot(slot);
        return Texts.isBlank(normalized) ? SLOT_ALL : normalized;
    }

    private static boolean hasPayload(PersistentDataContainer container) {
        return container != null && (has(container, SKILL_IDS_KEY)
                || has(container, SKILL_ACTIVE_SLOT_KEY)
                || has(container, SKILL_TRIGGERS_KEY));
    }

    private static boolean has(PersistentDataContainer container, NamespacedKey key) {
        return container.has(key, PersistentDataType.STRING);
    }

    private static List<String> decodeSkillIds(String raw) {
        if (Texts.isBlank(raw)) {
            return List.of();
        }
        LinkedHashSet<String> decoded = new LinkedHashSet<>();
        for (String entry : raw.split(";")) {
            String skillId = normalizeSkillId(entry);
            if (Texts.isNotBlank(skillId)) {
                decoded.add(skillId);
            }
        }
        return decoded.isEmpty() ? List.of() : List.copyOf(decoded);
    }

    private static String normalizeSkillId(String value) {
        return Texts.normalizeId(value);
    }

    private static String normalizeSlot(String slot) {
        String normalized = normalizeSkillId(slot);
        if (Texts.isBlank(normalized)) {
            return "";
        }
        return switch (normalized) {
            case "all", "any" -> SLOT_ALL;
            case "hand" -> SLOT_HAND;
            case "mainhand", "main_hand", "main" -> SLOT_MAIN_HAND;
            case "offhand", "off_hand", "off" -> SLOT_OFF_HAND;
            case "helmet", "head" -> SLOT_HELMET;
            case "chestplate", "chest", "body" -> SLOT_CHESTPLATE;
            case "leggings", "legs" -> SLOT_LEGGINGS;
            case "boots", "feet", "foot" -> SLOT_BOOTS;
            default -> normalized;
        };
    }

    public record Payload(List<String> skillIds, String activeSlot, boolean present) {

        public Payload {
            skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
            activeSlot = activeSlot == null ? SLOT_ALL : activeSlot;
        }

        public static Payload empty() {
            return new Payload(List.of(), SLOT_ALL, false);
        }
    }
}
