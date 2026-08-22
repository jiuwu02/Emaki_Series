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

        builder.withPdcString(makeKey(ForgePdcKeys.QUALITY_ID));
        builder.withPdcString(makeKey(ForgePdcKeys.QUALITY_DISPLAY));
        builder.withPdcString(makeKey(ForgePdcKeys.QUALITY_MULTIPLIER));
        builder.withPdcString(makeKey(ForgePdcKeys.FORGE_RECIPE_ID));

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
            }
        }

        String recipeId = PDC_SERVICE.get(item, partition, ForgePdcKeys.FORGE_RECIPE_ID, PersistentDataType.STRING);
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

    public static @Nullable String getQualityId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        PdcPartition partition = PDC_SERVICE.partition(ForgePdcKeys.FORGE_PARTITION);
        return PDC_SERVICE.get(item, partition, ForgePdcKeys.QUALITY_ID, PersistentDataType.STRING);
    }

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
