package emaki.jiuwu.craft.item.api;

import org.jetbrains.annotations.NotNull;

/** Immutable metadata shared by all state fields stored on one item. */
public record ItemStateMetadata(int schemaVersion,
        long revision,
        @NotNull String instanceId,
        boolean repaired) {

    public ItemStateMetadata {
        schemaVersion = Math.max(0, schemaVersion);
        revision = Math.max(0L, revision);
        instanceId = instanceId == null ? "" : instanceId;
    }

    /** {@return the default metadata used when an item has no persistent state} */
    public static @NotNull ItemStateMetadata empty() {
        return new ItemStateMetadata(ItemStateSchema.CURRENT_SCHEMA_VERSION, 0L, "", false);
    }

    /** {@return whether the metadata has a supported schema and stable instance identity} */
    public boolean valid() {
        return schemaVersion == ItemStateSchema.CURRENT_SCHEMA_VERSION && !instanceId.isBlank();
    }
}
