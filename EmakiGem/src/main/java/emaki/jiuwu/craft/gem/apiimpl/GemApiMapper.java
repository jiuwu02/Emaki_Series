package emaki.jiuwu.craft.gem.apiimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.api.model.GemDefinitionView;
import emaki.jiuwu.craft.gem.api.model.GemRelationshipCheck;
import emaki.jiuwu.craft.gem.api.model.GemResonanceSlotView;
import emaki.jiuwu.craft.gem.api.model.GemResonanceView;
import emaki.jiuwu.craft.gem.api.model.GemSlotView;
import emaki.jiuwu.craft.gem.api.model.GemStateView;
import emaki.jiuwu.craft.gem.model.GemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemDefinition;
import emaki.jiuwu.craft.gem.model.GemItemInstance;
import emaki.jiuwu.craft.gem.model.GemResonanceDefinition;
import emaki.jiuwu.craft.gem.model.GemState;
import emaki.jiuwu.craft.gem.service.GemStateService;

/**
 * runtime 模型到 API 只读视图的映射工具。
 *
 * <p>集中放在一处，避免 Catalog 与 Operations 各写一份导致视图字段漂移。
 */
final class GemApiMapper {

    private GemApiMapper() {
    }

    /**
     * 把宝石定义按指定等级解析为只读视图。
     *
     * @param definition runtime 宝石定义
     * @param level      解析等级
     * @return 只读视图
     */
    static GemDefinitionView toDefinitionView(GemDefinition definition, int level) {
        int resolved = Math.max(1, level);
        return new GemDefinitionView(Texts.lower(definition.id()),
                definition.displayNameForLevel(resolved),
                definition.gemType(),
                resolved,
                definition.statsForLevel(resolved),
                definition.attributesForLevel(resolved),
                definition.skillIdsForLevel(resolved),
                definition.socketCompatibility(),
                definition.dependencies(),
                definition.conflicts());
    }

    /**
     * 把装备定义与已存状态合并为完整的插槽视图列表。
     *
     * <p>逐槽输出而非只输出已镶嵌的槽，调用方无需再回查定义即可渲染整条插槽条。
     *
     * @param itemDefinition 装备定义
     * @param state          已存状态；为 {@code null} 时视为全空
     * @return 只读状态视图
     */
    static GemStateView toStateView(GemItemDefinition itemDefinition, GemState state) {
        List<GemSlotView> slots = new ArrayList<>();
        for (GemItemDefinition.SocketSlot slot : itemDefinition.slots()) {
            GemItemInstance assignment = state == null ? null : state.assignment(slot.index());
            boolean opened = state != null && state.isOpened(slot.index());
            slots.add(new GemSlotView(slot.index(),
                    slot.type(),
                    slot.displayName(),
                    opened,
                    assignment == null ? null : Texts.lower(assignment.gemId()),
                    assignment == null ? 0 : assignment.level()));
        }
        return new GemStateView(Texts.lower(itemDefinition.id()),
                slots,
                state == null ? 0L : state.updatedAt());
    }

    /**
     * 把 runtime 的关系校验结果映射为 API 视图。
     *
     * @param check runtime 校验结果
     * @return API 视图
     */
    static GemRelationshipCheck toRelationshipCheck(GemStateService.RelationshipCheck check) {
        if (check == null) {
            return GemRelationshipCheck.deny("gem.error.condition_not_met", Map.of());
        }
        return check.allowed()
                ? GemRelationshipCheck.pass()
                : GemRelationshipCheck.deny(check.messageKey(), check.placeholders());
    }

    /**
     * 把共鸣定义映射为只读视图。
     *
     * @param definition runtime 共鸣定义
     * @return 只读视图
     */
    static GemResonanceView toResonanceView(GemResonanceDefinition definition) {
        List<GemResonanceSlotView> pattern = definition.chain().pattern().stream()
                .map(entry -> new GemResonanceSlotView(entry.id(), entry.type(), entry.minLevel()))
                .toList();
        return new GemResonanceView(Texts.lower(definition.id()),
                definition.displayName(),
                definition.priority(),
                definition.exclusiveGroup(),
                pattern,
                definition.chain().isOrdered());
    }
}
