package emaki.jiuwu.craft.corelib.api.action.descriptor;

import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.pipeline.compile.TriggerContract;

/**
 * Immutable metadata snapshot for one registered action trigger.
 *
 * @param id namespaced trigger id
 * @param ownerName plugin that owns the registration
 * @param description human-readable description
 * @param contract phase contract declared by the trigger
 */
public record CoreActionTriggerDescriptor(@NotNull String id,
        @NotNull String ownerName,
        @NotNull String description,
        @NotNull TriggerContract contract) {

    public CoreActionTriggerDescriptor {
        id = id == null ? "" : id;
        ownerName = ownerName == null ? "" : ownerName;
        description = description == null ? "" : description;
        contract = contract == null ? TriggerContract.permissive(id) : contract;
    }
}
