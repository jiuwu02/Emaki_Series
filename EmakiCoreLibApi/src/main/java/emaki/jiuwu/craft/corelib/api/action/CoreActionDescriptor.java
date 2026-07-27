package emaki.jiuwu.craft.corelib.api.action;

import java.util.List;

import org.jetbrains.annotations.NotNull;

/**
 * Read-only metadata for a registered CoreLib action.
 */
public record CoreActionDescriptor(
        @NotNull String id,
        @NotNull String ownerKey,
        @NotNull String source,
        @NotNull String category,
        @NotNull String description,
        @NotNull String version,
        @NotNull CoreActionExecutionMode executionMode,
        long timeoutMillis,
        @NotNull List<CoreActionParameter> parameters) {

    public CoreActionDescriptor {
        id = id == null ? "" : id;
        ownerKey = ownerKey == null ? "" : ownerKey;
        source = source == null ? "" : source;
        category = category == null ? "" : category;
        description = description == null ? "" : description;
        version = version == null ? "" : version;
        executionMode = executionMode == null ? CoreActionExecutionMode.SYNC : executionMode;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
