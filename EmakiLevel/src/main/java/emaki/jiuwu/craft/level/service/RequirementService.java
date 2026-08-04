package emaki.jiuwu.craft.level.service;

import java.util.LinkedHashMap;
import java.util.Map;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.config.RequirementConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class RequirementService {

    private RequirementConfig config = RequirementConfig.parse(null);

    public void reload(RequirementConfig config) {
        this.config = config == null ? RequirementConfig.parse(null) : config;
    }

    public double requiredExp(LevelTypeConfig type, PlayerLevelEntry entry, int targetLevel) {
        if (type == null) {
            return 0D;
        }
        double value = resolveValue(type, targetLevel);
        if (value > 0D) {
            return value;
        }
        String formula = resolveFormula(type);
        if (Texts.isBlank(formula)) {
            return 0D;
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("current_level", entry == null ? type.startLevel() : entry.level());
        variables.put("target_level", targetLevel);
        variables.put("max_level", type.maxLevel());
        variables.put("exp", entry == null ? 0D : entry.exp());
        variables.put("total_exp", entry == null ? 0D : entry.totalExp());
        return Math.max(0D, ExpressionEngine.evaluate(formula, variables));
    }

    public String debugSource(LevelTypeConfig type, int targetLevel) {
        if (type == null) {
            return "none";
        }
        if (type.requirement().values().containsKey(targetLevel)) {
            return "type.values";
        }
        if (Texts.isNotBlank(type.requirement().formula())) {
            return "type.formula";
        }
        RequirementConfig.RequirementGroup group = config.groups().get(type.requirement().group());
        if (group != null && group.values().containsKey(targetLevel)) {
            return "groups." + type.requirement().group() + ".values";
        }
        if (group != null && Texts.isNotBlank(group.formula())) {
            return "groups." + type.requirement().group() + ".formula";
        }
        if (config.global().values().containsKey(targetLevel)) {
            return "global.values";
        }
        return "global.formula";
    }

    private double resolveValue(LevelTypeConfig type, int targetLevel) {
        Double value = type.requirement().values().get(targetLevel);
        if (value != null) {
            return value;
        }
        RequirementConfig.RequirementGroup group = config.groups().get(type.requirement().group());
        if (group != null) {
            value = group.values().get(targetLevel);
            if (value != null) {
                return value;
            }
        }
        value = config.global().values().get(targetLevel);
        return value == null ? 0D : value;
    }

    private String resolveFormula(LevelTypeConfig type) {
        if (Texts.isNotBlank(type.requirement().formula())) {
            return type.requirement().formula();
        }
        RequirementConfig.RequirementGroup group = config.groups().get(type.requirement().group());
        if (group != null && Texts.isNotBlank(group.formula())) {
            return group.formula();
        }
        return config.global().formula();
    }
}
