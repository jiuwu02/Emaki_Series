function main(ctx) {
  const recipeId = emaki.context.placeholder("forge_recipe_id");
  const quality = emaki.context.placeholder("forge_quality");
  emaki.logger.info("Forge success script: recipe=" + recipeId + ", quality=" + quality);
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 锻造脚本触发，配方: " + recipeId + " 品质: " + quality);
  }
  return true;
}
