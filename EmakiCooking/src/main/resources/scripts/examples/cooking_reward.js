function register() {
  const cooking = emaki.module("cooking");

  cooking.registerResultRule({
    id: "example_oven_bonus",
    station: "oven",
    function: "modifyCookingResult"
  });

  cooking.onComplete({
    id: "example_complete_notice",
    function: "onCookingComplete"
  });
}

function modifyCookingResult(ctx) {
  return {
    extraResults: [{
      item_sources: "minecraft:cookie",
      amount: 1
    }],
    message: "示例：烤炉产物额外附赠 1 个曲奇"
  };
}

function onCookingComplete(event) {
  emaki.logger.info("Cooking complete hook: recipe=" + event.recipeId + ", station=" + event.stationType + ", outputs=" + event.outputs.length);
}
