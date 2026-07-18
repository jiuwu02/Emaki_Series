// 示例文件不会自动启用。
// 启用方式：复制到 plugins/EmakiCoreLib/scripts/extensions/global/item_runtime_definition.js 后执行 /corelib script reload。
function register() {
  const item = emaki.module("item");

  item.registerDefinition({
    id: "js_event_sword",
    // 注册控制字段：同 ID YAML 已存在时，只有 override=true 才允许覆盖。
    override: false,
    item: {
      source: "minecraft:diamond_sword",
      amount: 1,
      components: {
        "minecraft:custom_name": "<red>JS 活动之剑</red>",
        "minecraft:lore": [
          "<gray>由 JavaScript 运行时注册</gray>",
          "<yellow>攻击 +10</yellow>"
        ],
        "minecraft:enchantment_glint_override": true
      }
    },
    ea_attributes: {
      attack_damage: 10
    },
    skills: ["fireball"],
    skillTriggers: {
      fireball: "right_click"
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
    item: {
      source: "minecraft:nether_star",
      amount: ctx.amount,
      components: {
        "minecraft:custom_name": roll >= 50
          ? "<light_purple>闪耀随机遗物</light_purple>"
          : "<aqua>随机遗物</aqua>",
        "minecraft:lore": [
          "<gray>由 JavaScript Factory 动态生成</gray>",
          "<dark_gray>roll=" + roll + "</dark_gray>"
        ]
      }
    },
    variables: {
      roll: roll
    }
  };
}
