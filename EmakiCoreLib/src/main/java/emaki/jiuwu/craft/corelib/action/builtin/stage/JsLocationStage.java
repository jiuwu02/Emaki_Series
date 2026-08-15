package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
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
import emaki.jiuwu.craft.corelib.script.exports.EmakiContextExport;

public final class JsLocationStage extends BaseStage {

    private static final int DEFAULT_TIMEOUT_MS = 5000;

    public JsLocationStage() {
        super("js_location", "script", "执行操作方块/位置的 JavaScript 脚本",
                CoreTargetRequirement.REQUIRED_LOCATION, CoreActionExecutionDomain.LOCATION_REGION,
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

        Location location = context.currentTarget().location();
        if (location == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_location");
        }

        int timeoutMs = arguments.getInt("timeout", DEFAULT_TIMEOUT_MS);
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("location", new LocationExport(location));
        bindings.put("context", new EmakiContextExport(context));

        if (context.currentTarget().entityOrNull() instanceof Player player) {
            bindings.put("player", new PlayerReadOnlyExport(player));
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

    private static final class LocationExport {
        private final Location location;

        LocationExport(Location location) {
            this.location = location;
        }

        @org.graalvm.polyglot.HostAccess.Export
        public @NotNull String getWorld() {
            return location.getWorld() != null ? location.getWorld().getName() : "unknown";
        }

        @org.graalvm.polyglot.HostAccess.Export
        public double getX() {
            return location.getX();
        }

        @org.graalvm.polyglot.HostAccess.Export
        public double getY() {
            return location.getY();
        }

        @org.graalvm.polyglot.HostAccess.Export
        public double getZ() {
            return location.getZ();
        }

        @org.graalvm.polyglot.HostAccess.Export
        public int getBlockX() {
            return location.getBlockX();
        }

        @org.graalvm.polyglot.HostAccess.Export
        public int getBlockY() {
            return location.getBlockY();
        }

        @org.graalvm.polyglot.HostAccess.Export
        public int getBlockZ() {
            return location.getBlockZ();
        }
    }

    private static final class PlayerReadOnlyExport {
        private final Player player;

        PlayerReadOnlyExport(Player player) {
            this.player = player;
        }

        @org.graalvm.polyglot.HostAccess.Export
        public @NotNull String getName() {
            return player.getName();
        }

        @org.graalvm.polyglot.HostAccess.Export
        public @NotNull String getUniqueId() {
            return player.getUniqueId().toString();
        }

        @org.graalvm.polyglot.HostAccess.Export
        public double getHealth() {
            return player.getHealth();
        }

        @org.graalvm.polyglot.HostAccess.Export
        public int getLevel() {
            return player.getLevel();
        }
    }
}
