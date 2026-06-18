package emaki.jiuwu.craft.skills.script;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class SkillScriptCastService {

    private final EmakiSkillsPlugin plugin;
    private final SkillVariableResolver variableResolver;
    private final SkillScriptExecutor executor;

    public SkillScriptCastService(EmakiSkillsPlugin plugin,
            SkillVariableResolver variableResolver,
            SkillScriptExecutor executor) {
        this.plugin = plugin;
        this.variableResolver = variableResolver;
        this.executor = executor;
    }

    public boolean cast(Player caster,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        if (caster == null || definition == null || !definition.script().enabled()) {
            return false;
        }
        Map<String, String> variables = variableResolver.resolve(caster, definition, triggerId, invocation, parameters);
        SkillScriptContext context = new SkillScriptContext(plugin, caster, definition, triggerId, invocation, variables);
        context.refreshTargetVariables();
        CompletableFuture<SkillActionResult> future = executor.executePhase(context, definition.script(), SkillScriptPhase.CAST)
                .thenCompose(result -> {
                    if (!result.success() && definition.script().stopOnFailure()) {
                        return CompletableFuture.completedFuture(result);
                    }
                    SkillScriptPhase next = context.hasTarget() ? SkillScriptPhase.HIT : SkillScriptPhase.MISS;
                    return executor.executePhase(context, definition.script(), next);
                });
        try {
            SkillActionResult result = future.join();
            if (!result.success()) {
                executor.executePhase(context, definition.script(), SkillScriptPhase.FAIL).join();
            }
            return result.success();
        } catch (Exception exception) {
            executor.executePhase(context, definition.script(), SkillScriptPhase.FAIL).join();
            return false;
        }
    }
}
