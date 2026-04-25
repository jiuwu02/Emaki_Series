package emaki.jiuwu.craft.skills.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterDefinition;
import emaki.jiuwu.craft.skills.model.SkillParameterType;
import emaki.jiuwu.craft.skills.model.SkillUpgradeConfig;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class SkillParameterResolver {

    private static final Pattern COMPARISON_PATTERN = Pattern.compile("^(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+)$");

    private final SkillLevelService levelService;

    public SkillParameterResolver(SkillLevelService levelService) {
        this.levelService = levelService;
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
        String raw = Texts.isNotBlank(parameter.formula()) ? parameter.formula() : parameter.value();
        if (Texts.isBlank(raw)) {
            raw = parameter.defaultValue();
        }
        return switch (parameter.type()) {
            case STRING -> Texts.formatTemplate(raw, variables);
            case BOOLEAN -> Boolean.toString(resolveBoolean(raw, variables, parseBoolean(parameter.defaultValue(), false)));
            case NUMBER -> resolveNumber(parameter, raw, variables);
        };
    }

    private String resolveNumber(SkillParameterDefinition parameter, String raw, Map<String, Object> variables) {
        double value = ExpressionEngine.evaluate(raw, variables);
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

    private boolean resolveBoolean(String raw, Map<String, Object> variables, boolean fallback) {
        String prepared = Texts.formatTemplate(raw, variables).trim();
        Boolean literal = parseBooleanObject(prepared);
        if (literal != null) {
            return literal;
        }
        Matcher matcher = COMPARISON_PATTERN.matcher(prepared);
        if (matcher.matches()) {
            double left = ExpressionEngine.evaluate(matcher.group(1), variables);
            double right = ExpressionEngine.evaluate(matcher.group(3), variables);
            return switch (matcher.group(2)) {
                case ">=" -> left >= right;
                case "<=" -> left <= right;
                case "==" -> Double.compare(left, right) == 0;
                case "!=" -> Double.compare(left, right) != 0;
                case ">" -> left > right;
                case "<" -> left < right;
                default -> fallback;
            };
        }
        return ExpressionEngine.evaluate(prepared, variables) > 0D;
    }

    private boolean parseBoolean(String raw, boolean fallback) {
        Boolean value = parseBooleanObject(raw);
        return value == null ? fallback : value;
    }

    private Boolean parseBooleanObject(String raw) {
        String value = Texts.lower(raw).trim();
        if ("true".equals(value) || "yes".equals(value) || "1".equals(value)) {
            return true;
        }
        if ("false".equals(value) || "no".equals(value) || "0".equals(value)) {
            return false;
        }
        return null;
    }
}
