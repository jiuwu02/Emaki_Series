package emaki.jiuwu.craft.forge;

/**
 * 集中声明 EmakiForge 写入物品 PDC 的分区与字段名。
 * <p>
 * 这些键是「独立可读的锻造变量」：与属性桥 payload 的 meta 不同，它们在配方没有任何
 * 属性贡献时同样会被写入，因此 Strengthen 等下游模块可以稳定地从目标物品上下文读取品质
 * 与倍率，而不必依赖属性 payload 是否存在。
 * <p>
 * 所有对 {@code "emakiforge"} 命名空间下 PDC 字段的引用都应从此处取常量，而非在各服务中
 * 重复声明字符串，以防不同步导致的静默失效。
 */
public final class ForgePdcKeys {

    /** PDC 命名空间。 */
    public static final String NAMESPACE = "emakiforge";

    /** 锻造变量所在分区路径。 */
    public static final String FORGE_PARTITION = "forge";

    /** 品质档位标识。当前取品质档位名（{@code QualityTier.name()}）。 */
    public static final String QUALITY_ID = "quality_id";

    /** 品质显示名。当前与 {@link #QUALITY_ID} 同源，均取品质档位名。 */
    public static final String QUALITY_DISPLAY = "quality_display";

    /** 品质倍率，以字符串形式持久化，保留两位小数。 */
    public static final String QUALITY_MULTIPLIER = "quality_multiplier";

    /** 产出该物品的锻造配方 ID。 */
    public static final String FORGE_RECIPE_ID = "forge_recipe_id";

    private ForgePdcKeys() {
    }
}
