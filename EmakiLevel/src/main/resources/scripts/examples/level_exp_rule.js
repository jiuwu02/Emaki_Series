// 示例文件不会自动启用。
// 启用方式：复制到 plugins/EmakiCoreLib/scripts/extensions/global/level_exp_rule.js 后执行 /corelib script reload。
function register() {
  const level = emaki.module("level");

  level.registerExpRule({
    id: "js_weekend_bonus",
    priority: 10,
    function: "modifyExp"
  });

  level.onLevelUp({
    id: "js_levelup_message",
    function: "onLevelUp"
  });
}

function modifyExp(ctx) {
  const day = new Date().getDay();
  if (day === 0 || day === 6) {
    return {
      multiplier: 2.0,
      message: "weekend bonus"
    };
  }
  return {};
}

function onLevelUp(event) {
  emaki.logger.info("Level up hook: " + event.playerName + " " + event.typeId + " " + event.oldLevel + " -> " + event.newLevel);
  return true;
}
