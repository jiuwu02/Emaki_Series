package emaki.jiuwu.craft.strengthen.enhancement;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.variable.VariableContext;

/** Central target ItemStack PDC variable and compatibility-alias contract. */
final class EnhancementTargetVariables {

    private static final String FORGE_NAMESPACE = "emakiforge";

    private EnhancementTargetVariables() {
    }

    static void enrich(@NotNull VariableContext.Builder builder, ItemStack target) {
        // Stable defaults make formulas and target-aware matchers deterministic when Forge metadata is absent.
        String material = target == null || target.getType().isAir() ? "" : target.getType().name();
        builder.with("target_item_type", material)
                .with("target.item_type", material)
                .with("item_type", material)
                .with("target_material", material)
                .with("target.material", material)
                .with("forge_quality_id", "")
                .with("forge_quality_display", "")
                .with("forge_quality_multiplier", 1D)
                .with("forge_recipe_id", "")
                .with("forge.quality_id", "")
                .with("forge.quality_display", "")
                .with("forge.quality_multiplier", 1D)
                .with("forge.forge_recipe_id", "")
                .with("quality_id", "")
                .with("quality_display", "")
                .with("quality_multiplier", 1D)
                .with("forge_quality_multiplier_valid", 1)
                .with("target_pdc_read_error_count", 0);
        if (target == null || target.getType().isAir()) {
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        int unreadable = 0;
        for (NamespacedKey key : container.getKeys()) {
            Object value = read(container, key);
            if (value == null) {
                unreadable++;
                continue;
            }
            builder.with("item_pdc_" + key.getNamespace() + "_" + key.getKey(), value);
            addForgeAliases(builder, key, value);
        }
        builder.with("target_pdc_read_error_count", unreadable);
    }

    private static Object read(PersistentDataContainer container, NamespacedKey key) {
        try {
            if (container.has(key, PersistentDataType.STRING)) {
                return container.get(key, PersistentDataType.STRING);
            }
            if (container.has(key, PersistentDataType.DOUBLE)) {
                return container.get(key, PersistentDataType.DOUBLE);
            }
            if (container.has(key, PersistentDataType.FLOAT)) {
                return container.get(key, PersistentDataType.FLOAT);
            }
            if (container.has(key, PersistentDataType.LONG)) {
                return container.get(key, PersistentDataType.LONG);
            }
            if (container.has(key, PersistentDataType.INTEGER)) {
                return container.get(key, PersistentDataType.INTEGER);
            }
            if (container.has(key, PersistentDataType.SHORT)) {
                return container.get(key, PersistentDataType.SHORT);
            }
            if (container.has(key, PersistentDataType.BYTE)) {
                return container.get(key, PersistentDataType.BYTE);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Unsupported or corrupt PDC types remain absent and retain the documented defaults.
        }
        return null;
    }

    private static void addForgeAliases(VariableContext.Builder builder, NamespacedKey key, Object rawValue) {
        if (!FORGE_NAMESPACE.equalsIgnoreCase(key.getNamespace())) {
            return;
        }
        String path = key.getKey().toLowerCase(Locale.ROOT);
        switch (path) {
            case "forge.quality_id" -> aliases(builder, rawValue,
                    "forge_quality_id", "forge.quality_id", "quality_id");
            case "forge.quality_display" -> aliases(builder, rawValue,
                    "forge_quality_display", "forge.quality_display", "quality_display");
            case "forge.quality_multiplier" -> {
                NumberResult result = finiteDouble(rawValue, 1D);
                aliases(builder, result.value(),
                        "forge_quality_multiplier", "forge.quality_multiplier", "quality_multiplier");
                builder.with("forge_quality_multiplier_valid", result.valid() ? 1 : 0);
            }
            case "forge.forge_recipe_id" -> aliases(builder, rawValue,
                    "forge_recipe_id", "forge.forge_recipe_id");
            default -> {
            }
        }
    }

    private static void aliases(VariableContext.Builder builder, Object value, String... names) {
        for (String name : names) {
            builder.with(name, value);
        }
    }

    private static NumberResult finiteDouble(Object value, double fallback) {
        try {
            double parsed = value instanceof Number number
                    ? number.doubleValue()
                    : Double.parseDouble(String.valueOf(value));
            boolean valid = Double.isFinite(parsed) && parsed >= 0D;
            return new NumberResult(valid ? parsed : fallback, valid);
        } catch (NumberFormatException exception) {
            return new NumberResult(fallback, false);
        }
    }

    private record NumberResult(double value, boolean valid) {
    }
}
