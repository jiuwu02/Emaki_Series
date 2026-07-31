package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Casts a MythicMobs skill on the target.
 *
 * <p>MythicMobs is an optional dependency, so the bridge is resolved once on first use and the result cached.
 * {@code NoClassDefFoundError} is caught alongside ordinary exceptions because a missing MythicMobs manifests as a
 * linkage error rather than a thrown exception, and an unavailable optional integration must not take the pipeline
 * down with it.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: the skill is cast by and around one entity.</p>
 */
public final class CastMythicSkillStage extends BaseStage {

    private volatile Object apiHelper;
    private volatile boolean initialized;
    private volatile boolean available;

    public CastMythicSkillStage() {
        super("cast_mythic_skill", "integration", "Casts a MythicMobs skill on the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("skill", CoreStageParameterType.STRING, "MythicMobs skill id"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Entity target = StageSupport.entity(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_entity");
        }
        String skillId = Texts.trim(arguments.getString("skill"));
        if (skillId.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.cast_mythic_skill.skill_required");
        }
        if (!ensureInitialized()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.OWNER_DISABLED,
                    "action.v2.stage.cast_mythic_skill.unavailable");
        }
        try {
            var helper = (io.lumine.mythic.bukkit.BukkitAPIHelper) apiHelper;
            return helper.castSkill(target, skillId)
                    ? CoreActionOutcome.success(Map.of("skill", skillId))
                    : CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                            "action.v2.stage.cast_mythic_skill.cast_failed", Map.of("skill", skillId));
        } catch (Exception exception) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.v2.stage.cast_mythic_skill.cast_error",
                    Map.of("skill", skillId, "error", Texts.toStringSafe(exception.getMessage())));
        }
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
                apiHelper = io.lumine.mythic.bukkit.MythicBukkit.inst().getAPIHelper();
                available = apiHelper != null;
            } catch (NoClassDefFoundError | Exception exception) {
                Bukkit.getLogger().log(Level.FINE,
                        "[EmakiCoreLib] cast_mythic_skill: MythicMobs bridge init failed", exception);
            }
            return available;
        }
    }
}
