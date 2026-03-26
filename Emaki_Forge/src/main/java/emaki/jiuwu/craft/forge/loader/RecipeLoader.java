package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Recipe;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class RecipeLoader extends AbstractDirectoryLoader<Recipe> {

    public RecipeLoader(EmakiForgePlugin plugin) {
        super(plugin);
    }

    @Override
    protected String directoryName() {
        return "recipes";
    }

    @Override
    protected String typeName() {
        return "recipe";
    }

    @Override
    protected Recipe parse(File file, YamlConfiguration configuration) {
        Recipe recipe = Recipe.fromConfig(configuration);
        return validateActions(file, recipe) ? recipe : null;
    }

    @Override
    protected String idOf(Recipe value) {
        return value.id();
    }

    public List<Recipe> byPermission(Player player) {
        List<Recipe> result = new ArrayList<>();
        for (Recipe recipe : items.values()) {
            if (!recipe.requiresPermission() || (player != null && player.hasPermission(recipe.permission()))) {
                result.add(recipe);
            }
        }
        return result;
    }

    private boolean validateActions(File file, Recipe recipe) {
        if (recipe == null || recipe.action() == null) {
            return recipe != null;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || coreLib.actionRegistry() == null || coreLib.actionTemplateRegistry() == null) {
            plugin.getLogger().warning("Skipped action validation for recipe '" + file.getName() + "' because Emaki_CoreLib is unavailable.");
            return true;
        }
        ActionLineParser parser = new ActionLineParser();
        return validatePhase(file, recipe, "pre", recipe.action().pre(), parser, coreLib)
            && validatePhase(file, recipe, "result", recipe.result() == null ? List.of() : recipe.result().action(), parser, coreLib)
            && validatePhase(file, recipe, "success", recipe.action().success(), parser, coreLib)
            && validatePhase(file, recipe, "failure", recipe.action().failure(), parser, coreLib);
    }

    private boolean validatePhase(File file,
                                  Recipe recipe,
                                  String phase,
                                  List<String> lines,
                                  ActionLineParser parser,
                                  EmakiCoreLibPlugin coreLib) {
        if (lines == null || lines.isEmpty()) {
            return true;
        }
        for (int index = 0; index < lines.size(); index++) {
            ParsedActionLine parsed;
            try {
                parsed = parser.parse(index + 1, lines.get(index));
            } catch (ActionSyntaxException exception) {
                plugin.getLogger().warning("Invalid action syntax in recipe '" + recipe.id()
                    + "' (" + file.getName() + ", phase=" + phase + ", line=" + (index + 1) + "): " + exception.getMessage());
                return false;
            }
            if (parsed == null) {
                continue;
            }
            Action action = coreLib.actionRegistry().get(parsed.actionId());
            if (action == null) {
                String suggestion = "send_action_bar".equals(parsed.actionId())
                    ? " 请改用标准操作名 'send_actionbar'."
                    : "";
                plugin.getLogger().warning("Unknown action '" + parsed.actionId() + "' in recipe '" + recipe.id()
                    + "' (" + file.getName() + ", phase=" + phase + ", line=" + parsed.lineNumber() + ")." + suggestion);
                return false;
            }
            ActionResult validation = action.validate(parsed.arguments());
            if (!validation.success()) {
                plugin.getLogger().warning("Invalid action arguments in recipe '" + recipe.id()
                    + "' (" + file.getName() + ", phase=" + phase + ", line=" + parsed.lineNumber() + "): "
                    + Texts.toStringSafe(validation.errorMessage()));
                return false;
            }
            if ("use_template".equals(parsed.actionId())) {
                String templateName = parsed.arguments().get("name");
                if (Texts.isBlank(templateName) || coreLib.actionTemplateRegistry().get(templateName) == null) {
                    plugin.getLogger().warning("Unknown action template '" + templateName + "' in recipe '" + recipe.id()
                        + "' (" + file.getName() + ", phase=" + phase + ", line=" + parsed.lineNumber() + ").");
                    return false;
                }
            }
        }
        return true;
    }
}
