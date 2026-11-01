// ============================================================================
// EmakiCoreLib 示例表达式函数 (scripts/expressions/example_function.js)
// ============================================================================
// expressions.register({...}) 把一个 JS 函数注册为 exp4j 表达式函数。
// 注册成功后，全部模块的表达式/公式配置点都可以直接书写该函数，例如：
//     formula: "script_demo_curve(%level%)"
// 字段说明：
//   name 函数名（字母/数字/下划线，且不得与内置函数 ceil/floor/round/log10/min/max/pow 重名）
//   args 参数个数（>= 1）
//   fn   回调函数，接收数值参数、返回数值；返回非数值或抛异常时按 NaN 处理
// ============================================================================

expressions.register({
    name: 'script_demo_curve',
    args: 1,
    fn: function (level) {
        var value = Number(level) || 0;
        return exampleLib.curve(value) * 1.5;
    }
});
