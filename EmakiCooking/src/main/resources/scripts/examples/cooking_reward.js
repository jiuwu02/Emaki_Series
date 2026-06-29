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
  // ctx 可读字段（节选）：ruleId / recipeId / recipeName / stationType
  //   playerUuid / playerName / phase / world / x / y / z
  //   inputs 本次消耗的输入材料，每项 {source, amount}（果汁机供应等无输入路径为空列表）
  //   outputs 当前产物列表，actions 当前动作列表，traces 之前规则轨迹
  const inputCount = (ctx.inputs || []).reduce(function (sum, it) { return sum + it.amount; }, 0);
  emaki.logger.info("Cooking result rule: station=" + ctx.stationType
    + ", inputs=" + (ctx.inputs ? ctx.inputs.length : 0) + " (" + inputCount + " items)");
  return {
    extraResults: [{
      item_sources: "minecraft:cookie",
      amount: 1
    }],
    message: "示例：烤炉产物额外附赠 1 个曲奇"
  };
}

function onCookingComplete(event) {
  // event 可读字段（节选）：hookId / playerUuid / playerName
  //   recipeId / recipeName 配方，stationType 工位，outputs 产物列表
  // 返回值可含 actions: [...]，由插件用 CoreLib ActionExecutor 执行（玩家在线时）；
  //   可用占位符：%cooking_recipe_id% %cooking_station_type%
  emaki.logger.info("Cooking complete hook: recipe=" + event.recipeId + ", station=" + event.stationType + ", outputs=" + event.outputs.length);
  return {
    actions: [
      "sendmessage text=\"<green>烹饪完成：%cooking_recipe_id%</green>\""
    ]
  };
}
