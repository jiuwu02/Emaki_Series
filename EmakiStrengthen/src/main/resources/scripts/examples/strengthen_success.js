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
  // ctx 可读字段（节选）：
  //   playerUuid / playerName 玩家
  //   recipeId 强化配方，currentStar / targetStar 当前与目标星级
  //   originalSuccessRate / successRate 原始与当前成功率
  //   targetItem 被强化物品摘要 { type, amount, displayName, lore?, customModelData? }
  //   requiredMaterials / optionalMaterials 材料列表，每项含 item / requiredAmount / availableAmount / consumedAmount / optional / protection / temperBoost
  // 返回值可用字段：
  //   successRate(绝对) / successBonus(加) / successMultiplier(乘)
  //   failureProtected / downgradeProtected 失败或降级保护
  //   extraCosts 额外消耗清单（只读暴露，供展示/审计）
  //   message 提示文本
  if (ctx.targetItem && ctx.targetItem.type === "diamond_sword" && ctx.targetStar >= 5) {
    return {
      successBonus: 2.5,
      failureProtected: true,
      extraCosts: [{ item: "minecraft:diamond", amount: 1 }],
      message: "示例：钻石剑 5 星以上额外加成并提供失败保护"
    };
  }
  if (ctx.playerName && ctx.targetStar >= 5) {
    return {
      successBonus: 2.5,
      message: "示例：5 星以上额外增加 2.5% 成功率"
    };
  }
  return {};
}

function onStrengthenResult(event) {
  // event 可读字段（节选）：hookId / playerUuid / playerName
  //   success 是否成功，resultingStar / resultingTemper 结果星级与裂纹，recipeId 配方
  //   maxLevel 结算后星级是否已达配方最大星级（boolean，仅在含预览信息时附加）
  // 返回值可含 actions: [...]，由插件用 CoreLib ActionExecutor 执行；
  //   可用占位符：%success% %strengthen_star% %strengthen_temper% %strengthen_recipe_id%
  emaki.logger.info("Strengthen result hook: recipe=" + event.recipeId + ", success=" + event.success + ", star=" + event.resultingStar);
  if (event.success && event.maxLevel) {
    return {
      actions: [
        "playsound sound=minecraft:ui.toast.challenge_complete volume=1 pitch=1",
        "sendmessage text=\"<gold>已强化至满级 %strengthen_star% 星！</gold>\""
      ]
    };
  }
  if (event.success && event.resultingStar >= 5) {
    return {
      actions: [
        "playsound sound=minecraft:entity.player.levelup volume=1 pitch=1",
        "sendmessage text=\"<gold>强化到 %strengthen_star% 星！</gold>\""
      ]
    };
  }
  return {};
}
