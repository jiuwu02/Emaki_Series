package emaki.jiuwu.craft.skills.api.pdc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 三个技能 PDC key 的**未解码原始字符串**快照，用于变更前后比对与调试输出。
 *
 * <p>与已解码的 {@link EquipmentSkillPayload} 不同，这里保留 PDC 中的原始形态：
 * 分隔符尚未拆开、大小写与别名尚未折叠、缺失的 key 为 {@code null}
 * （{@code null} 表示该 key 不存在，空串表示存在但为空，两者语义不同）。
 *
 * <p>{@link EquipmentSkillPdcCodec} 在每次写操作前后各取一次快照，放入
 * {@link SkillPdcMutation} 的 {@code before} / {@code after}，因此调用方可以判断
 * 「本次写入是否真的改变了物品」。
 *
 * @param skillIds      {@code item.skills.ids} 的原始值，缺失为 {@code null}
 * @param activeSlot    {@code item.skills.active_slot} 的原始值，缺失为 {@code null}
 * @param boundTriggers {@code item.skills.triggers} 的原始值，缺失为 {@code null}
 */
public record RawSnapshot(
        String skillIds,
        String activeSlot,
        String boundTriggers) {

    private static final RawSnapshot EMPTY = new RawSnapshot(null, null, null);

    /** {@return a shared snapshot with all three keys absent} */
    public static RawSnapshot empty() {
        return EMPTY;
    }

    /**
     * {@return present keys as debug field names mapped to their raw values}
     *
     * 只收录非 {@code null} 的 key，字段名为 {@code skill_ids}、{@code active_slot}、
     * {@code skill_triggers}（**与 PDC key 名不同**，是给日志/调试输出用的短名）。
     */
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        if (skillIds != null) {
            values.put("skill_ids", skillIds);
        }
        if (activeSlot != null) {
            values.put("active_slot", activeSlot);
        }
        if (boundTriggers != null) {
            values.put("skill_triggers", boundTriggers);
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }
}
