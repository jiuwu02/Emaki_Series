// ============================================================
// EmakiSkills JS 技能定义示例
// ============================================================
// skills.register(payload) 把 JS 对象注册为技能定义，与 skills/*.yml 中的
// YAML 技能同权参与解析、槽位装配、施法、升级查询与 catalog；同 id 时覆盖 YAML。
// payload 字段在注册时一次性快照为稳定 Java 值，注册后再修改 JS 对象不影响技能。
// scriptLines / scriptConditions 与 YAML 技能的 script.actions 走同一条动作
// 管线：可使用内置动作段，也可使用 EmakiCoreLib 注册的任意 JS 动作段。
// ============================================================

skills.register({
  id: "script_example_burst",

  // 显示名称，支持 MiniMessage；留空时回退为 id。
  displayName: "<gradient:#FFB75E:#ED8F03>烈焰爆发</gradient>",

  // 技能描述，逐行显示在技能界面。
  description: [
    "<gray>由 JS 脚本注册的示例主动技能</gray>",
    "<gray>施放火焰冲击并灼烧目标</gray>"
  ],

  // GUI 图标材质；小写材质名。
  iconMaterial: "blaze_powder",

  // 触发方式：ACTIVE / PASSIVE；无法识别的值按 ACTIVE 处理。
  activationType: "ACTIVE",

  // 冷却（tick）与释放后所有技能共享的全局冷却（tick）；负值按 0 处理。
  cooldownTicks: 100,
  globalCooldownTicks: 20,

  // 标签（用于筛选与装备上限）与 Lore 别名。
  tags: ["attack"],
  loreAliases: ["烈焰爆发"],

  // GUI 分类与排序；sortOrder 越小越靠前。
  uiCategory: "attack",
  sortOrder: 30,
  showInSlots: true,
  enabled: true,

  // 各阶段的动作行列表；只识别 cast/hit/miss/fail（大小写不敏感），其余键忽略。
  // 语法与 YAML 技能的 script.actions 完全一致：源 | 闸门 | 动作。
  scriptLines: {
    cast: [
      "self | play_sound sound=entity.blaze.shoot volume=1 pitch=1.1",
      "self | spawn_particle particle=flame count=24 extra=0.05",
      "looking_at range=12 width=1.5 | keep"
    ],
    hit: [
      "inherited | damage amount=10",
      "inherited | ignite duration=40t",
      "inherited | spawn_particle particle=flame count=16 extra=0.1",
      'send_message text="<gold>烈焰爆发命中目标！</gold>"'
    ],
    miss: [
      "self | spawn_particle particle=smoke count=8 extra=0.02",
      'send_message text="<gray>烈焰爆发没有命中目标。</gray>"'
    ]
  },

  // 阶段闸门条件，与 YAML 技能的 phase 条件同语法；条件不通过时跳过该阶段。
  scriptConditions: {
    cast: ["%level% >= 1"]
  }
});
