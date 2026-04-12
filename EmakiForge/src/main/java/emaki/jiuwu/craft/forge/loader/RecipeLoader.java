package emaki.jiuwu.craft.forge.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionSyntaxException;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.action.ParsedActionLine;
import emaki.jiuwu.craft.corelib.action.builtin.UseTemplateAction;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class RecipeLoader extends YamlDirectoryLoader<Recipe> {

    private final EmakiForgePlugin forgePlugin;
    private final Supplier<ActionRegistry> actionRegistrySupplier;
    private final Supplier<ActionTemplateRegistry> actionTemplateRegistrySupplier;

    public RecipeLoader(EmakiForgePlugin plugin,
            Supplier<ActionRegistry> actionRegistrySupplier,
            Supplier<ActionTemplateRegistry> actionTemplateRegistrySupplier) {
        super(plugin);
        this.forgePlugin = plugin;
        this.actionRegistrySupplier = actionRegistrySupplier;
        this.actionTemplateRegistrySupplier = actionTemplateRegistrySupplier;
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
    protected Recipe parse(File file, YamlSection configuration) {
        Recipe recipe = Recipe.fromConfig(configuration);
        return validateActions(file, recipe) ? recipe : null;
    }

    public Recipe parseDocument(File file, YamlSection configuration) {
        return parse(file, configuration);
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
        var actionRegistry = actionRegistrySupplier == null ? null : actionRegistrySupplier.get();
        var actionTemplateRegistry = actionTemplateRegistrySupplier == null
                ? null
                : actionTemplateRegistrySupplier.get();
        if (actionRegistry == null || actionTemplateRegistry == null) {
            forgePlugin.messageService().warning("loader.recipe_action_validation_skipped", Map.of(
                    "file", file.getName()
            ));
            return true;
        }
        ActionLineParser parser = new ActionLineParser();
        return validatePhase(file, recipe, "pre", recipe.action().pre(), parser, actionRegistry, actionTemplateRegistry)
                && validatePhase(file, recipe, "result", recipe.result() == null ? List.of() : recipe.result().action(), parser, actionRegistry, actionTemplateRegistry)
                && validatePhase(file, recipe, "success", recipe.action().success(), parser, actionRegistry, actionTemplateRegistry)
                && validatePhase(file, recipe, "failure", recipe.action().failure(), parser, actionRegistry, actionTemplateRegistry);
    }

    private boolean validatePhase(File file,
            Recipe recipe,
            String phase,
            List<String> lines,
            ActionLineParser parser,
            ActionRegistry actionRegistry,
            ActionTemplateRegistry actionTemplateRegistry) {
        if (lines == null || lines.isEmpty()) {
            return true;
        }
        for (int index = 0; index < lines.size(); index++) {
            ParsedActionLine parsed;
            try {
                parsed = parser.parse(index + 1, lines.get(index));
            } catch (ActionSyntaxException exception) {
                forgePlugin.messageService().warning("loader.recipe_invalid_action_syntax", Map.of(
                        "recipe", recipe.id(),
                        "file", file.getName(),
                        "phase", phase,
                        "line", index + 1,
                        "error", String.valueOf(exception.getMessage())
                ));
                return false;
            }
            if (parsed == null) {
                continue;
            }
            Action action = actionRegistry.get(parsed.actionId());
            if (action == null) {
                String suggestion = "";
                String normalizedActionId = parsed.actionId().replace("_", "");
                if (!normalizedActionId.equals(parsed.actionId()) && actionRegistry.get(normalizedActionId) != null) {
                    suggestion = " 请改用标准操作名 '" + normalizedActionId + "'.";
                }
                forgePlugin.messageService().warning("loader.recipe_unknown_action", Map.of(
                        "action", parsed.actionId(),
                        "recipe", recipe.id(),
                        "file", file.getName(),
                        "phase", phase,
                        "line", parsed.lineNumber(),
                        "suggestion", suggestion
                ));
                return false;
            }
            ActionResult validation = action.validate(parsed.arguments());
            if (!validation.success()) {
                forgePlugin.messageService().warning("loader.recipe_invalid_action_arguments", Map.of(
                        "recipe", recipe.id(),
                        "file", file.getName(),
                        "phase", phase,
                        "line", parsed.lineNumber(),
                        "error", Texts.toStringSafe(validation.errorMessage())
                ));
                return false;
            }
            if (UseTemplateAction.ID.equals(parsed.actionId())) {
                String templateName = parsed.arguments().get("name");
                if (Texts.isBlank(templateName) || actionTemplateRegistry.get(templateName) == null) {
                    forgePlugin.messageService().warning("loader.recipe_unknown_action_template", Map.of(
                            "template", templateName,
                            "recipe", recipe.id(),
                            "file", file.getName(),
                            "phase", phase,
                            "line", parsed.lineNumber()
                    ));
                    return false;
                }
            }
        }
        return true;
    }
}
