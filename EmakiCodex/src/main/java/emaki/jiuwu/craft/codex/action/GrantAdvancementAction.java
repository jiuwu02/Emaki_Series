package emaki.jiuwu.craft.codex.action;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;

/** Grants one registered EmakiCodex advancement to an online player. */
public final class GrantAdvancementAction implements Action {

    private final EmakiCodexPlugin plugin;
    private final String id;

    public GrantAdvancementAction(EmakiCodexPlugin plugin) {
        this(plugin, "codex-grant-advancement");
    }

    public GrantAdvancementAction(EmakiCodexPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = Texts.normalizeId(id);
    }

    @Override
    public String id() {
        return id;
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
        return List.of(
                ActionParameter.required("advancement", ActionParameterType.STRING, "Advancement id (page/node or full key)"),
                ActionParameter.optional("target", ActionParameterType.STRING, "", "Target online player name or UUID. Defaults to action context player.")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = targetPlayer(context, arguments == null ? null : arguments.get("target"));
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, id + " requires an online player target.");
        }
        String advancementId = Texts.toStringSafe(arguments == null ? null : arguments.get("advancement"));
        if (Texts.isBlank(advancementId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, id + " requires an 'advancement' argument.");
        }
        boolean granted = plugin.advancementService().grant(player, advancementId.trim());
        if (!granted) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT,
                    "Advancement '" + advancementId + "' is not registered or already completed.");
        }
        return ActionResult.ok(Map.of(
                "advancement", advancementId.trim(),
                "target", player.getUniqueId().toString()
        ));
    }

    private Player targetPlayer(ActionContext context, String targetName) {
        if (Texts.isNotBlank(targetName)) {
            Player byName = Bukkit.getPlayerExact(targetName);
            if (byName != null) {
                return byName;
            }
            try {
                return Bukkit.getPlayer(UUID.fromString(targetName.trim()));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return context == null ? null : context.player();
    }
}
