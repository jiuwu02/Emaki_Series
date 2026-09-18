package emaki.jiuwu.craft.corelib.debug;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ActionAuditLogger {

    private static final String MODULE = "action.audit";

    private final DebugLogger debugLogger;

    public ActionAuditLogger(@Nullable DebugLogger debugLogger) {
        this.debugLogger = debugLogger;
    }

    public enum OperationType {
        INCREASE, DECREASE, SET
    }

    public void logSuccess(@NotNull String stageId,
            @Nullable Entity target,
            @NotNull OperationType operation,
            @Nullable Number before,
            @Nullable Number after,
            @Nullable Number amount,
            @NotNull CoreStageContext context) {
        if (debugLogger == null) {
            return;
        }
        UUID targetUuid = target == null ? null : target.getUniqueId();
        debugLogger.log(MODULE, targetUuid, "action.audit.success", Map.of(
                "timestamp", Instant.now().toString(),
                "stage", stageId,
                "operation", operation.name(),
                "target", target == null ? "absent" : target.getName(),
                "target_uuid", targetUuid == null ? "absent" : targetUuid.toString(),
                "before", Texts.toStringSafe(before),
                "after", Texts.toStringSafe(after),
                "amount", Texts.toStringSafe(amount),
                "source", source(context),
                "phase", context.phase()
        ));
    }

    public void logFailure(@NotNull String stageId,
            @Nullable Entity target,
            @NotNull OperationType operation,
            @Nullable Number amount,
            @NotNull String reasonKey,
            @Nullable Map<String, ?> details,
            @NotNull CoreStageContext context) {
        if (debugLogger == null) {
            return;
        }
        UUID targetUuid = target == null ? null : target.getUniqueId();
        debugLogger.log(MODULE, targetUuid, "action.audit.failure", Map.of(
                "timestamp", Instant.now().toString(),
                "stage", stageId,
                "operation", operation.name(),
                "target", target == null ? "absent" : target.getName(),
                "target_uuid", targetUuid == null ? "absent" : targetUuid.toString(),
                "amount", Texts.toStringSafe(amount),
                "source", source(context),
                "phase", context.phase(),
                "reason", reasonKey,
                "details", Texts.toStringSafe(details)
        ));
    }

    private static String source(CoreStageContext context) {
        Plugin plugin = context.sourcePlugin();
        return plugin == null ? "unknown" : plugin.getName();
    }
}
