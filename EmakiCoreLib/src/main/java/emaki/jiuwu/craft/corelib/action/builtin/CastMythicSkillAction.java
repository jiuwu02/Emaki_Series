package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;

/**
 * CoreLib built-in action that directly casts a MythicMobs skill on the player.
 *
 * <p>This action soft-depends on MythicMobs. When MythicMobs is not installed,
 * the action gracefully returns a failure result without throwing exceptions.
 *
 * <p>Script usage: {@code castmythicskill skill="FireballSkill"}
 */
final class CastMythicSkillAction extends BaseAction {

    private volatile Object apiHelper;
    private volatile boolean initialized;
    private volatile boolean available;

    CastMythicSkillAction() {
        super(
                "castmythicskill",
                "skills",
                "Cast a MythicMobs skill directly on the player.",
                ActionParameter.required("skill", ActionParameterType.STRING, "MythicMobs skill ID to cast")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }

        String skillId = stringArg(arguments, "skill");
        if (skillId == null || skillId.isBlank()) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Missing required argument 'skill'.");
        }

        if (!ensureInitialized()) {
            return ActionResult.failure(ActionErrorType.PROVIDER_UNAVAILABLE, "MythicMobs is not available.");
        }

        Player player = context.player();
        try {
            var helper = (io.lumine.mythic.bukkit.BukkitAPIHelper) apiHelper;
            boolean success = helper.castSkill(player, skillId);
            return success
                    ? ActionResult.ok()
                    : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "MythicMobs skill cast failed: " + skillId);
        } catch (Exception exception) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION,
                    "MythicMobs skill cast error: " + exception.getMessage());
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
                        "[EmakiCoreLib] castmythicskill: MythicMobs bridge init failed", exception);
            }
            return available;
        }
    }
}
