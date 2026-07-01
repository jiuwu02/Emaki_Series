package emaki.jiuwu.craft.codex.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Action {@code codex-grant-advancement}: grants an EmakiCodex advancement to the
 * context player by awarding its manual criterion.
 */
public final class GrantAdvancementAction implements Action {

    private final EmakiCodexPlugin plugin;

    public GrantAdvancementAction(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "codex-grant-advancement";
    }

    @Override
    public String category() {
        return "codex";
    }

    @Override
    public String description() {
        return "Grant an EmakiCodex advancement to the player.";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(ActionParameter.required("advancement", ActionParameterType.STRING, "Advancement id (page/node or full key)"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "codex-grant-advancement requires a player context.");
        }
        String advancementId = Texts.toStringSafe(arguments.get("advancement"));
        if (Texts.isBlank(advancementId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "codex-grant-advancement requires an 'advancement' argument.");
        }
        boolean granted = plugin.advancementService().grant(player, advancementId.trim());
        if (!granted) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT,
                    "Advancement '" + advancementId + "' is not registered or already completed.");
        }
        return ActionResult.ok();
    }
}
