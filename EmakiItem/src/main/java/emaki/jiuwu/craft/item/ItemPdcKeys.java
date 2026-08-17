package emaki.jiuwu.craft.item;

import org.bukkit.NamespacedKey;

/**
 * 集中声明 EmakiItem 插件所使用的全部 PDC 键。
 * <p>
 * 所有对 {@code "emakiitem"} 命名空间下 PDC 键的引用都应从此处取常量，
 * 而非在各服务 / 监听器中重复声明，以防字符串不同步导致的静默失效。
 */
public final class ItemPdcKeys {

    /** 标记物品是否处于禁用状态（值为 {@code byte 1} 表示已禁用）。 */
    public static final NamespacedKey DISABLED = new NamespacedKey("emakiitem", "disabled");

    private ItemPdcKeys() {}
}
