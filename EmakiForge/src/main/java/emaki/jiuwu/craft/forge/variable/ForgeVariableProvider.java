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

public final class ForgeVariableProvider {

    private static final PdcService PDC_SERVICE = new PdcService(
            ForgePdcKeys.NAMESPACE,
            "pdc",
            null
    );

    public static VariableContext.Builder enhance(@NotNull VariableContext.Builder builder,
                                                    @Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return builder;
        }

        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        NamespacedKey nsKey = new NamespacedKey(ForgePdcKeys.NAMESPACE, ForgePdcKeys.FORGE_PARTITION);

        builder.withPdcString(partition.key(ForgePdcKeys.QUALITY_ID));
        builder.withPdcString(partition.key(ForgePdcKeys.QUALITY_DISPLAY));
        builder.withPdcString(partition.key(ForgePdcKeys.QUALITY_MULTIPLIER));
        builder.withPdcString(partition.key(ForgePdcKeys.FORGE_RECIPE_ID));

        String qualityId = readMigrating(item, partition, ForgePdcKeys.QUALITY_ID);
        if (Texts.isNotBlank(qualityId)) {
            builder.with("forge_quality_id", qualityId);
        }

        String qualityDisplay = readMigrating(item, partition, ForgePdcKeys.QUALITY_DISPLAY);
        if (Texts.isNotBlank(qualityDisplay)) {
            builder.with("forge_quality_display", qualityDisplay);
        }

        String multiplierStr = readMigrating(item, partition, ForgePdcKeys.QUALITY_MULTIPLIER);
        if (Texts.isNotBlank(multiplierStr)) {
            try {
                double multiplier = Double.parseDouble(multiplierStr);
                builder.with("forge_quality_multiplier", multiplier);
            } catch (NumberFormatException ignored) {
            }
        }

        String recipeId = readMigrating(item, partition, ForgePdcKeys.FORGE_RECIPE_ID);
        if (Texts.isNotBlank(recipeId)) {
            builder.with("forge_recipe_id", recipeId);
        }

        return builder;
    }

    public static double getQualityMultiplier(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 1.0;
        }
        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        String multiplierStr = readMigrating(item, partition, ForgePdcKeys.QUALITY_MULTIPLIER);
        if (Texts.isBlank(multiplierStr)) {
            return 1.0;
        }
        try {
            return Double.parseDouble(multiplierStr);
        } catch (NumberFormatException _) {
            return 1.0;
        }
    }

    public static @Nullable String getQualityId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        return readMigrating(item, partition, ForgePdcKeys.QUALITY_ID);
    }

    public static @Nullable String getRecipeId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        return readMigrating(item, partition, ForgePdcKeys.FORGE_RECIPE_ID);
    }

    /**
     * 读取 Forge 字段，命中历史带点键时就地迁移。
     *
     * <p>历史分区路径与新分区路径同为 {@code "forge"}——点号原先由
     * {@code qualifiedPath} 注入（{@code forge.quality_id}），现在改成
     * {@code forge_quality_id}。
     *
     * <p>本类历史上用手工拼接的 {@code makeKey()} 读、用 {@code partition} 写，
     * 两套代码各自生成键名。现已统一到 {@code partition.key()}，避免读写不一致。
     */
    private static @Nullable String readMigrating(@NotNull ItemStack item,
            @NotNull PdcPartition partition,
            @NotNull String field) {
        return PDC_SERVICE.getMigrating(
                item, partition, ForgePdcKeys.FORGE_PARTITION, field, PersistentDataType.STRING);
    }

    private ForgeVariableProvider() {
    }
}
