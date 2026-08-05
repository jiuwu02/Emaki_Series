package emaki.jiuwu.craft.skills.script;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterType;
import emaki.jiuwu.craft.skills.service.SkillLevelService;
import emaki.jiuwu.craft.skills.service.SkillParameterResolver;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class SkillVariableResolver {

    private final SkillLevelService levelService;
    private final SkillParameterResolver parameterResolver;

    public SkillVariableResolver(SkillLevelService levelService, SkillParameterResolver parameterResolver) {
        this.levelService = levelService;
        this.parameterResolver = parameterResolver;
    }

    public Map<String, String> resolve(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation) {
        Map<String, String> resolved = new LinkedHashMap<>();
        int level = definition == null ? 1 : levelService.currentLevel(player, definition);
        Map<String, Object> expressionVariables = parameterResolver.variables(player, definition, triggerId, invocation, level, level);
        resolved.put("level", Integer.toString(level));
        resolved.put("skill_id", definition == null ? "" : definition.id());
        resolved.put("trigger_id", Texts.toStringSafe(triggerId));
        resolved.put("has_target", invocation != null && invocation.targetEntity() != null ? "1" : "0");

        // The `emaki_`-prefixed keys are what MythicMobs skills read as `<skill.var.emaki_*>`. The loader
        // skips `emaki_` keys in `variables:` (see SkillDefinitionLoader), so they can only be supplied here.
        resolved.put("emaki_skill_id", definition == null ? "" : definition.id());
        resolved.put("emaki_skill_level", Integer.toString(level));
        resolved.put("emaki_trigger_id", Texts.toStringSafe(triggerId));
        resolved.put("emaki_is_passive",
                definition != null && definition.activationType() == SkillActivationType.PASSIVE ? "1" : "0");
        resolved.put("emaki_has_target", invocation != null && invocation.targetEntity() != null ? "1" : "0");

        if (definition != null) {
            for (SkillParameterDefinition variable : definition.variables().values()) {
                String value = resolveVariable(variable, expressionVariables);
                resolved.put(variable.id(), value);
                expressionVariables.put(variable.id(), value);
            }
        }
        return resolved;
    }

    private String resolveVariable(SkillParameterDefinition variable, Map<String, Object> variables) {
        if (variable == null) {
            return "";
        }
        Object config = variable.config() == null && Texts.isNotBlank(variable.defaultValue())
                ? variable.defaultValue()
                : variable.config();
        if (variable.type() == SkillParameterType.STRING
                || variable.type() == SkillParameterType.RANDOM_TEXT
                || variable.type() == SkillParameterType.RANDOM_CHAR
                || variable.type() == SkillParameterType.WEIGHTED_RANDOM_CHAR
                || variable.type() == SkillParameterType.CONDITIONAL_CHAR) {
            return ExpressionEngine.evaluateStringConfig(config, variables);
        }
        if (variable.type() == SkillParameterType.BOOLEAN) {
            return Boolean.toString(ExpressionEngine.evaluateBoolean(Texts.toStringSafe(config), variables,
                    ExpressionEngine.evaluateBoolean(variable.defaultValue(), variables, false)));
        }
        double value = ExpressionEngine.evaluateRandomConfig(config, variables);
        if (variable.min() != null) {
            value = Math.max(variable.min(), value);
        }
        if (variable.max() != null) {
            value = Math.min(variable.max(), value);
        }
        if (variable.decimals() <= 0) {
            return Long.toString(Math.round(value));
        }
        return Double.toString(value);
    }
}
