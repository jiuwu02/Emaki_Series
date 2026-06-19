function main(ctx) {
  const forge = emaki.module("forge");
  const recipeId = emaki.context.placeholder("forge_recipe_id");
  const quality = emaki.context.placeholder("forge_quality");
  emaki.logger.info("Forge success script: recipe=" + recipeId + ", quality=" + quality + ", ready=" + forge.ready());
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 锻造脚本触发，配方: " + recipeId + " 品质: " + quality + " forge=" + forge.available());
  }
  return {
    success: true,
    output: {
      recipe_id: String(recipeId || ""),
      quality: String(quality || ""),
      forge_ready: forge.ready(),
      forge_api: forge.apiVersion()
    }
  };
}
