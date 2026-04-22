package emaki.jiuwu.craft.skills.trigger;

import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.CastAttemptResult;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.CastModeService;
import emaki.jiuwu.craft.skills.service.PlayerSkillDataStore;

/**
 * Default dispatcher that connects trigger invocations to the cast pipeline.
 * Only processes triggers when the player is in cast mode and the trigger
 * is both enabled and bound to a skill slot.
 */
public final class DefaultTriggerDispatcher implements TriggerDispatcher {

    private final CastModeService castModeService;
    private final TriggerRegistry triggerRegistry;
    private final PlayerSkillDataStore dataStore;
    private final CastAttemptService castAttemptService;
    private final Supplier<AppConfig> configSupplier;
    private final MessageService messageService;

    public DefaultTriggerDispatcher(CastModeService castModeService,
                                    TriggerRegistry triggerRegistry,
                                    PlayerSkillDataStore dataStore,
                                    CastAttemptService castAttemptService,
                                    Supplier<AppConfig> configSupplier,
                                    MessageService messageService) {
        this.castModeService = castModeService;
        this.triggerRegistry = triggerRegistry;
        this.dataStore = dataStore;
        this.castAttemptService = castAttemptService;
        this.configSupplier = configSupplier;
        this.messageService = messageService;
    }

    @Override
    public void dispatch(TriggerInvocation invocation) {
        Player player = invocation.player();
        String triggerId = invocation.triggerId();

        // 1. Not in cast mode -> ignore, let original action through
        if (!castModeService.isCastModeEnabled(player)) {
            return;
        }

        // 2. Trigger not enabled -> ignore
        if (!triggerRegistry.isEnabled(triggerId)) {
            return;
        }

        // 3. No slot bound to this trigger -> let original action through
        SkillSlotBinding binding = findBoundSlot(player, triggerId);
        if (binding == null) {
            return;
        }

        // 4. Bound trigger found -> cancel original action
        invocation.setCancelOriginalAction(true);

        // 5. Attempt cast
        CastAttemptResult result = castAttemptService.attemptCast(player, triggerId, binding);

        // 6. Send failure message if cast failed
        if (!result.success() && result.failureMessage() != null && !result.failureMessage().isBlank()) {
            messageService.send(player, result.failureMessage());
        }
    }

    private SkillSlotBinding findBoundSlot(Player player, String triggerId) {
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return null;
        }
        for (SkillSlotBinding binding : profile.bindings()) {
            if (!binding.isEmpty() && triggerId.equals(binding.triggerId())) {
                return binding;
            }
        }
        return null;
    }
}
