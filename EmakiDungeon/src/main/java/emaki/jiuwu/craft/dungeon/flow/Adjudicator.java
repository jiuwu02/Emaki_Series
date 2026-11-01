package emaki.jiuwu.craft.dungeon.flow;

import java.util.List;
import java.util.UUID;

import emaki.jiuwu.craft.dungeon.flow.ConditionEvaluator.EvalContext;
import emaki.jiuwu.craft.dungeon.flow.FlowModules.Head;

public final class Adjudicator {

    public record Verdict(boolean allowed, TriggerCounter.ExhaustedTier exhaustedTier, boolean conditionFailed) {

        public static Verdict permitted() {
            return new Verdict(true, TriggerCounter.ExhaustedTier.NONE, false);
        }
    }

    private final ConditionEvaluator evaluator;
    private final TriggerCounter counter;

    public Adjudicator(ConditionEvaluator evaluator, TriggerCounter counter) {
        this.evaluator = evaluator;
        this.counter = counter;
    }

    public Verdict adjudicate(Head head, List<ConditionDef> conditions, EvalContext context, UUID playerId) {
        TriggerCounter.ExhaustedTier exhausted = counter.check(head, playerId);
        if (exhausted != TriggerCounter.ExhaustedTier.NONE) {
            return new Verdict(false, exhausted, false);
        }
        if (!evaluator.evaluateAll(conditions, "AND", context)) {
            return new Verdict(false, TriggerCounter.ExhaustedTier.NONE, true);
        }
        if (!counter.consume(head, playerId)) {
            return new Verdict(false, TriggerCounter.ExhaustedTier.PER_TRIGGER, false);
        }
        return Verdict.permitted();
    }

    public TriggerCounter counter() {
        return counter;
    }

    public ConditionEvaluator evaluator() {
        return evaluator;
    }
}
