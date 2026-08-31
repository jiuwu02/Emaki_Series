package emaki.jiuwu.craft.mobs.selector;

import java.util.List;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;

public record SelectorDefinition(
        String id,
        double range,
        SelectorMode mode,
        ConditionBlock filter,
        List<ScoreTerm> score
) {

    public SelectorDefinition {
        score = score == null ? List.of() : List.copyOf(score);
    }
}
