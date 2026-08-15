package emaki.jiuwu.craft.corelib.action.pipeline.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.compile.ActionAst;
import emaki.jiuwu.craft.corelib.runtime.ExecutionDomain;

public record StageGroup(@Nullable ExecutionDomain domain, @NotNull List<Member> members) {

    public StageGroup {
        members = members == null ? List.of() : List.copyOf(members);
    }

    public record Member(@NotNull ActionAst.Stage node, @NotNull StageInvoker.Handle handle) {
    }

    public boolean perTarget() {
        return members.stream().anyMatch(member -> member.handle().perTarget());
    }

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

    public record Plan(@NotNull ActionAst.Stage node,
            @NotNull StageInvoker.Handle handle,
            @Nullable ExecutionDomain domain) {

        public @NotNull Map<String, String> arguments() {
            return node.arguments();
        }
    }
}
