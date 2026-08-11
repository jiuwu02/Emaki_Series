package emaki.jiuwu.craft.codex.advancement.model;

import emaki.jiuwu.craft.corelib.condition.ConditionGroup;
import emaki.jiuwu.craft.corelib.api.text.Texts;













public record AdvancementTrigger(String event, ConditionGroup condition) {

    public AdvancementTrigger {
        event = Texts.normalizeId(event);
        condition = condition == null ? ConditionGroup.empty() : condition;
    }


    public boolean hasCondition() {
        return !condition.emptyGroup();
    }
}
