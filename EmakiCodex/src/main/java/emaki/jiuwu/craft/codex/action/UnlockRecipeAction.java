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
 * Action {@code codex-unlock-recipe}: unlocks a recipe for the context player and
 * refreshes their recipe sync so it becomes visible immediately. This is the bridge
 * that lets an advancement's {@code on_complete} unlock recipes.
 */
public final class UnlockRecipeAction implements Action {

    private final EmakiCodexPlugin plugin;

    public UnlockRecipeAction(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "codex-unlock-recipe";
    }

    @Override
    public String category() {
        return "codex";
    }

    @Override
    public String description() {
        return "Unlock a recipe for the player so it becomes visible in their recipe viewer.";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of(ActionParameter.required("recipe", ActionParameterType.STRING, "Recipe id (namespaced key)"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "codex-unlock-recipe requires a player context.");
        }
        String recipeId = Texts.toStringSafe(arguments.get("recipe"));
        if (Texts.isBlank(recipeId)) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "codex-unlock-recipe requires a 'recipe' argument.");
        }
        boolean changed = plugin.unlockStore().unlock(player.getUniqueId(), recipeId.trim());
        plugin.recipeSyncGateway().sync(player);
        return ActionResult.ok(Map.of("changed", changed));
    }
}
