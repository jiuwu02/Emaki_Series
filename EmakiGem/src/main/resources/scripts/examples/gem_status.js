function register() {
  const gem = emaki.module("gem");

  gem.registerSocketRule({
    id: "example_socket_bonus",
    priority: 10,
    function: "checkSocket"
  });

  gem.registerSetBonus({
    id: "example_lore_bonus",
    function: "applySetBonus"
  });
}

function checkSocket(ctx) {
  // ctx 可读字段（节选）：
  //   playerUuid / playerName，itemId 目标物品，slotIndex / socketType 槽位
  //   gemId / gemType / gemLevel 待镶嵌宝石
  //   inlaidGems 当前已镶嵌宝石列表（镶嵌前），每项含 slot / gemId / gemType / gemLevel
  //   inlaidGemCount 当前已镶嵌数量
  // 返回值：allowed / cancel 控制是否允许，messageKey / message 拒绝原因，
  //         successRate / successBonus / successMultiplier 成功率修正
  var fireCount = (ctx.inlaidGems || []).filter(function (g) { return g.gemType === "fire"; }).length;
  if (fireCount >= 2 && ctx.gemType === "fire") {
    return {
      cancel: true,
      messageKey: "gem.error.condition_not_met",
      message: "示例：同一物品上火属性宝石最多 2 颗"
    };
  }
  if (ctx.gemLevel >= 3) {
    return {
      successBonus: 5,
      message: "示例：3 级以上宝石镶嵌成功率额外增加 5%"
    };
  }
  return {};
}

function applySetBonus(ctx) {
  if (ctx.gemIds.length >= 3) {
    return {
      loreActions: [{
        op: "append",
        value: "<gold>JavaScript 套装示例：已镶嵌 3 颗以上宝石"
      }]
    };
  }
  return {};
}
