/**
 * JavaScript 脚本执行契约，基于 GraalVM Polyglot 实现。
 *
 * <p>该包提供了服主在 YAML 配置中使用 JavaScript 扩展业务逻辑的能力，
 * 用于弥补 DSL 在多步状态机、复杂循环和自定义数据结构上的表达能力不足。</p>
 *
 * <h2>核心契约</h2>
 * <ul>
 *   <li>{@link emaki.jiuwu.craft.corelib.api.script.ScriptEngine} - 脚本引擎接口，隐藏 GraalVM 实现细节</li>
 *   <li>{@link emaki.jiuwu.craft.corelib.api.script.ScriptResult} - 执行结果，封装成功/失败/超时/中断四种状态</li>
 * </ul>
 *
 * <h2>安全模型</h2>
 * <p>脚本执行在严格受控的沙箱中：</p>
 * <ul>
 *   <li>只能访问 {@code bindings} 中显式传入的对象</li>
 *   <li>只能调用标注 {@code @HostAccess.Export} 的方法</li>
 *   <li>{@code Java.type()} 被禁止，无法访问任意 Java 类</li>
 *   <li>死循环会在超时后被强制中断（默认 5 秒）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // YAML 配置
 * script:
 *   actions:
 *     cast:
 *       - "self | js_entity code='player.setHealth(20); return \"治疗成功\";' timeout=3000"
 *       - "send_message text='%script_result%'"
 * }</pre>
 *
 * @since 4.8.0
 */
@org.jetbrains.annotations.ApiStatus.Experimental
package emaki.jiuwu.craft.corelib.api.script;
