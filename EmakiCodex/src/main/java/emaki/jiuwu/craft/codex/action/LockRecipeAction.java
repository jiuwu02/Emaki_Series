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
 * Action {@code codex-lock-recipe}: hides a previously unlocked recipe for the context
 * player and refreshes their recipe sync.
 */
public final class LockRecipeAction implements Action {

    private final EmakiCodexPlugin plugin;

    public LockRecipeAction(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "codex-lock-recipe";
    }

    @Override
    public String category() {
        return "codex";
    }

    @Override
    public String description() {
        return "Lock (hide) a recipe for the player in their recipe viewer.";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(ActionParameter.required("recipe", ActionParameterType.STRING, "Recipe id (namespaced key)"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "codex-lock-recipe requires a player context.");
        }
        String recipeId = Texts.toStringSafe(arguments.get("recipe"));
        if (Texts.isBlank(recipeId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "codex-lock-recipe requires a 'recipe' argument.");
        }
        boolean changed = plugin.unlockStore().lock(player.getUniqueId(), recipeId.trim());
        plugin.recipeSyncGateway().sync(player);
        return ActionResult.ok(Map.of("changed", changed));
    }
}
