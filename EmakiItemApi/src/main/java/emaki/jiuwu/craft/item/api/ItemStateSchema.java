package emaki.jiuwu.craft.item.api;

/** Stable namespace and partition reserved for EmakiItem custom item state. */
public final class ItemStateSchema {
    public static final String NAMESPACE = "emaki";
    public static final String PARTITION = "item_state";
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /**
     * 内置元字段前缀
     *
     * <p>用 {@code '_'} 而非历史的 {@code '.'}: 带点的键在 Bukkit YAML 里无法表达
     */
    public static final String METADATA_PREFIX = "meta_";
    /** 历史元字段前缀, 仅供 {@link #metadataKey} 兼容判断. */
    public static final String LEGACY_METADATA_PREFIX = "meta.";
    public static final ItemStateKey<Integer> SCHEMA_VERSION = key("meta_schema_version", ItemStateType.INTEGER);
    public static final ItemStateKey<Long> REVISION = key("meta_revision", ItemStateType.LONG);
    public static final ItemStateKey<String> INSTANCE_ID = key("meta_instance_id", ItemStateType.STRING);

    private ItemStateSchema() {
    }

    public static <T> ItemStateKey<T> key(String key, ItemStateType type) {
        return new ItemStateKey<>(NAMESPACE, PARTITION, key, type);
    }

    /**
     * {@return 该键是否为内置元字段}
     *
     * <p>同时容忍新前缀 {@code meta_} 与历史前缀 {@code meta.}: 尚未迁移的物品
     * 仍带 {@code meta.revision} 这类键, 只认新前缀会让它们被误判为服主自定义字段
     * 而进入不同的保留/派生逻辑
     */
    public static boolean metadataKey(ItemStateKey<?> key) {
        if (key == null) {
            return false;
        }
        String name = key.key();
        return name.startsWith(METADATA_PREFIX) || name.startsWith(LEGACY_METADATA_PREFIX);
    }
}
