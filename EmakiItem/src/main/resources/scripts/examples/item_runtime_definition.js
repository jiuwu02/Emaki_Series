// 示例文件不会自动启用。
// 启用方式：复制到 plugins/EmakiCoreLib/scripts/extensions/global/item_runtime_definition.js 后执行 /corelib script reload。
function register() {
  const item = emaki.module("item");

  item.registerDefinition({
    id: "js_event_sword",
    source: "minecraft:diamond_sword",
    display_name: "<red>JS 活动之剑</red>",
    lore: [
      "<gray>由 JavaScript 运行时注册</gray>",
      "<yellow>攻击 +10</yellow>"
    ],
    ea_attributes: {
      attack_damage: 10
    },
    actions: {
      give: ["sendmessage text=<gold>你获得了 JS 活动之剑！</gold>"]
    }
  });

  item.registerFactory({
    id: "js_random_relic",
    priority: 10,
    function: "createRelic"
  });
}

function createRelic(ctx) {
  if (ctx.id !== "js_random_relic") {
    return null;
  }
  const roll = emaki.random.integer(0, 99);
  return {
    source: "minecraft:nether_star",
    display_name: roll >= 50 ? "<light_purple>闪耀随机遗物</light_purple>" : "<aqua>随机遗物</aqua>",
    lore: [
      "<gray>由 JavaScript Factory 动态生成</gray>",
      "<dark_gray>roll=" + roll + "</dark_gray>"
    ],
    variables: {
      roll: roll
    }
  };
}
