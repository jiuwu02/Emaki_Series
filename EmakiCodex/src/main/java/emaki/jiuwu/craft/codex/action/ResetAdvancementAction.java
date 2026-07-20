package emaki.jiuwu.craft.codex.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionTarget;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionPlanningContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.Texts;


public final class ResetAdvancementAction implements Action {

    public enum Mode {
        PAGE,
        ALL
    }

    private final EmakiCodexPlugin plugin;
    private final Mode mode;
    private final String id;

    public ResetAdvancementAction(EmakiCodexPlugin plugin, Mode mode) {
        this(plugin, mode, mode == Mode.PAGE ? "codex-reset-page" : "codex-reset-all");
    }

    public ResetAdvancementAction(EmakiCodexPlugin plugin, Mode mode, String id) {
        this.plugin = plugin;
        this.mode = mode;
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
        return mode == Mode.PAGE
                ? "Reset one EmakiCodex advancement page for the player."
                : "Reset all registered EmakiCodex advancements for the player.";
    }

    @Override
    public List<ActionParameter> parameters() {
        if (mode == Mode.PAGE) {
            return List.of(
                    ActionParameter.required("page", ActionParameterType.STRING, "Advancement page id."),
                    ActionParameter.optional("target", ActionParameterType.STRING, "", "Target online player name or UUID. Defaults to action context player.")
            );
        }
        return List.of(ActionParameter.optional("target", ActionParameterType.STRING, "", "Target online player name or UUID. Defaults to action context player."));
    }

    @Override
    public ActionExecutionTarget executionTarget(ActionPlanningContext context) {
        Player target = targetPlayer(
                context == null ? null : context.actionContext(),
                context == null ? null : context.arguments().get("target")
        );
        return target == null
                ? ActionExecutionTarget.failure(ActionResult.failure(
                        ActionErrorType.INVALID_STATE, id() + " requires an online player target."))
                : ActionExecutionTarget.entity(target);
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (plugin.advancementRegistrar() == null || plugin.advancementService() == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiCodex advancement services are not ready.");
        }
        Player player = targetPlayer(context, arguments == null ? null : arguments.get("target"));
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, id() + " requires an online player target.");
        }

        String page = Texts.toStringSafe(arguments == null ? null : arguments.get("page")).trim();
        if (mode == Mode.PAGE && Texts.isBlank(page)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "codex-reset-page requires a 'page' argument.");
        }

        List<AdvancementRegistrar.RegisteredNode> nodes = matchingNodes(page);
        if (nodes.isEmpty()) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT,
                    mode == Mode.PAGE ? "No registered EmakiCodex nodes found for page: " + page : "No EmakiCodex advancement nodes are registered.");
        }

        int revoked = 0;
        for (AdvancementRegistrar.RegisteredNode node : nodes) {
            if (plugin.advancementService().revoke(player, node.key().toString())) {
                revoked++;
            }
        }

        return ActionResult.ok(Map.of(
                "target", player.getUniqueId().toString(),
                "player", player.getName(),
                "mode", mode.name().toLowerCase(java.util.Locale.ROOT),
                "page", page,
                "nodes", nodes.size(),
                "revoked", revoked
        ));
    }

    private List<AdvancementRegistrar.RegisteredNode> matchingNodes(String page) {
        List<AdvancementRegistrar.RegisteredNode> result = new ArrayList<>();
        String normalizedPage = Texts.normalizeId(page);
        for (AdvancementRegistrar.RegisteredNode node : plugin.advancementRegistrar().registeredNodes()) {
            if (node == null || node.page() == null) {
                continue;
            }
            if (mode == Mode.ALL || node.page().pageId().equalsIgnoreCase(normalizedPage)) {
                result.add(node);
            }
        }
        Collections.reverse(result);
        return List.copyOf(result);
    }

    private Player targetPlayer(ActionContext context, String targetName) {
        if (Texts.isNotBlank(targetName)) {
            Player target = Bukkit.getPlayerExact(targetName);
            if (target != null) {
                return target;
            }
            try {
                return Bukkit.getPlayer(java.util.UUID.fromString(targetName.trim()));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return context == null ? null : context.player();
    }
}
