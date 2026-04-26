package emaki.jiuwu.craft.skills.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterDefinition;
import emaki.jiuwu.craft.skills.model.SkillUpgradeConfig;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class SkillParameterResolver {

    private final SkillLevelService levelService;
    private final Logger logger;
    private final Set<String> reportedNumericIssues = ConcurrentHashMap.newKeySet();
    private final Set<String> reportedTextIssues = ConcurrentHashMap.newKeySet();

    public SkillParameterResolver(SkillLevelService levelService) {
        this(levelService, null);
    }

    public SkillParameterResolver(SkillLevelService levelService, JavaPlugin plugin) {
        this.levelService = levelService;
        this.logger = plugin == null ? null : plugin.getLogger();
    }

    public ResolvedSkillParameters resolve(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation) {
        if (player == null || definition == null) {
            return ResolvedSkillParameters.empty();
        }
        int level = levelService.currentLevel(player, definition);
        return resolveAtLevel(player, definition, triggerId, invocation, level, level, level);
    }

    public ResolvedSkillParameters resolveAtLevel(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            int level,
            int targetLevel,
            int currentLevel) {
        if (player == null || definition == null) {
            return ResolvedSkillParameters.empty();
        }
        int effectiveLevel = Math.max(1, level);
        int effectiveTargetLevel = Math.max(1, targetLevel);
        Map<String, Object> variables = variables(player, definition, triggerId, invocation,
                effectiveLevel, effectiveTargetLevel);
        variables.put("current_level", Math.max(1, currentLevel));
        Map<String, SkillParameterDefinition> definitions = new LinkedHashMap<>(definition.skillParameters());
        SkillUpgradeConfig.SkillUpgradeLevel upgradeLevel = definition.upgrade().levels().get(effectiveLevel);
        if (upgradeLevel != null && !upgradeLevel.parameters().isEmpty()) {
            definitions.putAll(upgradeLevel.parameters());
        }

        Map<String, String> resolved = new LinkedHashMap<>();
        for (SkillParameterDefinition parameter : definitions.values()) {
            if (parameter == null || Texts.isBlank(parameter.id()) || parameter.id().startsWith("emaki_")) {
                continue;
            }
            resolved.put(parameter.id(), resolveSingle(parameter, variables));
        }
        resolved.put("emaki_skill_id", definition.id());
        resolved.put("emaki_skill_level", Integer.toString(effectiveLevel));
        resolved.put("emaki_trigger_id", Texts.toStringSafe(triggerId));
        resolved.put("emaki_is_passive", definition.activationType() == SkillActivationType.PASSIVE ? "1" : "0");
        resolved.put("emaki_has_target", invocation != null && invocation.targetEntity() != null ? "1" : "0");
        return new ResolvedSkillParameters(resolved);
    }

    public Map<String, Object> variables(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            int level,
            int targetLevel) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("level", Math.max(1, level));
        variables.put("target_level", Math.max(1, targetLevel));
        variables.put("max_level", levelService.maxLevel(definition));
        variables.put("skill_id", definition == null ? "" : definition.id());
        variables.put("trigger_id", Texts.toStringSafe(triggerId));
        variables.put("is_passive", definition != null && definition.activationType() == SkillActivationType.PASSIVE ? 1 : 0);
        variables.put("player_level", player == null ? 0 : player.getLevel());
        variables.put("player_health", player == null ? 0D : player.getHealth());
        variables.put("player_max_health", player == null || player.getAttribute(Attribute.MAX_HEALTH) == null
                ? 0D
                : player.getAttribute(Attribute.MAX_HEALTH).getValue());
        variables.put("sneaking", player != null && player.isSneaking() ? 1 : 0);
        variables.put("has_target", invocation != null && invocation.targetEntity() != null ? 1 : 0);
        return variables;
    }

    private String resolveSingle(SkillParameterDefinition parameter, Map<String, Object> variables) {
        Object config = parameter.config();
        return switch (parameter.type()) {
            case STRING, RANDOM_TEXT -> resolveText(parameter, config, variables);
            case BOOLEAN -> Boolean.toString(ExpressionEngine.evaluateBoolean(Texts.toStringSafe(config), variables,
                    ExpressionEngine.evaluateBoolean(parameter.defaultValue(), variables, false)));
            case CONSTANT, RANGE, UNIFORM, GAUSSIAN, SKEW_NORMAL, TRIANGLE, EXPRESSION -> resolveNumeric(parameter, config, variables);
        };
    }

    private String resolveText(SkillParameterDefinition parameter, Object config, Map<String, Object> variables) {
        Object effectiveConfig = config == null && Texts.isNotBlank(parameter.defaultValue())
                ? parameter.defaultValue()
                : config;
        ExpressionEngine.TextEvaluationResult result = ExpressionEngine.evaluateStringConfigDetailed(effectiveConfig,
                variables);
        if (result.hasIssues()) {
            reportTextIssues(parameter, result);
        }
        return result.value();
    }

    private String resolveNumeric(SkillParameterDefinition parameter, Object config, Map<String, Object> variables) {
        Object effectiveConfig = config == null && Texts.isNotBlank(parameter.defaultValue())
                ? parameter.defaultValue()
                : config;
        ExpressionEngine.NumericEvaluationResult result = ExpressionEngine.evaluateRandomConfigDetailed(effectiveConfig,
                variables);
        if (result.hasIssues()) {
            reportNumericIssues(parameter, result);
        }
        double value = result.success() ? result.value() : 0D;
        if (parameter.min() != null) {
            value = Math.max(parameter.min(), value);
        }
        if (parameter.max() != null) {
            value = Math.min(parameter.max(), value);
        }
        if (parameter.decimals() <= 0) {
            return Long.toString(Math.round(value));
        }
        StringBuilder pattern = new StringBuilder("0.");
        pattern.append("#".repeat(parameter.decimals()));
        return Numbers.formatNumber(value, pattern.toString());
    }

    private void reportNumericIssues(SkillParameterDefinition parameter,
            ExpressionEngine.NumericEvaluationResult result) {
        if (logger == null || parameter == null || !result.hasIssues()) {
            return;
        }
        String message = "Skill parameter '" + parameter.id()
                + "' numeric expression/config issue: " + String.join("; ", result.issues());
        if (reportedNumericIssues.add(message)) {
            logger.warning(message);
        }
    }

    private void reportTextIssues(SkillParameterDefinition parameter,
            ExpressionEngine.TextEvaluationResult result) {
        if (logger == null || parameter == null || !result.hasIssues()) {
            return;
        }
        String message = "Skill parameter '" + parameter.id()
                + "' text expression/config issue: " + String.join("; ", result.issues());
        if (reportedTextIssues.add(message)) {
            logger.warning(message);
        }
    }
}
