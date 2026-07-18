package emaki.jiuwu.craft.skills.trigger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
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

    private final Plugin plugin;
    private final CastModeService castModeService;
    private final TriggerRegistry triggerRegistry;
    private final PlayerSkillDataStore dataStore;
    private final PlayerSkillStateService stateService;
    private final EquipmentSkillCollector equipmentCollector;
    private final CastAttemptService castAttemptService;
    private final Supplier<AppConfig> configSupplier;
    private final MessageService messageService;

    public DefaultTriggerDispatcher(Plugin plugin,
            CastModeService castModeService,
            TriggerRegistry triggerRegistry,
            PlayerSkillDataStore dataStore,
            PlayerSkillStateService stateService,
            EquipmentSkillCollector equipmentCollector,
            CastAttemptService castAttemptService,
            Supplier<AppConfig> configSupplier,
            MessageService messageService) {
        this.plugin = plugin;
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
        if (!castModeService.isCastModeEnabled(player) || !triggerRegistry.isEnabled(triggerId)) {
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
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        if (binding != null) {
            chain = chain.thenCompose(_ -> castAttemptService.attemptCast(player, triggerId, binding))
                    .thenCompose(result -> reportFailureAsync(player, result));
        }
        for (BoundSkillTrigger directBinding : directBindings) {
            if (directBinding == null || !directBinding.valid()) {
                continue;
            }
            SkillDefinition definition = stateService == null
                    ? null
                    : stateService.getDefinition(directBinding.skillId());
            chain = chain.thenCompose(_ -> castAttemptService.attemptDirectCast(
                            player, directBinding.triggerId(), definition, invocation))
                    .thenCompose(result -> reportFailureAsync(player, result));
        }
        chain.exceptionally(_ -> null);
    }

    private CompletableFuture<Void> reportFailureAsync(Player player, CastAttemptResult result) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            FoliaSchedulerAdapter.runEntityTask(plugin, player, () -> {
                if (result != null && !result.success()
                        && result.failureMessage() != null
                        && !result.failureMessage().isBlank()) {
                    messageService.send(player, result.failureMessage(), result.replacements());
                }
                future.complete(null);
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private SkillSlotBinding findBoundSlot(Player player, String triggerId) {
        PlayerSkillProfile profile = dataStore.get(player);
        return profile == null ? null : profile.findBindingByTrigger(triggerId);
    }
}
