package emaki.jiuwu.craft.skills.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.corelib.trigger.TriggerInvocation;

public final class SkillParameterResolver {

    private final SkillLevelService levelService;

    public SkillParameterResolver(SkillLevelService levelService) {
        this.levelService = levelService;
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

}
