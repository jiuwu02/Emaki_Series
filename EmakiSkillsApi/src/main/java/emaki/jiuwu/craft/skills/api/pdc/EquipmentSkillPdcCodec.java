package emaki.jiuwu.craft.skills.api.pdc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 装备技能 PDC 的读写编解码器，是 EmakiSkills 与其他模块之间的 wire protocol。
 *
 * <h2>持久化格式（跨版本稳定契约）</h2>
 *
 * 三个 {@link NamespacedKey} 全部使用命名空间 {@code emaki_skills}，值类型均为
 * {@link PersistentDataType#STRING}：
 *
 * <ul>
 *   <li>{@code item.skills.ids} —— 技能 id 列表，以 {@code ;} 分隔并按自然序排序，
 *       例如 {@code fireball;heal}</li>
 *   <li>{@code item.skills.active_slot} —— 生效槽位，取本类的 {@code SLOT_*} 常量之一</li>
 *   <li>{@code item.skills.triggers} —— 技能到触发器的绑定，形如
 *       {@code skillId=triggerId} 的键值对再以 {@code ;} 分隔，按 key 排序</li>
 * </ul>
 *
 * <p><strong>三个 key 是同一份 payload 的组成部分</strong>：{@code clear} 会一并移除三者，
 * 只写其中一个会得到不完整的 payload。判断物品是否携带 payload 用
 * {@link #hasPayload(ItemStack)}，它对三个 key 取或。
 *
 * <h2>规范化语义</h2>
 *
 * 写入前一律先经 {@link #normalize(Iterable, String, Map)}：技能 id 去空白、转小写、空格转
 * 下划线并去重排序；触发器 id 在此基础上再把 {@code -} 转为 {@code _}。
 * {@code boundTriggers} 的 key 会被并入技能 id 集合，因此绑定了触发器的技能不必再单独列入
 * {@code skillIds}。规范化后若技能集合为空，写入等价于 {@code clear}。
 *
 * <p>槽位匹配见 {@link #matchesSlot(String, String)}：{@code all} 匹配一切，
 * 而 {@code hand} 同时匹配 {@code main_hand} 与 {@code off_hand}。
 *
 * <h2>失败语义</h2>
 *
 * 所有写操作返回 {@link SkillPdcMutation} 而不抛异常，其 {@code committed} 表示
 * {@code setItemMeta} 是否成功，{@code reason} 在未提交时给出原因
 * （{@code item_missing}、{@code item_meta_missing}、{@code payload_absent}）。
 *
 * <p>本类被需要读写该 payload 的运行时模块 shade 并 relocate，因此
 * <strong>key 名称与编码格式的任何改动都是跨模块的破坏性变更</strong>。
 */
public final class EquipmentSkillPdcCodec {

    public static final String SLOT_ALL = "all";
    public static final String SLOT_HAND = "hand";
    public static final String SLOT_MAIN_HAND = "main_hand";
    public static final String SLOT_OFF_HAND = "off_hand";
    public static final String SLOT_HELMET = "helmet";
    public static final String SLOT_CHESTPLATE = "chestplate";
    public static final String SLOT_LEGGINGS = "leggings";
    public static final String SLOT_BOOTS = "boots";

    private static final NamespacedKey SKILL_IDS_KEY = new NamespacedKey("emaki_skills", "item.skills.ids");
    private static final NamespacedKey SKILL_ACTIVE_SLOT_KEY = new NamespacedKey("emaki_skills", "item.skills.active_slot");
    private static final NamespacedKey SKILL_TRIGGERS_KEY = new NamespacedKey("emaki_skills", "item.skills.triggers");

    private EquipmentSkillPdcCodec() {
    }

    /** {@return the PDC key holding the {@code ;}-separated, sorted skill id list} */
    public static NamespacedKey skillIdsKey() {
        return SKILL_IDS_KEY;
    }

    /** {@return the PDC key holding the required slot, one of the {@code SLOT_*} constants} */
    public static NamespacedKey activeSlotKey() {
        return SKILL_ACTIVE_SLOT_KEY;
    }

    /** {@return the PDC key holding {@code skillId=triggerId} pairs joined by {@code ;}} */
    public static NamespacedKey boundTriggersKey() {
        return SKILL_TRIGGERS_KEY;
    }

    public static EquipmentSkillPayload normalize(
            Iterable<String> skillIds,
            String activeSlot,
            Map<String, String> boundTriggers) {
        Map<String, String> normalizedTriggers = normalizeTriggers(boundTriggers);
        Set<String> normalizedIds = new LinkedHashSet<>();
        addNormalizedIds(normalizedIds, skillIds);
        addNormalizedIds(normalizedIds, normalizedTriggers.keySet());
        if (normalizedIds.isEmpty()) {
            return new EquipmentSkillPayload(List.of(), SLOT_ALL, Map.of());
        }
        List<String> sortedIds = new ArrayList<>(normalizedIds);
        sortedIds.sort(String::compareTo);
        return new EquipmentSkillPayload(sortedIds, normalizeRequiredSlot(activeSlot), normalizedTriggers);
    }

    public static EquipmentSkillPayload read(ItemStack itemStack) {
        RawSnapshot raw = readRaw(itemStack);
        return new EquipmentSkillPayload(
                decodeSkillIds(raw.skillIds()),
                normalizeRequiredSlot(raw.activeSlot()),
                decodeTriggers(raw.boundTriggers())
        );
    }

    public static RawSnapshot readRaw(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta == null ? RawSnapshot.empty() : readRaw(itemMeta.getPersistentDataContainer());
    }

    public static SkillPdcMutation write(ItemStack itemStack, Iterable<String> skillIds) {
        return write(itemStack, skillIds, SLOT_ALL, Map.of());
    }

    public static SkillPdcMutation write(
            ItemStack itemStack,
            Iterable<String> skillIds,
            String activeSlot,
            Map<String, String> boundTriggers) {
        return write(itemStack, normalize(skillIds, activeSlot, boundTriggers));
    }

    public static SkillPdcMutation write(ItemStack itemStack, EquipmentSkillPayload payload) {
        RawSnapshot before = readRaw(itemStack);
        if (itemStack == null) {
            return mutation("skill_write", before, before, false, "item_missing");
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return mutation("skill_write", before, before, false, "item_meta_missing");
        }
        EquipmentSkillPayload normalized = payload == null
                ? normalize(List.of(), SLOT_ALL, Map.of())
                : normalize(payload.skillIds(), payload.activeSlot(), payload.boundTriggers());
        if (normalized.empty()) {
            return clear(itemStack, itemMeta, before);
        }

        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        container.set(SKILL_IDS_KEY, PersistentDataType.STRING, String.join(";", normalized.skillIds()));
        container.set(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING, normalized.activeSlot());
        if (normalized.boundTriggers().isEmpty()) {
            container.remove(SKILL_TRIGGERS_KEY);
        } else {
            container.set(SKILL_TRIGGERS_KEY, PersistentDataType.STRING, encodeTriggers(normalized.boundTriggers()));
        }
        boolean committed = itemStack.setItemMeta(itemMeta);
        return mutation("skill_write", before, readRaw(itemStack), committed, "");
    }

    public static SkillPdcMutation clear(ItemStack itemStack) {
        RawSnapshot before = readRaw(itemStack);
        if (itemStack == null) {
            return mutation("skill_clear", before, before, false, "item_missing");
        }
        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return mutation("skill_clear", before, before, false, "item_meta_missing");
        }
        return clear(itemStack, itemMeta, before);
    }

    public static SkillPdcMutation copy(ItemStack original, ItemStack rebuilt) {
        RawSnapshot before = readRaw(rebuilt);
        if (original == null || rebuilt == null) {
            return mutation("skill_copy", before, before, false, "item_missing");
        }
        ItemMeta rebuiltMeta = rebuilt.getItemMeta();
        if (rebuiltMeta == null) {
            return mutation("skill_copy", before, before, false, "item_meta_missing");
        }

        RawSnapshot source = readRaw(original);
        if (isBlank(source.skillIds())) {
            return clear(rebuilt, rebuiltMeta, before, "skill_copy");
        }

        PersistentDataContainer container = rebuiltMeta.getPersistentDataContainer();
        container.set(SKILL_IDS_KEY, PersistentDataType.STRING, source.skillIds());
        container.set(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING, normalizeRequiredSlot(source.activeSlot()));
        if (isBlank(source.boundTriggers())) {
            container.remove(SKILL_TRIGGERS_KEY);
        } else {
            container.set(SKILL_TRIGGERS_KEY, PersistentDataType.STRING, source.boundTriggers());
        }
        boolean committed = rebuilt.setItemMeta(rebuiltMeta);
        return mutation("skill_copy", before, readRaw(rebuilt), committed, "");
    }

    public static boolean hasPayload(ItemStack itemStack) {
        ItemMeta itemMeta = itemStack == null ? null : itemStack.getItemMeta();
        return itemMeta != null && hasPayload(itemMeta.getPersistentDataContainer());
    }

    public static boolean matchesSlot(String actualSlot, String requiredSlot) {
        String normalizedRequired = normalizeRequiredSlot(requiredSlot);
        if (SLOT_ALL.equals(normalizedRequired)) {
            return true;
        }
        String normalizedActual = normalizeActualSlot(actualSlot);
        if (isBlank(normalizedActual)) {
            return false;
        }
        if (normalizedRequired.equals(normalizedActual)) {
            return true;
        }
        return SLOT_HAND.equals(normalizedRequired)
                && (SLOT_MAIN_HAND.equals(normalizedActual) || SLOT_OFF_HAND.equals(normalizedActual));
    }

    public static String normalizeSkillId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public static String normalizeTriggerId(String value) {
        return normalizeSkillId(value).replace('-', '_');
    }

    public static String normalizeRequiredSlot(String slot) {
        String normalized = normalizeSlot(slot);
        return isBlank(normalized) ? SLOT_ALL : normalized;
    }

    public static String normalizeActualSlot(String slot) {
        return normalizeSlot(slot);
    }

    private static SkillPdcMutation clear(ItemStack itemStack, ItemMeta itemMeta, RawSnapshot before) {
        return clear(itemStack, itemMeta, before, "skill_clear");
    }

    private static SkillPdcMutation clear(
            ItemStack itemStack,
            ItemMeta itemMeta,
            RawSnapshot before,
            String operation) {
        if (!hasPayload(itemMeta.getPersistentDataContainer())) {
            return mutation(operation, before, before, false, "payload_absent");
        }
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        container.remove(SKILL_IDS_KEY);
        container.remove(SKILL_ACTIVE_SLOT_KEY);
        container.remove(SKILL_TRIGGERS_KEY);
        boolean committed = itemStack.setItemMeta(itemMeta);
        return mutation(operation, before, readRaw(itemStack), committed, "");
    }

    private static RawSnapshot readRaw(PersistentDataContainer container) {
        if (container == null) {
            return RawSnapshot.empty();
        }
        return new RawSnapshot(
                container.get(SKILL_IDS_KEY, PersistentDataType.STRING),
                container.get(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING),
                container.get(SKILL_TRIGGERS_KEY, PersistentDataType.STRING)
        );
    }

    private static boolean hasPayload(PersistentDataContainer container) {
        return container != null
                && (container.get(SKILL_IDS_KEY, PersistentDataType.STRING) != null
                || container.get(SKILL_ACTIVE_SLOT_KEY, PersistentDataType.STRING) != null
                || container.get(SKILL_TRIGGERS_KEY, PersistentDataType.STRING) != null);
    }

    private static void addNormalizedIds(Set<String> sink, Iterable<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            String normalized = normalizeSkillId(value);
            if (!isBlank(normalized)) {
                sink.add(normalized);
            }
        }
    }

    private static Map<String, String> normalizeTriggers(Map<String, String> boundTriggers) {
        if (boundTriggers == null || boundTriggers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : boundTriggers.entrySet()) {
            String skillId = normalizeSkillId(entry.getKey());
            String triggerId = normalizeTriggerId(entry.getValue());
            if (!isBlank(skillId) && !isBlank(triggerId)) {
                normalized.put(skillId, triggerId);
            }
        }
        return normalized.isEmpty() ? Map.of() : Map.copyOf(normalized);
    }

    private static List<String> decodeSkillIds(String raw) {
        if (isBlank(raw)) {
            return List.of();
        }
        Set<String> decoded = new LinkedHashSet<>();
        for (String entry : raw.split(";")) {
            String skillId = normalizeSkillId(entry);
            if (!isBlank(skillId)) {
                decoded.add(skillId);
            }
        }
        return decoded.isEmpty() ? List.of() : List.copyOf(decoded);
    }

    private static Map<String, String> decodeTriggers(String raw) {
        if (isBlank(raw)) {
            return Map.of();
        }
        Map<String, String> decoded = new LinkedHashMap<>();
        for (String entry : raw.split(";")) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator >= entry.length() - 1) {
                continue;
            }
            String skillId = normalizeSkillId(entry.substring(0, separator));
            String triggerId = normalizeTriggerId(entry.substring(separator + 1));
            if (!isBlank(skillId) && !isBlank(triggerId)) {
                decoded.put(skillId, triggerId);
            }
        }
        return decoded.isEmpty() ? Map.of() : Map.copyOf(decoded);
    }

    private static String encodeTriggers(Map<String, String> triggers) {
        List<String> entries = new ArrayList<>();
        triggers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> entries.add(entry.getKey() + "=" + entry.getValue()));
        return String.join(";", entries);
    }

    private static String normalizeSlot(String slot) {
        String normalized = normalizeSkillId(slot);
        if (isBlank(normalized)) {
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

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static SkillPdcMutation mutation(
            String operation,
            RawSnapshot before,
            RawSnapshot after,
            boolean committed,
            String reason) {
        return new SkillPdcMutation(operation, before, after, committed, reason);
    }
}
