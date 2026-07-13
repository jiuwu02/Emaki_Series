package emaki.jiuwu.craft.skills.trigger;

import java.util.List;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.BoundSkillTrigger;
import emaki.jiuwu.craft.skills.model.CastAttemptResult;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.provider.EquipmentSkillCollector;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;
import emaki.jiuwu.craft.skills.service.PlayerSkillStateService;

public final class DefaultTriggerDispatcher implements TriggerDispatcher {

    private final CastModeService castModeService;
    private final TriggerRegistry triggerRegistry;
    private final PlayerSkillDataStore dataStore;
    private final PlayerSkillStateService stateService;
    private final EquipmentSkillCollector equipmentCollector;
    private final CastAttemptService castAttemptService;
    private final Supplier<AppConfig> configSupplier;
    private final MessageService messageService;

    public DefaultTriggerDispatcher(CastModeService castModeService,
                                    TriggerRegistry triggerRegistry,
                                    PlayerSkillDataStore dataStore,
                                    PlayerSkillStateService stateService,
                                    EquipmentSkillCollector equipmentCollector,
                                    CastAttemptService castAttemptService,
                                    Supplier<AppConfig> configSupplier,
                                    MessageService messageService) {
        this.castModeService = castModeService;
        this.triggerRegistry = triggerRegistry;
        this.dataStore = dataStore;
        this.stateService = stateService;
        this.equipmentCollector = equipmentCollector;
        this.castAttemptService = castAttemptService;
        this.configSupplier = configSupplier;
        this.messageService = messageService;
    }

    @Override
    public void dispatch(TriggerInvocation invocation) {
        Player player = invocation.player();
        String triggerId = invocation.triggerId();

        if (!castModeService.isCastModeEnabled(player)) {
            return;
        }

        if (!triggerRegistry.isEnabled(triggerId)) {
            return;
        }

        SkillSlotBinding binding = findBoundSlot(player, triggerId);
        List<BoundSkillTrigger> directBindings = equipmentCollector == null
                ? List.of()
                : equipmentCollector.collectBoundTriggers(player, triggerId);
        if (binding == null && directBindings.isEmpty()) {
            return;
        }

        invocation.setCancelOriginalAction(true);

        if (binding != null) {
            reportFailure(player, castAttemptService.attemptCast(player, triggerId, binding));
        }
        for (BoundSkillTrigger directBinding : directBindings) {
            if (directBinding == null || !directBinding.valid()) {
                continue;
            }
            SkillDefinition definition = stateService == null ? null : stateService.getDefinition(directBinding.skillId());
            reportFailure(player, castAttemptService.attemptDirectCast(player, directBinding.triggerId(), definition, invocation));
        }
    }

    private void reportFailure(Player player, CastAttemptResult result) {
        if (result != null && !result.success() && result.failureMessage() != null && !result.failureMessage().isBlank()) {
            messageService.send(player, result.failureMessage());
        }
    }

    private SkillSlotBinding findBoundSlot(Player player, String triggerId) {
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return null;
        }
        return profile.findBindingByTrigger(triggerId);
    }
}
