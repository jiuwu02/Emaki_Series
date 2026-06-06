package emaki.jiuwu.craft.skills.service;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.skills.bridge.EaBridge;
import emaki.jiuwu.craft.skills.bridge.ExternalManaBridge;
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
import emaki.jiuwu.craft.skills.script.SkillScriptCastService;
import emaki.jiuwu.craft.skills.script.SkillScriptMode;
import emaki.jiuwu.craft.skills.trigger.TriggerInvocation;
import emaki.jiuwu.craft.corelib.condition.ConditionEvaluator;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class CastAttemptService {

    private final JavaPlugin plugin;
    private final PlayerSkillStateService stateService;
    private final CastModeService castModeService;
    private final PlayerSkillDataStore dataStore;
    private final MythicSkillCastService mythicCastService;
    private final SkillScriptCastService skillScriptCastService;
    private final SkillParameterResolver skillParameterResolver;
    private final EaBridge eaBridge;
    private final ExternalManaBridge externalManaBridge;
    private final Supplier<Map<String, LocalResourceDefinition>> localResourceDefsSupplier;
    private final Supplier<AppConfig> configSupplier;

    public CastAttemptService(JavaPlugin plugin,
            PlayerSkillStateService stateService,
            CastModeService castModeService,
            PlayerSkillDataStore dataStore,
            MythicSkillCastService mythicCastService,
            SkillScriptCastService skillScriptCastService,
            SkillParameterResolver skillParameterResolver,
            EaBridge eaBridge,
            ExternalManaBridge externalManaBridge,
            Supplier<Map<String, LocalResourceDefinition>> localResourceDefsSupplier,
            Supplier<AppConfig> configSupplier) {
        this.plugin = plugin;
        this.stateService = stateService;
        this.castModeService = castModeService;
        this.dataStore = dataStore;
        this.mythicCastService = mythicCastService;
        this.skillScriptCastService = skillScriptCastService;
        this.skillParameterResolver = skillParameterResolver;
        this.eaBridge = eaBridge;
        this.externalManaBridge = externalManaBridge;
        this.localResourceDefsSupplier = localResourceDefsSupplier;
        this.configSupplier = configSupplier;
    }

    public CastAttemptResult attemptCast(Player player, String triggerId) {
        if (player == null || triggerId == null || triggerId.isBlank()) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.invalid_input");
        }

        if (!castModeService.isCastModeEnabled(player)) {
            return CastAttemptResult.fail(FailureReason.NOT_IN_CAST_MODE, "cast.not_in_cast_mode");
        }

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

    public CastAttemptResult attemptCast(Player player, String triggerId, SkillSlotBinding binding) {
        if (player == null || triggerId == null || triggerId.isBlank()) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.invalid_input");
        }
        if (binding == null || binding.isEmpty()) {
            return CastAttemptResult.fail(FailureReason.NO_BINDING, "cast.no_binding");
        }

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

        SkillDefinition definition = stateService.getDefinition(binding.skillId());
        if (definition == null) {
            return CastAttemptResult.fail(FailureReason.SKILL_NOT_FOUND, "skill.not_found");
        }

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

        PlayerCastTimingState timing = profile.timingState();
        if (timing.isForcedDelayActive()) {
            return CastAttemptResult.fail(FailureReason.FORCED_DELAY_ACTIVE, "cast.forced_delay");
        }

        if (timing.isGlobalCooldownActive()) {
            return CastAttemptResult.fail(FailureReason.GLOBAL_COOLDOWN_ACTIVE, "cast.global_cooldown");
        }

        if (timing.isSkillOnCooldown(definition.id())) {
            return CastAttemptResult.fail(FailureReason.SKILL_COOLDOWN_ACTIVE, "cast.skill_cooldown");
        }

        if (!definition.conditions().emptyGroup()) {
            boolean conditionsPassed = ConditionEvaluator.evaluate(
                    definition.conditions(),
                    text -> resolvePlaceholders(player, text),
                    true
            );
            if (!conditionsPassed) {
                return CastAttemptResult.fail(FailureReason.RESOURCE_INSUFFICIENT, "cast.condition_not_met");
            }
        }

        CastAttemptResult costCheck = checkResourceCosts(player, profile, definition);
        if (costCheck != null) {
            return costCheck;
        }

        ResolvedSkillParameters parameters = skillParameterResolver == null
                ? ResolvedSkillParameters.empty()
                : skillParameterResolver.resolve(player, definition, triggerId, invocation);
        boolean castSuccess = castSkill(player, definition, triggerId, invocation, parameters);
        if (!castSuccess) {
            return CastAttemptResult.fail(FailureReason.MYTHIC_CAST_FAILED, "cast.skill_execute_failed");
        }

        consumeResources(player, profile, definition);
        AppConfig config = configSupplier.get();
        long forcedDelayTicks = config != null ? config.castTiming().forcedGlobalCastDelayTicks() : 0L;
        timing.recordCast(definition.id(), definition.cooldownTicks(),
                definition.globalCooldownTicks(), forcedDelayTicks);
        profile.markDirty();

        return CastAttemptResult.ok();
    }


    private boolean castSkill(Player player,
            SkillDefinition definition,
            String triggerId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        boolean hasScript = definition.script() != null && definition.script().enabled();
        String mythicSkillId = definition.mythicSkill();
        boolean hasMythic = mythicSkillId != null && !mythicSkillId.isBlank();
        SkillScriptMode mode = hasScript ? definition.script().mode() : SkillScriptMode.MYTHIC;
        if (hasScript && mode == SkillScriptMode.NATIVE) {
            return skillScriptCastService != null
                    && skillScriptCastService.cast(player, definition, triggerId, invocation, parameters);
        }
        if (hasScript && mode == SkillScriptMode.HYBRID) {
            boolean nativeOk = skillScriptCastService != null
                    && skillScriptCastService.cast(player, definition, triggerId, invocation, parameters);
            return nativeOk && (!hasMythic || castMythic(player, mythicSkillId, invocation, parameters));
        }
        if (hasMythic) {
            return castMythic(player, mythicSkillId, invocation, parameters);
        }
        return false;
    }

    private boolean castMythic(Player player,
            String mythicSkillId,
            TriggerInvocation invocation,
            ResolvedSkillParameters parameters) {
        return mythicCastService != null
                && mythicCastService.skillExists(mythicSkillId)
                && mythicCastService.cast(player, mythicSkillId, invocation, parameters);
    }

    private SkillSlotBinding findBindingByTrigger(PlayerSkillProfile profile, String triggerId) {
        SkillSlotBinding indexed = profile.findBindingByTrigger(triggerId);
        if (indexed != null) return indexed;
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
                case AURASKILLS_MANA, MYTHICLIB_MANA -> checkExternalMana(player, cost);
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

    private boolean checkExternalMana(Player player, SkillResourceCost cost) {
        if (externalManaBridge == null || !externalManaBridge.isAvailable(cost.type())) {
            return false;
        }
        double current = externalManaBridge.readCurrent(player, cost.type());
        return current >= cost.amount();
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
                }
                case AURASKILLS_MANA, MYTHICLIB_MANA -> {
                    if (externalManaBridge != null && externalManaBridge.isAvailable(cost.type())) {
                        externalManaBridge.consume(player, cost.type(), cost.amount());
                    }
                }
            }
        }
    }

    private String resolvePlaceholders(Player player, String text) {
        if (player == null || Texts.isBlank(text) || !plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return text;
        }
        try {
            return Texts.toStringSafe(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text));
        } catch (Exception | NoClassDefFoundError _) {
            return text;
        }
    }
}
