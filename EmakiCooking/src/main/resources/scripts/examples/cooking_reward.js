function main(ctx) {
  const recipeId = emaki.context.placeholder("cooking_recipe_id") || emaki.context.placeholder("recipe_id");
  const stationType = emaki.context.placeholder("cooking_station_type") || emaki.context.placeholder("station_type");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 烹饪奖励脚本触发: " + recipeId + " @ " + stationType);
  }
  return true;
}
