// ============================================================================
// EmakiCoreLib 示例占位符 (scripts/placeholders/example_placeholder.js)
// ============================================================================
// placeholders.register({...}) 把一个 JS 函数包装为占位符解析器，
// 追加到占位符解析链尾部（在 ActionContext 变量解析之后执行）。
// 字段说明：
//   id      诊断标识，出现在日志中
//   resolve(text, ctx)  对「链上前序解析后的文本」做文本变换并返回新文本；
//          ctx 提供 ctx.getPlayer()（可为空的玩家只读导出）、ctx.getPhase()、
//          ctx.isSilent()、ctx.getPlaceholder(key) 等 ActionContext 信息。
// resolve 抛异常或超时时，本次解析按原文本返回并输出含 id 的告警，
// 占位符解析链不会中断。
// ============================================================================

placeholders.register({
    id: 'script_demo_mark',
    resolve: function (text, ctx) {
        var player = ctx.getPlayer() ? ctx.getPlayer().getName() : '未知玩家';
        return text.split('%script_demo_mark%').join(exampleLib.greet(player));
    }
});
