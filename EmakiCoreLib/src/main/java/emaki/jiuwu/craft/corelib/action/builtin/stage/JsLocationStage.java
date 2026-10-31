package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.graalvm.polyglot.HostAccess;
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
            String message = error != null ? error.getMessage() : "未知";
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.script.eval.error",
                    Map.of("error_message", message));
        }

        Object value = result.getValue();
        return CoreActionOutcome.success(Map.of("script_result", value != null ? value : "null"));
    }

    private static final class LocationExport {
        private final String worldName;
        private final double x;
        private final double y;
        private final double z;
        private final int blockX;
        private final int blockY;
        private final int blockZ;

        LocationExport(Location location) {
            this.worldName = location.getWorld() != null ? location.getWorld().getName() : "未知";
            this.x = location.getX();
            this.y = location.getY();
            this.z = location.getZ();
            this.blockX = location.getBlockX();
            this.blockY = location.getBlockY();
            this.blockZ = location.getBlockZ();
        }

        @HostAccess.Export
        public @NotNull String getWorld() {
            return worldName;
        }

        @HostAccess.Export
        public double getX() {
            return x;
        }

        @HostAccess.Export
        public double getY() {
            return y;
        }

        @HostAccess.Export
        public double getZ() {
            return z;
        }

        @HostAccess.Export
        public int getBlockX() {
            return blockX;
        }

        @HostAccess.Export
        public int getBlockY() {
            return blockY;
        }

        @HostAccess.Export
        public int getBlockZ() {
            return blockZ;
        }
    }

    private static final class PlayerReadOnlyExport {
        private final String name;
        private final String uniqueId;
        private final double health;
        private final int level;

        PlayerReadOnlyExport(Player player) {
            this.name = player.getName();
            this.uniqueId = player.getUniqueId().toString();
            this.health = player.getHealth();
            this.level = player.getLevel();
        }

        @HostAccess.Export
        public @NotNull String getName() {
            return name;
        }

        @HostAccess.Export
        public @NotNull String getUniqueId() {
            return uniqueId;
        }

        @HostAccess.Export
        public double getHealth() {
            return health;
        }

        @HostAccess.Export
        public int getLevel() {
            return level;
        }
    }
}
