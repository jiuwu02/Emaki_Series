package emaki.jiuwu.craft.skills.service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.config.AppConfig;
import emaki.jiuwu.craft.skills.model.CastAttemptResult;
import emaki.jiuwu.craft.skills.model.CastAttemptResult.FailureReason;
import emaki.jiuwu.craft.skills.model.CostOperation;
import emaki.jiuwu.craft.skills.model.LocalResourceDefinition;
import emaki.jiuwu.craft.skills.model.PlayerCastTimingState;
import emaki.jiuwu.craft.skills.model.PlayerLocalResourceState;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.ResolvedSkillParameters;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillResourceCost;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.model.UnlockedSkillEntry;
import emaki.jiuwu.craft.skills.mythic.MythicSkillCastService;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;

public final class CastAttemptService {

    private final JavaPlugin plugin;
    private final PlayerSkillStateService stateService;
    private final CastModeService castModeService;
    private final PlayerSkillDataStore dataStore;
    private final MythicSkillCastService mythicCastService;
    private final SkillParameterResolver skillParameterResolver;
    private final EaBridge eaBridge;
    private final Supplier<Map<String, LocalResourceDefinition>> localResourceDefsSupplier;
    private final Supplier<AppConfig> configSupplier;

    public CastAttemptService(JavaPlugin plugin,
            PlayerSkillStateService stateService,
            CastModeService castModeService,
            PlayerSkillDataStore dataStore,
            MythicSkillCastService mythicCastService,
            SkillParameterResolver skillParameterResolver,
            EaBridge eaBridge,
            Supplier<Map<String, LocalResourceDefinition>> localResourceDefsSupplier,
            Supplier<AppConfig> configSupplier) {
        this.plugin = plugin;
        this.stateService = stateService;
        this.castModeService = castModeService;
        this.dataStore = dataStore;
        this.mythicCastService = mythicCastService;
        this.skillParameterResolver = skillParameterResolver;
        this.eaBridge = eaBridge;
        this.localResourceDefsSupplier = localResourceDefsSupplier;
        this.configSupplier = configSupplier;
    }

    public CastAttemptResult attemptCast(Player player, String triggerId) {
        if (player == null || triggerId == null || triggerId.isBlank()) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.invalid_input");
        }

        // 1. Check cast mode
        if (!castModeService.isCastModeEnabled(player)) {
            return CastAttemptResult.fail(FailureReason.NOT_IN_CAST_MODE, "cast.not_in_cast_mode");
        }

        // 2. Find binding for triggerId
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.no_profile");
        }
        SkillSlotBinding binding = findBindingByTrigger(profile, triggerId);
        if (binding == null || binding.isEmpty()) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.no_binding");
        }

        return attemptCastWithBinding(player, triggerId, binding);
    }

    /**
     * Attempts a cast using a pre-resolved binding, skipping the binding lookup.
     */
    public CastAttemptResult attemptCast(Player player, String triggerId, SkillSlotBinding binding) {
        if (player == null || triggerId == null || triggerId.isBlank()) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.invalid_input");
        }
        if (binding == null || binding.isEmpty()) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.no_binding");
        }

        // Check cast mode
        if (!castModeService.isCastModeEnabled(player)) {
            return CastAttemptResult.fail(FailureReason.NOT_IN_CAST_MODE, "cast.not_in_cast_mode");
        }

        return attemptCastWithBinding(player, triggerId, binding);
    }

    public CastAttemptResult attemptPassiveCast(Player player,
            String triggerId,
            SkillDefinition definition,
            TriggerInvocation invocation) {
        if (player == null || triggerId == null || triggerId.isBlank()) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.invalid_input");
        }
        if (definition == null || !definition.enabled()) {
            return CastAttemptResult.fail(FailureReason.SKILL_NOT_FOUND, "skill.not_found");
        }
        return attemptCastWithDefinition(player, definition, triggerId, invocation);
    }

    private CastAttemptResult attemptCastWithBinding(Player player, String triggerId, SkillSlotBinding binding) {

        // 3. Look up SkillDefinition
        SkillDefinition definition = stateService.getDefinition(binding.skillId());
        if (definition == null) {
            return CastAttemptResult.fail(FailureReason.SKILL_NOT_FOUND, "skill.not_found");
        }

        // 4. Check skill still in unlocked pool
        List<UnlockedSkillEntry> unlocked = stateService.getUnlockedSkills(player);
        boolean inPool = false;
        for (UnlockedSkillEntry entry : unlocked) {
            if (entry.skillId().equals(binding.skillId())) {
                inPool = true;
                break;
            }
        }
        if (!inPool) {
            return CastAttemptResult.fail(FailureReason.SOURCE_LOST, "skill.source_lost");
        }

        return attemptCastWithDefinition(player, definition, triggerId, null);
    }

    private CastAttemptResult attemptCastWithDefinition(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation) {
        PlayerSkillProfile profile = dataStore.get(player);
        if (profile == null) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.no_profile");
        }

        // 5. Check forced global delay
        PlayerCastTimingState timing = profile.timingState();
        if (timing.isForcedDelayActive()) {
            return CastAttemptResult.fail(FailureReason.FORCED_DELAY_ACTIVE, "cast.forced_delay");
        }

        // 6. Check global cooldown
        if (timing.isGlobalCooldownActive()) {
            return CastAttemptResult.fail(FailureReason.GLOBAL_COOLDOWN_ACTIVE, "cast.global_cooldown");
        }

        // 7. Check skill cooldown
        if (timing.isSkillOnCooldown(definition.id())) {
            return CastAttemptResult.fail(FailureReason.SKILL_COOLDOWN_ACTIVE, "cast.skill_cooldown");
        }

        // 8. Check resource costs
        CastAttemptResult costCheck = checkResourceCosts(player, profile, definition);
        if (costCheck != null) {
            return costCheck;
        }

        // 9. Cast via MythicMobs
        String mythicSkillId = definition.mythicSkill();
        if (mythicSkillId == null || mythicSkillId.isBlank()) {
            return CastAttemptResult.fail(FailureReason.MYTHIC_SKILL_NOT_FOUND,
                    "cast.mythic_not_configured");
        }
        if (!mythicCastService.skillExists(mythicSkillId)) {
            return CastAttemptResult.fail(FailureReason.MYTHIC_SKILL_NOT_FOUND,
                    "cast.mythic_not_found");
        }
        ResolvedSkillParameters parameters = skillParameterResolver == null
                ? ResolvedSkillParameters.empty()
                : skillParameterResolver.resolve(player, definition, triggerId, invocation);
        boolean castSuccess = mythicCastService.cast(player, mythicSkillId, invocation, parameters);
        if (!castSuccess) {
            return CastAttemptResult.fail(FailureReason.MYTHIC_CAST_FAILED, "cast.mythic_failed");
        }

        // 10. On success: consume resources, record timing
        consumeResources(player, profile, definition);
        AppConfig config = configSupplier.get();
        long forcedDelayTicks = config != null ? config.castTiming().forcedGlobalCastDelayTicks() : 0L;
        timing.recordCast(definition.id(), definition.cooldownTicks(),
                definition.globalCooldownTicks(), forcedDelayTicks);
        profile.markDirty();

        return CastAttemptResult.ok();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private SkillSlotBinding findBindingByTrigger(PlayerSkillProfile profile, String triggerId) {
        SkillSlotBinding indexed = profile.findBindingByTrigger(triggerId);
        if (indexed != null) return indexed;
        // Fallback to linear scan for safety
        for (SkillSlotBinding binding : profile.bindings()) {
            if (!binding.isEmpty() && triggerId.equals(binding.triggerId())) {
                return binding;
            }
        }
        return null;
    }

    private CastAttemptResult checkResourceCosts(Player player, PlayerSkillProfile profile,
            SkillDefinition definition) {
        for (SkillResourceCost cost : definition.resourceCosts()) {
            boolean sufficient = switch (cost.type()) {
                case EA_RESOURCE -> checkEaResource(player, cost);
                case ATTRIBUTE_CHECK -> checkAttribute(player, cost);
                case LOCAL_RESOURCE -> checkLocalResource(profile, cost);
            };
            if (!sufficient) {
                String message = cost.failureMessage() != null && !cost.failureMessage().isBlank()
                        ? cost.failureMessage()
                        : "cast.resource_insufficient";
                return CastAttemptResult.fail(FailureReason.RESOURCE_INSUFFICIENT, message);
            }
        }
        return null;
    }

    private boolean checkEaResource(Player player, SkillResourceCost cost) {
        if (eaBridge == null || !eaBridge.isAvailable()) {
            return true;
        }
        double current = eaBridge.readResourceCurrent(player, cost.targetId());
        return current >= cost.amount();
    }

    private boolean checkAttribute(Player player, SkillResourceCost cost) {
        if (eaBridge == null || !eaBridge.isAvailable()) {
            return true;
        }
        double value = eaBridge.readAttributeValue(player, cost.targetId());
        return value >= cost.amount();
    }

    private boolean checkLocalResource(PlayerSkillProfile profile, SkillResourceCost cost) {
        PlayerLocalResourceState state = profile.localResources().get(cost.targetId());
        if (state == null) {
            return false;
        }
        return state.currentValue() >= cost.amount();
    }

    private void consumeResources(Player player, PlayerSkillProfile profile, SkillDefinition definition) {
        for (SkillResourceCost cost : definition.resourceCosts()) {
            if (cost.operation() != CostOperation.CONSUME) {
                continue;
            }
            switch (cost.type()) {
                case EA_RESOURCE -> {
                    if (eaBridge != null && eaBridge.isAvailable()) {
                        eaBridge.consumeResource(player, cost.targetId(), cost.amount());
                    }
                }
                case LOCAL_RESOURCE -> {
                    PlayerLocalResourceState state = profile.localResources().get(cost.targetId());
                    if (state != null) {
                        state.setCurrentValue(Math.max(0D, state.currentValue() - cost.amount()));
                    }
                }
                case ATTRIBUTE_CHECK -> {
                    // Attribute checks are read-only, no consumption
                }
            }
        }
    }
}
