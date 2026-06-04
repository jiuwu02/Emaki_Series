function main(ctx) {
  const recipeId = emaki.context.placeholder("strengthen_recipe_id");
  const star = emaki.context.placeholder("star");
  const temper = emaki.context.placeholder("temper");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 强化成功脚本触发: " + recipeId + " 星级=" + star + " 淬炼=" + temper);
  }
  return true;
}
