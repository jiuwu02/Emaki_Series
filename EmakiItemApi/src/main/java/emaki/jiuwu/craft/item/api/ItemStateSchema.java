package emaki.jiuwu.craft.item.api;

/** Stable namespace and partition reserved for EmakiItem custom item state. */
public final class ItemStateSchema {
    public static final String NAMESPACE = "emaki";
    public static final String PARTITION = "item_state";

    private ItemStateSchema() {
    }

    public static <T> ItemStateKey<T> key(String key, ItemStateType type) {
        return new ItemStateKey<>(NAMESPACE, PARTITION, key, type);
    }
}
