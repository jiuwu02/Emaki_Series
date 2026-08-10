package emaki.jiuwu.craft.skills.action;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import io.lumine.mythic.bukkit.BukkitAPIHelper;
import io.lumine.mythic.bukkit.MythicBukkit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Casts a MythicMobs skill on the target.
 *
 * <p>Lives in EmakiSkills rather than CoreLib because casting skills is this module's subject matter.
 * It does not go through {@code MythicSkillCastService}: that service accepts a {@code Player}, while the
 * whole point of this stage is that any entity can be the caster, so it keeps its own
 * {@code BukkitAPIHelper} path.</p>
 *
 * <p>MythicMobs is an optional dependency, so the bridge is resolved once on first use and the result cached.
 * {@code NoClassDefFoundError} is caught alongside ordinary exceptions because a missing MythicMobs manifests as a
 * linkage error rather than a thrown exception, and an unavailable optional integration must not take the pipeline
 * down with it.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: the skill is cast by and around one entity.</p>
 */
public final class CastMythicSkillStage implements CoreActionStage {

    private volatile Object apiHelper;
    private volatile boolean initialized;
    private volatile boolean available;

    @Override
    public @NotNull String id() {
        return "cast_mythic_skill";
    }

    @Override
    public @NotNull String description() {
        return "Casts a MythicMobs skill on the target.";
    }

    @Override
    public @NotNull String category() {
        return "integration";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(CoreStageParameter.required("skill", CoreStageParameterType.STRING,
                "MythicMobs skill id"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_entity");
        }
        String skillId = Texts.trim(arguments.getString("skill"));
        if (skillId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.cast_mythic_skill.skill_required");
        }
        if (!ensureInitialized()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "action.stage.cast_mythic_skill.unavailable");
        }
        try {
            var helper = (BukkitAPIHelper) apiHelper;
            return helper.castSkill(target, skillId)
                    ? CoreActionOutcome.success(Map.of("skill", skillId))
                    : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                            "action.stage.cast_mythic_skill.cast_failed", Map.of("skill", skillId));
        } catch (Exception exception) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.cast_mythic_skill.cast_error",
                    Map.of("skill", skillId, "error", Texts.toStringSafe(exception.getMessage())));
        }
    }

    /**
     * Reads the subject's entity.
     *
     * <p>Deliberately not narrowed to a player the way the other stages in this package are: MythicMobs
     * casts from any entity, and that generality is this stage's reason to exist.</p>
     *
     * @param subject the current target
     * @return the entity, or {@code null} when the subject carries none
     */
    private static Entity entity(CoreActionSubject subject) {
        return subject == null ? null : subject.entityOrNull();
    }

    private boolean ensureInitialized() {
        if (initialized) {
            return available;
        }
        synchronized (this) {
            if (initialized) {
                return available;
            }
            initialized = true;
            available = false;
            try {
                if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
                    return false;
                }
                apiHelper = MythicBukkit.inst().getAPIHelper();
                available = apiHelper != null;
            } catch (NoClassDefFoundError | Exception exception) {
                Bukkit.getLogger().log(Level.FINE,
                        "[EmakiSkills] cast_mythic_skill: MythicMobs bridge init failed", exception);
            }
            return available;
        }
    }
}
