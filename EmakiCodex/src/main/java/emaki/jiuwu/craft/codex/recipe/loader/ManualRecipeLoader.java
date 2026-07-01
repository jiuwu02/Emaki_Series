package emaki.jiuwu.craft.codex.recipe.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.recipe.model.CodexRecipe;
import emaki.jiuwu.craft.codex.recipe.model.ItemRef;
import emaki.jiuwu.craft.codex.recipe.model.RecipeType;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlDirectoryLoader;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * Loads manually declared recipes from {@code recipes/*.yml}. This is a fallback for
 * the rare recipes that are not registered on the vanilla system, or for servers that
 * want to override display. Items are declared with corelib source shorthands.
 *
 * <p>Normal CraftEngine/ItemsAdder recipes are auto-collected from the vanilla system
 * and do not need to be declared here.
 */
public final class ManualRecipeLoader extends YamlDirectoryLoader<CodexRecipe> {

    public ManualRecipeLoader(JavaPlugin plugin) {
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
    protected CodexRecipe parse(File file, YamlSection configuration) {
        if (configuration == null) {
            return null;
        }
        String recipeId = configuration.getString("id", "");
        if (Texts.isBlank(recipeId)) {
            return null;
        }
        RecipeType type = parseType(configuration.getString("type", "other"));
        List<ItemRef> ingredients = parseItemList(configuration.getList("ingredients"));
        ItemRef result = parseItem(configuration.get("result"));
        Map<String, Object> metadata = new LinkedHashMap<>();
        YamlSection metaSection = configuration.getSection("metadata");
        if (metaSection != null) {
            for (String key : metaSection.getKeys(false)) {
                metadata.put(key, metaSection.get(key));
            }
        }
        return new CodexRecipe(recipeId.trim(), type, CodexRecipe.namespaceOf(recipeId.trim()),
                ingredients, result == null ? ItemRef.empty() : result, metadata);
    }

    @Override
    protected String idOf(CodexRecipe value) {
        return value == null ? null : value.recipeId();
    }

    private RecipeType parseType(String raw) {
        if (Texts.isBlank(raw)) {
            return RecipeType.OTHER;
        }
        try {
            return RecipeType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RecipeType.OTHER;
        }
    }

    private List<ItemRef> parseItemList(List<?> raw) {
        List<ItemRef> refs = new ArrayList<>();
        if (raw == null) {
            return refs;
        }
        for (Object element : raw) {
            ItemRef ref = parseItem(element);
            if (ref != null && !ref.isEmpty()) {
                refs.add(ref);
            }
        }
        return refs;
    }

    private ItemRef parseItem(Object raw) {
        if (raw == null) {
            return ItemRef.empty();
        }
        if (raw instanceof Map<?, ?> map) {
            Object shorthand = map.get("item");
            Object amount = map.get("amount");
            int count = amount instanceof Number number ? number.intValue() : 1;
            String value = shorthand == null ? "" : shorthand.toString();
            return new ItemRef(value, count, isCustom(value));
        }
        String value = raw.toString();
        return new ItemRef(value, 1, isCustom(value));
    }

    private boolean isCustom(String shorthand) {
        return Texts.isNotBlank(shorthand) && !shorthand.toLowerCase(Locale.ROOT).startsWith("minecraft-");
    }
}
