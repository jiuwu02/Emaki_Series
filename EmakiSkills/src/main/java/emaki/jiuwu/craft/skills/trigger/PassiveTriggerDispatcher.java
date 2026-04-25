package emaki.jiuwu.craft.skills.trigger;

import java.util.List;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.model.SkillActivationType;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;

public final class PassiveTriggerDispatcher {

    private final TriggerRegistry triggerRegistry;
    private final PlayerSkillStateService stateService;
    private final CastAttemptService castAttemptService;

    public PassiveTriggerDispatcher(TriggerRegistry triggerRegistry,
            PlayerSkillStateService stateService,
            CastAttemptService castAttemptService) {
        this.triggerRegistry = triggerRegistry;
        this.stateService = stateService;
        this.castAttemptService = castAttemptService;
    }

    public void dispatch(TriggerInvocation invocation) {
        if (invocation == null || invocation.player() == null || invocation.triggerId() == null) {
            return;
        }

        SkillTriggerDefinition trigger = triggerRegistry.get(invocation.triggerId());
        if (trigger == null || !trigger.enabled() || trigger.category() != TriggerCategory.PASSIVE) {
            return;
        }

        Player player = invocation.player();
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedSkills(player);
        for (UnlockedSkillEntry entry : unlocked) {
            SkillDefinition definition = stateService.getDefinition(entry.skillId());
            if (!isMatchingPassiveSkill(definition, invocation.triggerId())) {
                continue;
            }
            castAttemptService.attemptPassiveCast(player, invocation.triggerId(), definition, invocation);
        }
    }

    private boolean isMatchingPassiveSkill(SkillDefinition definition, String triggerId) {
        return definition != null
                && definition.enabled()
                && definition.activationType() == SkillActivationType.PASSIVE
                && definition.passiveTriggers().contains(triggerId);
    }
}
