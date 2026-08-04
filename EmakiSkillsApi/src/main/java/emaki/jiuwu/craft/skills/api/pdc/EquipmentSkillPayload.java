package emaki.jiuwu.craft.skills.api.pdc;

import java.util.List;
import java.util.Map;

/**
 * 已解码的装备技能 payload，是 {@link EquipmentSkillPdcCodec} 的读写单位。
 *
 * <p>构造器只做防御性拷贝与 null 归一，<strong>不做语义规范化</strong>：id 的小写化、
 * 去重排序与槽位别名折叠都在
 * {@link EquipmentSkillPdcCodec#normalize(Iterable, String, Map)} 中完成。
 * 因此直接 {@code new} 出来的实例可能带有未规范化的值，写入前仍会被再次规范化。
 *
 * @param skillIds      技能 id 列表；经 codec 读出时已去重并按自然序排序
 * @param activeSlot    生效槽位，取 {@code EquipmentSkillPdcCodec.SLOT_*} 常量；
 *                      经 codec 读出时空值已折叠为 {@code all}
 * @param boundTriggers 技能 id 到触发器 id 的绑定；其 key 是 {@code skillIds} 的子集
 */
public record EquipmentSkillPayload(
        List<String> skillIds,
        String activeSlot,
        Map<String, String> boundTriggers) {

    public EquipmentSkillPayload {
        skillIds = skillIds == null || skillIds.isEmpty() ? List.of() : List.copyOf(skillIds);
        activeSlot = activeSlot == null ? "" : activeSlot;
        boundTriggers = boundTriggers == null || boundTriggers.isEmpty() ? Map.of() : Map.copyOf(boundTriggers);
    }

    /**
     * {@return whether this payload carries no skills}
     *
     * 只看 {@code skillIds}：写入一个 empty payload 等价于
     * {@link EquipmentSkillPdcCodec#clear(org.bukkit.inventory.ItemStack)}。
     */
    public boolean empty() {
        return skillIds.isEmpty();
    }
}
