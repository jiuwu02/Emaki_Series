package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ActionAst;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

/**
 * A run of consecutive stages that share one scheduler domain.
 *
 * <p>Grouping is what keeps a pipeline from costing one dispatch per stage. The design requires that
 * same-domain stages merge: dispatching every stage separately would make a long pipeline worse than
 * v1, which already cost 2-3 dispatches per action.</p>
 *
 * @param domain the shared domain, {@code null} for a group made only of foldable stages
 * @param members stages in written order
 */
public record StageGroup(@Nullable ExecutionDomain domain, @NotNull List<Member> members) {

    public StageGroup {
        members = members == null ? List.of() : List.copyOf(members);
    }

    /**
     * One stage within a group.
     *
     * @param node the AST node
     * @param handle the resolved stage
     */
    public record Member(@NotNull ActionAst.Stage node, @NotNull StageInvoker.Handle handle) {
    }

    /** {@return whether this group needs one dispatch per target rather than one per flow} */
    public boolean perTarget() {
        return members.stream().anyMatch(member -> member.handle().perTarget());
    }

    /**
     * Groups consecutive stages by declared domain.
     *
     * <p>Three rules, taken from the runtime design:</p>
     * <ul>
     *   <li>adjacent stages in the same domain merge into one dispatch;</li>
     *   <li>a foldable ({@code PURE}) gate joins whichever group is adjacent, without a dispatch of
     *       its own;</li>
     *   <li>a domain switch starts a new group carrying the previous group's output flow.</li>
     * </ul>
     *
     * <p>Per-target action stages are kept in their own group: they are invoked once per target, so
     * merging them with a per-flow stage would change how often that stage runs.</p>
     *
     * @param plans resolved stages in written order
     * @return grouped stages
     */
    public static @NotNull List<StageGroup> group(@Nullable List<Plan> plans) {
        if (plans == null || plans.isEmpty()) {
            return List.of();
        }
        List<StageGroup> groups = new ArrayList<>();
        List<Member> current = new ArrayList<>();
        ExecutionDomain currentDomain = null;
        boolean currentPerTarget = false;

        for (Plan plan : plans) {
            boolean planPerTarget = plan.handle().perTarget();
            if (plan.handle().foldable() && !current.isEmpty() && !currentPerTarget) {
                current.add(new Member(plan.node(), plan.handle()));
                continue;
            }
            boolean sameDomain = currentDomain != null && currentDomain == plan.domain();
            boolean mergeable = sameDomain && currentPerTarget == planPerTarget;
            if (!current.isEmpty() && !mergeable) {
                groups.add(new StageGroup(currentDomain, current));
                current = new ArrayList<>();
            }
            if (current.isEmpty()) {
                currentDomain = plan.domain();
                currentPerTarget = planPerTarget;
            }
            current.add(new Member(plan.node(), plan.handle()));
        }
        if (!current.isEmpty()) {
            groups.add(new StageGroup(currentDomain, current));
        }
        return List.copyOf(groups);
    }

    /**
     * One stage with the domain it declared for this invocation.
     *
     * @param node the AST node
     * @param handle the resolved stage
     * @param domain the domain the stage declared
     */
    public record Plan(@NotNull ActionAst.Stage node,
            @NotNull StageInvoker.Handle handle,
            @Nullable ExecutionDomain domain) {

        /** {@return this stage's raw arguments} */
        public @NotNull Map<String, String> arguments() {
            return node.arguments();
        }
    }
}
