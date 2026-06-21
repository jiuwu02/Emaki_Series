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
