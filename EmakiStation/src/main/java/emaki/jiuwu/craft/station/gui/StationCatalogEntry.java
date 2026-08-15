package emaki.jiuwu.craft.station.gui;

import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

public record StationCatalogEntry(RecipeDefinition recipe, boolean unlocked) {
}
