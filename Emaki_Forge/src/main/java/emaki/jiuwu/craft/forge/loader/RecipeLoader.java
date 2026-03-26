package emaki.jiuwu.craft.forge.loader;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.operation.Operation;
import emaki.jiuwu.craft.corelib.operation.OperationLineParser;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.operation.OperationSyntaxException;
import emaki.jiuwu.craft.corelib.operation.ParsedOperationLine;
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
        return validateOperations(file, recipe) ? recipe : null;
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

    private boolean validateOperations(File file, Recipe recipe) {
        if (recipe == null || recipe.operations() == null) {
            return recipe != null;
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        if (coreLib == null || coreLib.operationRegistry() == null || coreLib.operationTemplateRegistry() == null) {
            plugin.getLogger().warning("Skipped operation validation for recipe '" + file.getName() + "' because Emaki_CoreLib is unavailable.");
            return true;
        }
        OperationLineParser parser = new OperationLineParser();
        return validatePhase(file, recipe, "pre", recipe.operations().pre(), parser, coreLib)
            && validatePhase(file, recipe, "success", recipe.operations().success(), parser, coreLib)
            && validatePhase(file, recipe, "failure", recipe.operations().failure(), parser, coreLib);
    }

    private boolean validatePhase(File file,
                                  Recipe recipe,
                                  String phase,
                                  List<String> lines,
                                  OperationLineParser parser,
                                  EmakiCoreLibPlugin coreLib) {
        if (lines == null || lines.isEmpty()) {
            return true;
        }
        for (int index = 0; index < lines.size(); index++) {
            ParsedOperationLine parsed;
            try {
                parsed = parser.parse(index + 1, lines.get(index));
            } catch (OperationSyntaxException exception) {
                plugin.getLogger().warning("Invalid operation syntax in recipe '" + recipe.id()
                    + "' (" + file.getName() + ", phase=" + phase + ", line=" + (index + 1) + "): " + exception.getMessage());
                return false;
            }
            if (parsed == null) {
                continue;
            }
            Operation operation = coreLib.operationRegistry().get(parsed.operationId());
            if (operation == null) {
                plugin.getLogger().warning("Unknown operation '" + parsed.operationId() + "' in recipe '" + recipe.id()
                    + "' (" + file.getName() + ", phase=" + phase + ", line=" + parsed.lineNumber() + ").");
                return false;
            }
            OperationResult validation = operation.validate(parsed.arguments());
            if (!validation.success()) {
                plugin.getLogger().warning("Invalid operation arguments in recipe '" + recipe.id()
                    + "' (" + file.getName() + ", phase=" + phase + ", line=" + parsed.lineNumber() + "): "
                    + Texts.toStringSafe(validation.errorMessage()));
                return false;
            }
            if ("use_template".equals(parsed.operationId())) {
                String templateName = parsed.arguments().get("name");
                if (Texts.isBlank(templateName) || coreLib.operationTemplateRegistry().get(templateName) == null) {
                    plugin.getLogger().warning("Unknown operation template '" + templateName + "' in recipe '" + recipe.id()
                        + "' (" + file.getName() + ", phase=" + phase + ", line=" + parsed.lineNumber() + ").");
                    return false;
                }
            }
        }
        return true;
    }
}
