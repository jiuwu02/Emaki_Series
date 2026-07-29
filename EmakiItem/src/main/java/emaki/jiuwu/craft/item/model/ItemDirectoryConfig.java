package emaki.jiuwu.craft.item.model;

/**
 * 物品与套装数据目录的扫描规则。
 *
 * <p>{@code maxDepth} 表示从数据目录根开始允许的最大层级：{@code 1} 只加载根目录下的
 * YAML 文件，{@code 2} 额外允许一层子目录，依此类推。超过上限的子目录会被跳过。
 */
public record ItemDirectoryConfig(int maxDepth) {

    public static final int DEFAULT_MAX_DEPTH = 2;

    public ItemDirectoryConfig {
        maxDepth = Math.max(1, maxDepth);
    }

    public static ItemDirectoryConfig defaults() {
        return new ItemDirectoryConfig(DEFAULT_MAX_DEPTH);
    }
}
