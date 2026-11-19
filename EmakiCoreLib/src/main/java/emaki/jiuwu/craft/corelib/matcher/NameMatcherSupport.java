package emaki.jiuwu.craft.corelib.matcher;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.VanillaTranslationService;
import net.kyori.adventure.text.Component;

public final class NameMatcherSupport {

    private static volatile VanillaTranslationService translations;

    private NameMatcherSupport() {
    }

    public static void installTranslations(@Nullable VanillaTranslationService service) {
        translations = service;
    }

    static @NotNull List<String> names(@NotNull ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null) {
            Component named = meta.hasDisplayName() ? meta.displayName() : null;
            if (named == null && meta.hasItemName()) {
                named = meta.itemName();
            }
            if (named != null) {
                String plain = MiniMessages.plain(named);
                if (plain != null && !plain.isBlank()) {
                    return List.of(plain.trim());
                }
            }
        }
        List<String> candidates = new ArrayList<>(2);
        VanillaTranslationService service = translations;
        if (service != null) {
            String localized = service.translateMaterial(item.getType());
            if (localized != null && !localized.isBlank()) {
                candidates.add(localized.trim());
            }
        }
        candidates.add(item.getType().getKey().value());
        return candidates;
    }
}
