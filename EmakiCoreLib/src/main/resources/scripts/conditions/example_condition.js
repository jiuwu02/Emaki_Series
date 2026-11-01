// ============================================================================
// EmakiCoreLib 示例目标条件 (scripts/conditions/example_condition.js)
// ============================================================================
// target_conditions.register({...}) 把一个 JS 谓词注册为目标选择器条件类型。
// 注册成功后，可以在 action.selectors 条件组里按类型引用：
//     - type: script_demo_creative
// 字段说明：
//   id          条件 id（不得与内置条件或已注册条件重名）
//   description 可选描述
//   test(ctx)   逐目标调用；ctx 提供：
//                 ctx.target     目标只读导出（isPlayer()/getGameMode()/
//                                getEntityType()/getLocation()/isValid() 等）
//                 ctx.context    只读管线上下文（getOrigin()/getPhase()/getVariable() 等）
//                 ctx.arguments  条件节点上的字段（占位符已替换）
//               返回值按 JS 真值转布尔；异常或超时按「不成立」处理。
// ============================================================================

target_conditions.register({
    id: 'script_demo_creative',
    description: '目标是处于创造模式的玩家',
    test: function (ctx) {
        return ctx.target.isPlayer() && ctx.target.getGameMode() === 'CREATIVE';
    }
});
