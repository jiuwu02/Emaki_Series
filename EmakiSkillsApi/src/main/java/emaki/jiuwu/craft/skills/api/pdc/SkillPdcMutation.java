package emaki.jiuwu.craft.skills.api.pdc;

/**
 * 一次技能 PDC 写操作的结果。{@link EquipmentSkillPdcCodec} 的所有写方法都返回它
 * 而**不抛异常**，因此调用方必须检查 {@code committed} 才能确认写入是否生效。
 *
 * <p>比较 {@code before} 与 {@code after} 可判断物品是否真的发生变化：
 * 例如对已无 payload 的物品调 {@code clear}，会得到 {@code committed == false} 且
 * {@code reason == "payload_absent"}，此时两个快照相同 —— 这属于**无操作而非失败**。
 *
 * @param operation 操作名，取 {@code skill_write}、{@code skill_clear}、{@code skill_copy}
 * @param before    写入前的原始快照
 * @param after     写入后的原始快照；未提交时与 {@code before} 相同
 * @param committed {@code ItemMeta} 是否已成功回写到 {@code ItemStack}
 * @param reason    未提交的原因；已提交时为空串。可能值：{@code item_missing}
 *                  （物品为 null）、{@code item_meta_missing}（取不到 meta）、
 *                  {@code payload_absent}（clear 时本就没有 payload）
 */
public record SkillPdcMutation(
        String operation,
        RawSnapshot before,
        RawSnapshot after,
        boolean committed,
        String reason) {

    public SkillPdcMutation {
        operation = operation == null ? "" : operation;
        before = before == null ? RawSnapshot.empty() : before;
        after = after == null ? RawSnapshot.empty() : after;
        reason = reason == null ? "" : reason;
    }
}
