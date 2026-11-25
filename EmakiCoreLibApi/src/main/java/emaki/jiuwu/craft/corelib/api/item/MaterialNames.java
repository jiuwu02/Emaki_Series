package emaki.jiuwu.craft.corelib.api.item;

import java.util.Locale;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Display-name helpers for {@link Material} values.
 *
 * <p>Both the API facade and the runtime presentation renderer need the same
 * translatable-key lookup and the same {@code DIRT_PATH -> Dirt Path} fallback, so the two live
 * here and callers delegate instead of repeating the split-and-capitalize loop.
 */
public final class MaterialNames {

    private MaterialNames() {
    }

    /**
     * {@return the {@code item.minecraft.*} / {@code block.minecraft.*} translation key for a
     * material, or an empty string when none applies}
     *
     * @param material the material to describe, may be {@code null}
     */
    public static @NotNull String translationKey(Material material) {
        if (material == null) {
            return "";
        }
        try {
            if (material.isItem()) {
                return material.getItemTranslationKey();
            }
            if (material.isBlock()) {
                return material.getBlockTranslationKey();
            }
            return material.translationKey();
        } catch (RuntimeException _) {
            return "";
        }
    }

    /**
     * {@return a title-cased display fallback derived from the material name}
     *
     * @param material the material to describe, may be {@code null}
     */
    public static @NotNull String humanize(Material material) {
        if (material == null) {
            return "";
        }
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }
}
