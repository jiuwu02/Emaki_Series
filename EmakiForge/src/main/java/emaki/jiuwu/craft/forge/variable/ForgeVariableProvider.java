package emaki.jiuwu.craft.forge.variable;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.pdc.PdcPartition;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.variable.VariableContext;
import emaki.jiuwu.craft.forge.ForgePdcKeys;

/**
 * Forge 变量提供者：为 {@link VariableContext} 提供从锻造物品读取品质、倍率、配方 ID 的集成方法。
 * <p>
 * 使用场景：
 * <ul>
 *   <li>Strengthen 模块在公式计算时需要读取物品的品质倍率</li>
 *   <li>Attribute 模块在快照收集时需要识别物品的锻造来源</li>
 *   <li>其他模块需要在条件判断或公式中引用锻造变量</li>
 * </ul>
 * <p>
 * 提供的变量键（按优先级从高到低）：
 * <ul>
 *   <li>{@code forge_quality_id} - 品质档位标识（String）</li>
 *   <li>{@code forge_quality_display} - 品质显示名（String）</li>
 *   <li>{@code forge_quality_multiplier} - 品质倍率（double）</li>
 *   <li>{@code forge_recipe_id} - 配方 ID（String）</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>{@code
 * ItemStack item = player.getInventory().getItemInMainHand();
 * VariableContext ctx = ForgeVariableProvider.enhance(
 *     VariableContext.builder(player), 
 *     item
 * ).build();
 * 
 * double multiplier = ctx.getDouble("forge_quality_multiplier");
 * String qualityId = ctx.getString("forge_quality_id");
 * }</pre>
 */
public final class ForgeVariableProvider {

    private static final PdcService PDC_SERVICE = new PdcService(
            ForgePdcKeys.NAMESPACE,
            "pdc",
            null
    );

    /**
     * 增强 {@link VariableContext.Builder}，从物品 PDC 读取 Forge 变量。
     * <p>
     * 将锻造变量注册到 Builder 的 PDC 键映射中，使 VariableContext 可通过
     * {@code forge_quality_multiplier} 等键直接读取。
     *
     * @param builder VariableContext Builder
     * @param item    锻造物品（可为 null，此时不注册任何变量）
     * @return 增强后的 Builder（链式调用）
     */
    public static VariableContext.Builder enhance(@NotNull VariableContext.Builder builder,
                                                    @Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return builder;
        }

        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        NamespacedKey nsKey = new NamespacedKey(ForgePdcKeys.NAMESPACE, ForgePdcKeys.FORGE_PARTITION);

        // 注册 PDC 键映射：VariableContext 会自动解析这些键
        builder.withPdcString(makeKey(ForgePdcKeys.QUALITY_ID));
        builder.withPdcString(makeKey(ForgePdcKeys.QUALITY_DISPLAY));
        builder.withPdcString(makeKey(ForgePdcKeys.QUALITY_MULTIPLIER));
        builder.withPdcString(makeKey(ForgePdcKeys.FORGE_RECIPE_ID));

        // 同时作为显式变量注入（优先级更高，避免 PDC 解析延迟）
        String qualityId = PDC_SERVICE.get(item, partition, ForgePdcKeys.QUALITY_ID, PersistentDataType.STRING);
        if (Texts.isNotBlank(qualityId)) {
            builder.with("forge_quality_id", qualityId);
        }

        String qualityDisplay = PDC_SERVICE.get(item, partition, ForgePdcKeys.QUALITY_DISPLAY, PersistentDataType.STRING);
        if (Texts.isNotBlank(qualityDisplay)) {
            builder.with("forge_quality_display", qualityDisplay);
        }

        String multiplierStr = PDC_SERVICE.get(item, partition, ForgePdcKeys.QUALITY_MULTIPLIER, PersistentDataType.STRING);
        if (Texts.isNotBlank(multiplierStr)) {
            try {
                double multiplier = Double.parseDouble(multiplierStr);
                builder.with("forge_quality_multiplier", multiplier);
            } catch (NumberFormatException ignored) {
                // 解析失败时跳过，VariableContext.getDouble 会返回 0.0
            }
        }

        String recipeId = PDC_SERVICE.get(item, partition, ForgePdcKeys.FORGE_RECIPE_ID, PersistentDataType.STRING);
        if (Texts.isNotBlank(recipeId)) {
            builder.with("forge_recipe_id", recipeId);
        }

        return builder;
    }

    /**
     * 直接从物品读取品质倍率（便捷方法）。
     *
     * @param item 锻造物品
     * @return 品质倍率，缺失或解析失败时返回 1.0
     */
    public static double getQualityMultiplier(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 1.0;
        }
        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        String multiplierStr = PDC_SERVICE.get(item, partition, ForgePdcKeys.QUALITY_MULTIPLIER, PersistentDataType.STRING);
        if (Texts.isBlank(multiplierStr)) {
            return 1.0;
        }
        try {
            return Double.parseDouble(multiplierStr);
        } catch (NumberFormatException _) {
            return 1.0;
        }
    }

    /**
     * 直接从物品读取品质 ID（便捷方法）。
     *
     * @param item 锻造物品
     * @return 品质 ID，缺失时返回 null
     */
    public static @Nullable String getQualityId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        return PDC_SERVICE.get(item, partition, ForgePdcKeys.QUALITY_ID, PersistentDataType.STRING);
    }

    /**
     * 直接从物品读取配方 ID（便捷方法）。
     *
     * @param item 锻造物品
     * @return 配方 ID，缺失时返回 null
     */
    public static @Nullable String getRecipeId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        return PDC_SERVICE.get(item, partition, ForgePdcKeys.FORGE_RECIPE_ID, PersistentDataType.STRING);
    }

    private static NamespacedKey makeKey(String key) {
        return new NamespacedKey(ForgePdcKeys.NAMESPACE, ForgePdcKeys.FORGE_PARTITION + "." + key);
    }

    private ForgeVariableProvider() {
    }
}
