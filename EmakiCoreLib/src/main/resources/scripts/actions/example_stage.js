// ============================================================================
// EmakiCoreLib 示例动作段 (scripts/actions/example_stage.js)
// ----------------------------------------------------------------------------
// actions.register({...}) 把一个 JS 函数注册为可复用的自定义动作段。
// 注册成功后，任意 Emaki 模块的动作行（技能、物品、Level、Forge 等）都可以书写：
//     script_demo amount=2
// 段落 id 必须与内置动作段不同；重复注册会失败并在控制台输出原因。
//
// execute(ctx, args) 的两个参数：
//   ctx  只读上下文导出：ctx.getCaster() / ctx.getTargets() / ctx.getCurrentTarget() /
//        ctx.getOrigin() / ctx.getPhase() / ctx.getVariable(name) / ctx.render(模板) 等
//   args 已解析参数的普通 JS 对象（键为参数名）
// 返回值会写入本次动作结果的 script_result。
// ============================================================================

actions.register({
    id: 'script_demo',
    description: '演示动作段：基于 amount 参数计算一个演示数值并返回',
    category: 'script',
    parameters: [
        {
            name: 'amount',
            type: 'double',
            required: false,
            default: '1',
            description: '参与计算的倍率'
        }
    ],
    timeout: 3000,
    execute: function (ctx, args) {
        var amount = Number(args.amount) || 1;
        var result = amount * 10 + exampleLib.curve(amount);
        logger.info('script_demo 执行：amount=' + amount + ' result=' + result
                + ' phase=' + ctx.getPhase());
        return result;
    }
});
