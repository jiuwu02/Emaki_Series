package emaki.jiuwu.craft.item.model;

public record ItemDirectoryConfig(int maxDepth) {

    public static final int DEFAULT_MAX_DEPTH = 2;

    public ItemDirectoryConfig {
        maxDepth = Math.max(1, maxDepth);
    }

    public static ItemDirectoryConfig defaults() {
        return new ItemDirectoryConfig(DEFAULT_MAX_DEPTH);
    }
}
