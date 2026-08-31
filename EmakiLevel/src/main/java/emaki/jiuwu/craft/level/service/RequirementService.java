package emaki.jiuwu.craft.level.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.progression.CompositeProgression;
import emaki.jiuwu.craft.corelib.progression.FormulaProgression;
import emaki.jiuwu.craft.corelib.progression.Progression;
import emaki.jiuwu.craft.corelib.progression.TableProgression;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;
import emaki.jiuwu.craft.level.config.RequirementConfig;
import emaki.jiuwu.craft.level.model.PlayerLevelEntry;

public final class RequirementService {

    private RequirementConfig config = RequirementConfig.parse(null);
    private final Map<String, Progression<Double>> progressionCache = new ConcurrentHashMap<>();

    public void reload(RequirementConfig config) {
        this.config = config == null ? RequirementConfig.parse(null) : config;
        progressionCache.clear();
    }

    public double requiredExp(LevelTypeConfig type, PlayerLevelEntry entry, int targetLevel) {
        if (type == null) {
            return 0D;
        }
        Progression<Double> progression = progressionCache.computeIfAbsent(type.id(), id -> buildProgression(type));
        Double value = progression.valueAt(targetLevel);
        return Math.max(0D, value == null ? 0D : value);
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

    private Progression<Double> buildProgression(LevelTypeConfig type) {
        Map<Integer, Double> mergedTable = new LinkedHashMap<>();
        mergedTable.putAll(config.global().values());
        RequirementConfig.RequirementGroup group = config.groups().get(type.requirement().group());
        if (group != null) {
            mergedTable.putAll(group.values());
        }
        mergedTable.putAll(type.requirement().values());

        String formula = resolveFormula(type);

        TableProgression<Double> tableProgression = new TableProgression<>(mergedTable, null);
        if (Texts.isBlank(formula)) {
            return tableProgression;
        }

        final int maxLevel = type.maxLevel();
        final int startLevel = type.startLevel();
        FormulaProgression<Double> formulaProgression = FormulaProgression.forDouble(
                formula,
                level -> {
                    Map<String, Object> vars = new LinkedHashMap<>();
                    vars.put("target_level", level);
                    vars.put("max_level", maxLevel);
                    vars.put("current_level", startLevel);
                    vars.put("exp", 0D);
                    vars.put("total_exp", 0D);
                    return vars;
                },
                0D
        );

        return CompositeProgression.<Double>builder()
                .add(tableProgression)
                .add(formulaProgression)
                .fallback(0D)
                .build();
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
