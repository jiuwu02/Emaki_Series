package emaki.jiuwu.craft.item.api;

/** Stable namespace and partition reserved for EmakiItem custom item state. */
public final class ItemStateSchema {
    public static final String NAMESPACE = "emaki";
    public static final String PARTITION = "item_state";
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String METADATA_PREFIX = "meta.";
    public static final ItemStateKey<Integer> SCHEMA_VERSION = key("meta.schema_version", ItemStateType.INTEGER);
    public static final ItemStateKey<Long> REVISION = key("meta.revision", ItemStateType.LONG);
    public static final ItemStateKey<String> INSTANCE_ID = key("meta.instance_id", ItemStateType.STRING);

    private ItemStateSchema() {
    }

    public static <T> ItemStateKey<T> key(String key, ItemStateType type) {
        return new ItemStateKey<>(NAMESPACE, PARTITION, key, type);
    }

    public static boolean metadataKey(ItemStateKey<?> key) {
        return key != null && key.key().startsWith(METADATA_PREFIX);
    }
}
