// EmakiCodex 图鉴触发器示例脚本（scripts/triggers/ 仅作组织目录，无隔离作用）。
//
// 通过 triggers 绑定注册触发器：每次图鉴/成就触发事件进入分发时，
// EmakiCodex 会同步调用这里注册的 advancements(ctx) 函数，把返回的
// 图鉴/成就 id 与配置内触发规则命中的 id 合并后授予（重复 id 自动折叠）。
//
// payload 契约：
//   id           必填，非空字符串，EmakiCodex 内唯一，重复注册会报错跳过。
//   priority     可选，整数，默认 100，数值越小越先求值。
//   advancements 必填，可执行函数；参数 ctx 含：
//     ctx.player     触发玩家，org.bukkit.entity.Player 宿主对象，可直接调用其方法。
//     ctx.triggerId  本次游戏事件的规范化 id（字符串）。
//     ctx.variables  事件携带的变量（普通对象，键值与事件传入一致）。
//   返回值：单个图鉴/成就 id（字符串）、id 数组/可迭代，或 null/undefined（按空处理）。
//   函数抛出异常或执行超时（scripts.timeout_ms）时按空集合处理并输出告警，
//   不影响其余触发器与本脚本后续求值。
//
// 真实用法：把返回值替换为你在 advancements/ 页面或 codex/ 分类中已定义的
// 条目 id；ctx.triggerId 会等于游戏事件分发时使用的规范化触发名。
// 下面的 example_event 并非本插件内置事件，因此本示例默认不会被任何玩法
// 事件命中，仅作演示，不会在运行期产生授予或告警。
triggers.register({
  id: "script_example_hunter",
  priority: 100,
  advancements: function (ctx) {
    if (ctx.triggerId !== "example_event") {
      return null;
    }
    var count = ctx.variables.count;
    if (typeof count !== "number" || count < 10) {
      return null;
    }
    return ["codex/example_entry"];
  }
});
