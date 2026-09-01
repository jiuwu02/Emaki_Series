package emaki.jiuwu.craft.corelib.api.action.descriptor;

import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionKey;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

/**
 * Immutable metadata snapshot for one registered action pipeline stage.
 *
 * @param id globally registered stage id
 * @param kind source, gate, or action role
 * @param ownerName plugin that owns the registration
 * @param category listing and documentation category
 * @param description human-readable description
 * @param version stage implementation version, or an empty string when not declared
 * @param parameters accepted argument declarations
 * @param targetRequirement target-flow requirement
 * @param requiredContext typed context keys read by the stage
 * @param providedContext typed context keys published by the stage
 * @param providedVariables variable names published by the stage
 */
public record CoreActionStageDescriptor(@NotNull String id,
        @NotNull CoreStageKind kind,
        @NotNull String ownerName,
        @NotNull String category,
        @NotNull String description,
        @NotNull String version,
        @NotNull List<CoreStageParameter> parameters,
        @NotNull CoreTargetRequirement targetRequirement,
        @NotNull Set<CoreActionKey<?>> requiredContext,
        @NotNull Set<CoreActionKey<?>> providedContext,
        @NotNull Set<String> providedVariables) {

    public CoreActionStageDescriptor {
        id = id == null ? "" : id;
        kind = kind == null ? CoreStageKind.ACTION : kind;
        ownerName = ownerName == null ? "" : ownerName;
        category = category == null ? "" : category;
        description = description == null ? "" : description;
        version = version == null ? "" : version;
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        targetRequirement = targetRequirement == null ? CoreTargetRequirement.NONE : targetRequirement;
        requiredContext = requiredContext == null ? Set.of() : Set.copyOf(requiredContext);
        providedContext = providedContext == null ? Set.of() : Set.copyOf(providedContext);
        providedVariables = providedVariables == null ? Set.of() : Set.copyOf(providedVariables);
    }
}
