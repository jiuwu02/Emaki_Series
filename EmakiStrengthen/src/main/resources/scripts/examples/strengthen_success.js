function register() {
  const strengthen = emaki.module("strengthen");

  strengthen.registerChanceRule({
    id: "example_vip_bonus",
    priority: 10,
    function: "modifyChance"
  });

  strengthen.onResult({
    id: "example_result_notice",
    function: "onStrengthenResult"
  });
}

function modifyChance(ctx) {
  if (ctx.playerName && ctx.targetStar >= 5) {
    return {
      successBonus: 2.5,
      message: "示例：5 星以上额外增加 2.5% 成功率"
    };
  }
  return {};
}

function onStrengthenResult(event) {
  emaki.logger.info("Strengthen result hook: recipe=" + event.recipeId + ", success=" + event.success + ", star=" + event.resultingStar);
}
