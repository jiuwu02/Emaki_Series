function register() {
  const forge = emaki.module("forge");

  forge.registerForgeRule({
    id: "example_moon_bonus",
    priority: 10,
    function: "modifyForge"
  });

  forge.onResult({
    id: "example_result_notice",
    function: "onForgeResult"
  });
}

function modifyForge(ctx) {
  // ctx 可读字段（节选）：
  //   playerUuid / playerName，recipeId / recipeName 配方
  //   originalSuccessRate / successRate 原始与当前成功率
  //   targetItem 主输入物品摘要 { type, amount, displayName, lore?, customModelData? }
  //   requiredMaterials / optionalMaterials 输入材料列表，每项为物品摘要并附带 slot
  // 返回值：successRate / successBonus / successMultiplier 成功率修正，cancel 取消锻造，message 提示
  if (ctx.targetItem && ctx.targetItem.type === "netherite_sword") {
    return {
      successBonus: 5,
      message: "示例：合成下界合金剑时成功率额外增加 5%"
    };
  }
  return {
    successBonus: 3,
    message: "示例：锻造成功率额外增加 3%"
  };
}

function onForgeResult(event) {
  // event 可读字段（节选）：hookId / playerUuid / playerName
  //   success 是否成功，quality 品质，multiplier 倍率，recipeId / recipeName 配方
  //   resultItem 产物物品摘要 { type, amount, displayName, lore?, customModelData? }（只读，仅成功时非空）
  // 返回值可含 actions: [...]，由插件用 CoreLib ActionExecutor 执行；
  //   可用占位符：%success% %forge_recipe_id% %forge_quality% %forge_multiplier%
  emaki.logger.info("Forge result hook: recipe=" + event.recipeId + ", success=" + event.success + ", quality=" + event.quality);
  if (event.success) {
    var producedType = event.resultItem && event.resultItem.type ? event.resultItem.type : "unknown";
    return {
      actions: [
        "playsound sound=minecraft:block.anvil.use volume=1 pitch=1",
        "sendmessage text=\"<aqua>锻造完成，产物：" + producedType + "，品质：%forge_quality%</aqua>\""
      ]
    };
  }
  return {};
}
