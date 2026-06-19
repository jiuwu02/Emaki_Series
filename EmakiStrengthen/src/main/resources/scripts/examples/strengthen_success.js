function main(ctx) {
  const strengthen = emaki.module("strengthen");
  const recipeId = emaki.context.placeholder("strengthen_recipe_id");
  const star = emaki.context.placeholder("star");
  const temper = emaki.context.placeholder("temper");
  if (emaki.player.exists()) {
    emaki.player.sendMessage("[EmakiJS] 强化成功脚本触发: " + recipeId + " 星级=" + star + " 淬炼=" + temper + " strengthen=" + strengthen.available());
  }
  return {
    success: true,
    output: {
      recipe_id: String(recipeId || ""),
      star: String(star || ""),
      temper: String(temper || ""),
      strengthen_available: strengthen.available()
    }
  };
}
