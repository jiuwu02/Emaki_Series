package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
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
 * 执行操作玩家/实体的 JavaScript 脚本。
 *
 * <p>该 stage 在 {@link CoreActionExecutionDomain#CONTEXT_ENTITY} 域执行，
 * 保证脚本运行在正确的 Folia 实体所有者线程上。</p>
 *
 * <p>典型用途：
 * <ul>
 *   <li>修改玩家生命值、饱食度、经验</li>
 *   <li>复杂的玩家状态判断</li>
 *   <li>根据多个条件动态调整玩家属性</li>
 * </ul>
 *
 * <p>脚本返回值会作为 {@code script_result} 变量供后续 stage 使用。</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: 操作实体状态，运行在实体所有者线程。</p>
 */
public final class JsEntityStage extends BaseStage {

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    public JsEntityStage() {
        super("js_entity", "script", "执行操作玩家/实体的 JavaScript 脚本",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
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

        Player player = StageSupport.player(context.currentTarget());
        if (player == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }

        int timeoutMs = arguments.getInt("timeout", DEFAULT_TIMEOUT_MS);
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("player", new BukkitPlayerExport(player));
        bindings.put("context", new EmakiContextExport(context));

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
