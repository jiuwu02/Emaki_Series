package emaki.jiuwu.craft.skills.trigger;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.trigger.TriggerCategory;
import emaki.jiuwu.craft.corelib.trigger.TriggerDefinition;
import emaki.jiuwu.craft.corelib.trigger.TriggerInvocation;
import emaki.jiuwu.craft.corelib.trigger.TriggerRegistry;
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
        TriggerDefinition trigger = triggerRegistry.get(invocation.triggerId());
        if (trigger == null || !trigger.enabled() || trigger.category() != TriggerCategory.PASSIVE) {
            return;
        }

        Player player = invocation.player();
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedSkills(player);
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (UnlockedSkillEntry entry : unlocked) {
            SkillDefinition definition = stateService.getDefinition(entry.skillId());
            if (!isMatchingPassiveSkill(definition, invocation.triggerId())) {
                continue;
            }
            chain = chain.thenCompose(_ -> castAttemptService.attemptPassiveCast(
                            player, invocation.triggerId(), definition, invocation))
                    .thenApply(_ -> null);
        }
        chain.exceptionally(_ -> null);
    }

    private boolean isMatchingPassiveSkill(SkillDefinition definition, String triggerId) {
        return definition != null
                && definition.enabled()
                && definition.activationType() == SkillActivationType.PASSIVE
                && definition.passiveTriggers().contains(triggerId);
    }
}
