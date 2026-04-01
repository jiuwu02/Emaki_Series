package emaki.jiuwu.craft.forge.model;

import java.util.Map;

public record RecipeMatch(Recipe recipe, String errorKey, Map<String, Object> replacements) {

}
