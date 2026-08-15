package emaki.jiuwu.craft.skills.apiimpl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.skills.EmakiSkillsPlugin;
import emaki.jiuwu.craft.skills.api.SkillOperations;
import emaki.jiuwu.craft.skills.api.model.SkillCastOutcome;
import emaki.jiuwu.craft.skills.api.model.SkillUpgradeOutcome;
import emaki.jiuwu.craft.skills.model.CastAttemptResult;
import emaki.jiuwu.craft.skills.model.PlayerSkillProfile;
import emaki.jiuwu.craft.skills.model.SkillDefinition;
import emaki.jiuwu.craft.skills.model.SkillSlotBinding;
import emaki.jiuwu.craft.skills.service.CastAttemptService;
import emaki.jiuwu.craft.skills.service.SkillUpgradeService;

public final class DefaultSkillOperations implements SkillOperations {

    private final EmakiSkillsPlugin plugin;

    public DefaultSkillOperations(EmakiSkillsPlugin plugin, DefaultSkillCatalog ignoredCatalog) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull CompletableFuture<EmakiResult<SkillCastOutcome>> cast(
            @Nullable Player player, @Nullable String skillId) {
        if (player == null) {
            return completed(EmakiResult.invalidInput("skills.player.required"));
        }
        if (Texts.isBlank(skillId)) {
            return completed(EmakiResult.invalidInput("skills.skill.id_required"));
        }
        if (!player.isOnline()) {
            return completed(EmakiResult.targetOffline());
        }
        CastAttemptService castService = plugin.castAttemptService();
        if (!plugin.isEnabled() || castService == null || plugin.playerSkillStateService() == null
                || !plugin.contentReady()) {
            return completed(EmakiResult.unavailable());
        }
        CompletableFuture<EmakiResult<SkillCastOutcome>> result = new CompletableFuture<>();
        Runnable resolveAndCast = () -> {
            PlayerSkillProfile profile = plugin.playerSkillStateService().getProfile(player);
            if (profile == null) {
                result.complete(EmakiResult.targetOffline());
                return;
            }
            String normalized = Texts.normalizeId(skillId);
            String triggerId = profile.bindings().stream()
                    .filter(binding -> normalized.equals(Texts.normalizeId(binding.skillId()))
                            && !Texts.isBlank(binding.triggerId()))
                    .map(SkillSlotBinding::triggerId)
                    .findFirst()
                    .orElse(null);
            if (triggerId == null) {
                result.complete(EmakiResult.notFound("skills.cast.skill_not_equipped_or_bound"));
                return;
            }
            completeFromStage(result, castService.attemptCast(player, triggerId));
        };
        if (plugin.scheduling().ownsEntity(player)) {
            resolveAndCast.run();
            return result;
        }
        try {
            plugin.scheduling().runForEntity(plugin, player, resolveAndCast,
                    () -> result.complete(EmakiResult.targetOffline()));
        } catch (RuntimeException | LinkageError exception) {
            result.complete(EmakiResult.internalError("skills.cast.scheduling_failed"));
        }
        return result;
    }

    @Override
    public @NotNull CompletableFuture<EmakiResult<SkillCastOutcome>> castByTrigger(
            @Nullable Player player, @Nullable String triggerId) {
        if (player == null) {
            return completed(EmakiResult.invalidInput("skills.player.required"));
        }
        if (Texts.isBlank(triggerId)) {
            return completed(EmakiResult.invalidInput("skills.trigger.id_required"));
        }
        if (!player.isOnline()) {
            return completed(EmakiResult.targetOffline());
        }
        CastAttemptService castService = plugin.castAttemptService();
        if (!plugin.isEnabled() || castService == null || !plugin.contentReady()) {
            return completed(EmakiResult.unavailable());
        }
        return castService.attemptCast(player, triggerId).thenApply(DefaultSkillOperations::toCastResult);
    }

    @Override
    public @NotNull EmakiResult<Unit> learn(@Nullable Player player, @Nullable String skillId) {
        EmakiResult<Unit> guard = guardPlayer(player);
        if (guard != null) return guard;
        SkillDefinition definition = definition(skillId);
        if (definition == null) return invalidOrMissing(skillId);
        return plugin.manualSkillSourceService().learn(player, definition.id())
                ? EmakiResult.ok() : EmakiResult.rejected("skills.learn.rejected");
    }

    @Override
    public @NotNull EmakiResult<Unit> forget(@Nullable Player player, @Nullable String skillId) {
        EmakiResult<Unit> guard = guardPlayer(player);
        if (guard != null) return guard;
        SkillDefinition definition = definition(skillId);
        if (definition == null) return invalidOrMissing(skillId);
        return plugin.manualSkillSourceService().forget(player, definition.id())
                ? EmakiResult.ok() : EmakiResult.rejected("skills.forget.not_learned");
    }

    @Override
    public @NotNull EmakiResult<Integer> forgetAll(@Nullable Player player) {
        EmakiResult<Integer> guard = guardPlayer(player);
        return guard != null ? guard : EmakiResult.success(plugin.manualSkillSourceService().forgetAll(player));
    }

    @Override
    public @NotNull EmakiResult<Unit> equip(@Nullable Player player, int slotIndex, @Nullable String skillId) {
        EmakiResult<Unit> guard = guardPlayer(player);
        if (guard != null) return guard;
        if (slotIndex < 0) return EmakiResult.invalidInput("skills.slot.index_invalid");
        SkillDefinition definition = definition(skillId);
        if (definition == null) return invalidOrMissing(skillId);
        return plugin.playerSkillStateService().equipSkill(player, slotIndex, definition.id())
                ? EmakiResult.ok() : EmakiResult.rejected("skills.equip.rejected");
    }

    @Override
    public @NotNull EmakiResult<Unit> unequip(@Nullable Player player, int slotIndex) {
        EmakiResult<Unit> guard = guardPlayer(player);
        if (guard != null) return guard;
        if (slotIndex < 0) return EmakiResult.invalidInput("skills.slot.index_invalid");
        return plugin.playerSkillStateService().unequipSkill(player, slotIndex)
                ? EmakiResult.ok() : EmakiResult.rejected("skills.unequip.rejected");
    }

    @Override
    public @NotNull EmakiResult<Unit> bindTrigger(@Nullable Player player, int slotIndex,
            @Nullable String triggerId) {
        EmakiResult<Unit> guard = guardPlayer(player);
        if (guard != null) return guard;
        if (slotIndex < 0) return EmakiResult.invalidInput("skills.slot.index_invalid");
        if (Texts.isBlank(triggerId)) return EmakiResult.invalidInput("skills.trigger.id_required");
        return plugin.playerSkillStateService().bindTrigger(player, slotIndex, triggerId)
                ? EmakiResult.ok() : EmakiResult.rejected("skills.trigger.bind_rejected");
    }

    @Override
    public @NotNull EmakiResult<SkillUpgradeOutcome> upgrade(@Nullable Player player,
            @Nullable String skillId) {
        EmakiResult<SkillUpgradeOutcome> guard = guardPlayer(player);
        if (guard != null) return guard;
        SkillDefinition definition = definition(skillId);
        if (definition == null) return invalidOrMissing(skillId);
        SkillUpgradeService.UpgradeResult result = plugin.skillUpgradeService().upgrade(player, definition.id());
        if (result == null) {
            return EmakiResult.internalError("skills.upgrade.failed");
        }
        if (!result.success()) {
            FailureKind kind = "upgrade.cancelled".equals(result.messageKey())
                    ? FailureKind.CANCELLED : FailureKind.REJECTED;
            return EmakiResult.failure(kind,
                    Texts.isBlank(result.messageKey()) ? "skills.upgrade.rejected" : result.messageKey(),
                    result.placeholders());
        }
        SkillUpgradeService.UpgradePreview preview = result.preview();
        int fromLevel = preview == null ? plugin.skillLevelService().currentLevel(player, definition) : preview.currentLevel();
        int maxLevel = preview == null ? plugin.skillLevelService().maxLevel(definition) : preview.maxLevel();
        double successRate = preview == null ? 100D : preview.successRate();
        int toLevel = result.successfulRoll()
                ? Math.min(maxLevel, fromLevel + 1)
                : result.downgraded() ? Math.max(1, fromLevel - 1) : fromLevel;
        return EmakiResult.success(new SkillUpgradeOutcome(
                definition.id(), fromLevel, toLevel, maxLevel, successRate,
                result.successfulRoll(), result.levelChanged(), result.downgraded()));
    }

    @Override
    public @NotNull EmakiResult<Integer> setLevel(@Nullable Player player, @Nullable String skillId, int level) {
        EmakiResult<Integer> guard = guardPlayer(player);
        if (guard != null) return guard;
        if (level < 1) return EmakiResult.invalidInput("skills.level.invalid");
        SkillDefinition definition = definition(skillId);
        if (definition == null) return invalidOrMissing(skillId);
        return EmakiResult.success(plugin.skillLevelService().setLevel(player, definition, level));
    }

    @Override
    public @NotNull EmakiResult<Integer> addLevel(@Nullable Player player, @Nullable String skillId, int delta) {
        EmakiResult<Integer> guard = guardPlayer(player);
        if (guard != null) return guard;
        if (delta == 0) return EmakiResult.invalidInput("skills.level.delta_zero");
        SkillDefinition definition = definition(skillId);
        if (definition == null) return invalidOrMissing(skillId);
        return EmakiResult.success(plugin.skillLevelService().addLevel(player, definition, delta));
    }

    @Override
    public @NotNull EmakiResult<Unit> setCastMode(@Nullable Player player, boolean enabled) {
        EmakiResult<Unit> guard = guardPlayer(player);
        if (guard != null) return guard;
        plugin.castModeService().setCastMode(player, enabled);
        return EmakiResult.ok();
    }

    private <T> EmakiResult<T> guardPlayer(Player player) {
        if (!plugin.isEnabled() || plugin.playerSkillDataStore() == null
                || plugin.playerSkillStateService() == null || plugin.manualSkillSourceService() == null
                || plugin.skillLevelService() == null || plugin.skillUpgradeService() == null
                || plugin.castModeService() == null || !plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        if (player == null) return EmakiResult.invalidInput("skills.player.required");
        if (!player.isOnline()) return EmakiResult.targetOffline();
        return plugin.scheduling().ownsEntity(player)
                ? null : EmakiResult.wrongThread();
    }

    private SkillDefinition definition(String skillId) {
        return Texts.isBlank(skillId) || plugin.skillRegistryService() == null
                ? null : plugin.skillRegistryService().getDefinition(skillId);
    }

    private static <T> EmakiResult<T> invalidOrMissing(String skillId) {
        return Texts.isBlank(skillId)
                ? EmakiResult.invalidInput("skills.skill.id_required")
                : EmakiResult.notFound("skills.skill.not_found");
    }

    private static void completeFromStage(CompletableFuture<EmakiResult<SkillCastOutcome>> target,
            CompletableFuture<CastAttemptResult> stage) {
        if (stage == null) {
            target.complete(EmakiResult.internalError("skills.cast.no_stage"));
            return;
        }
        stage.whenComplete((attempt, throwable) -> target.complete(throwable == null
                ? toCastResult(attempt)
                : EmakiResult.internalError("skills.cast.failed")));
    }

    private static EmakiResult<SkillCastOutcome> toCastResult(CastAttemptResult result) {
        if (result == null) return EmakiResult.internalError("skills.cast.failed");
        if (result.success()) {
            return EmakiResult.success(new SkillCastOutcome(result.skillId(), result.triggerId()));
        }
        FailureKind kind = switch (result.failureReason()) {
            case CANCELLED -> FailureKind.CANCELLED;
            case NO_BINDING, SKILL_NOT_FOUND -> FailureKind.NOT_FOUND;
            default -> FailureKind.REJECTED;
        };
        String reasonKey = Texts.isBlank(result.failureMessage())
                ? "skills.cast.rejected" : result.failureMessage();
        Map<String, Object> placeholders = new LinkedHashMap<>();
        if (result.replacements() != null) {
            result.replacements().forEach(placeholders::put);
        }
        return EmakiResult.failure(kind, reasonKey, placeholders);
    }

    private static <T> CompletableFuture<EmakiResult<T>> completed(EmakiResult<T> result) {
        return CompletableFuture.completedFuture(result);
    }
}
