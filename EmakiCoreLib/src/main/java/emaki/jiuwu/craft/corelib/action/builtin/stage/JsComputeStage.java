package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.script.ScriptResult;
import emaki.jiuwu.craft.corelib.script.GraalJsEngine;
import emaki.jiuwu.craft.corelib.script.exports.BukkitPlayerExport;
import emaki.jiuwu.craft.corelib.script.exports.EmakiContextExport;

/**
 * 执行纯计算 JavaScript 脚本，不操作 Bukkit 状态。
 *
 * <p>该 stage 在 {@link CoreActionExecutionDomain#ASYNC_COMPUTE} 域执行，
 * 意味着脚本不应访问任何 Bukkit API（即使通过 bindings 传入的对象也应只读取不修改状态）。</p>
 *
 * <p>典型用途：
 * <ul>
 *   <li>复杂数学计算</li>
 *   <li>条件判断</li>
 *   <li>数据转换</li>
 * </ul>
 *
 * <p>脚本返回值会作为 {@code script_result} 变量供后续 stage 使用。</p>
 *
 * <p>Domain {@code ASYNC_COMPUTE}: 纯计算，不访问 Bukkit 状态。</p>
 */
public final class JsComputeStage extends BaseStage {

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    public JsComputeStage() {
        super("js_compute", "script", "执行纯计算 JavaScript 脚本（不操作 Bukkit 状态）",
                CoreTargetRequirement.OPTIONAL, CoreActionExecutionDomain.ASYNC_COMPUTE,
                CoreStageParameter.required("code", CoreStageParameterType.STRING, "JavaScript 代码"),
                CoreStageParameter.optional("timeout", CoreStageParameterType.INTEGER,
                        String.valueOf(DEFAULT_TIMEOUT_MS), "超时时间（毫秒）"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
                                               @NotNull CoreResolvedArguments arguments) {
        String code = arguments.getString("code", "");
        if (code.isBlank()) {
            return CoreActionOutcome.skipped("action.script.eval.empty_code");
        }

        int timeoutMs = arguments.getInt("timeout", DEFAULT_TIMEOUT_MS);
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("context", new EmakiContextExport(context));
        
        // 如果当前目标是玩家，也注入 player 绑定（只读用途）
        if (context.currentTarget().entityOrNull() instanceof Player player) {
            bindings.put("player", new BukkitPlayerExport(player));
        }

        ScriptResult result = GraalJsEngine.getInstance().eval(code, bindings, timeoutMs);

        if (result.isTimeout()) {
            return CoreActionOutcome.skipped("action.script.eval.timeout");
        }
        if (result.isInterrupted()) {
            return CoreActionOutcome.skipped("action.script.eval.interrupted");
        }
        if (!result.isSuccess()) {
            Throwable error = result.getError();
            String message = error != null ? error.getMessage() : "unknown";
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.script.eval.error",
                    Map.of("error_message", message));
        }

        Object value = result.getValue();
        return CoreActionOutcome.success(Map.of("script_result", value != null ? value : "null"));
    }
}
