package emaki.jiuwu.craft.corelib.action.select;

import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

public record TargetConditionContext(@NotNull TargetFacts facts,
        @NotNull Function<String, String> renderer,
        @NotNull TargetConditionHost host) {

    public TargetConditionContext {
        facts = facts == null ? TargetFacts.absent() : facts;
        renderer = renderer == null ? text -> text : renderer;
        host = host == null ? TargetConditionHost.UNANSWERABLE : host;
    }

    public static TargetConditionContext of(TargetFacts facts) {
        return new TargetConditionContext(facts, null, null);
    }

    public static TargetConditionContext of(TargetFacts facts, Function<String, String> renderer) {
        return new TargetConditionContext(facts, renderer, null);
    }
}