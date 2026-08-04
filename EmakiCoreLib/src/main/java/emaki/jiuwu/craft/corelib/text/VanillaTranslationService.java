package emaki.jiuwu.craft.corelib.text;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;

import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;

/**
 * Resolves vanilla translation keys to localized text on the server side.
 *
 * <p>Vanilla item and block names are translated by the client from its resource
 * pack, so the server only ever holds a translation key such as
 * {@code block.minecraft.campfire}. Any server-side feature that has to reason
 * about the localized name - searching a storage by a Chinese item name, for
 * example - therefore needs its own copy of the language table.
 *
 * <p>The table is supplied by {@link VanillaLanguageDownloader} and installed
 * through {@link #install(Map)}. Until that happens every lookup reports
 * "unavailable" and callers keep their existing behaviour rather than silently
 * degrading to an empty name.
 *
 * <p>Thread safety: the table is published as one immutable map through an
 * {@link AtomicReference}, so lookups are safe from any thread including Folia
 * region threads. Installation replaces the whole map at once and never mutates
 * a table that a reader may be iterating.
 */
public final class VanillaTranslationService {

    private final AtomicReference<Map<String, String>> translations = new AtomicReference<>(Map.of());

    /**
     * Installs a translation table, replacing any previous one.
     *
     * @param table translation key to localized text, {@code null} clears the table
     */
    public void install(Map<String, String> table) {
        translations.set(table == null || table.isEmpty() ? Map.of() : Map.copyOf(table));
    }

    /** {@return whether a translation table is loaded and usable} */
    public boolean isAvailable() {
        return !translations.get().isEmpty();
    }

    /** {@return the number of loaded translation entries} */
    public int size() {
        return translations.get().size();
    }

    /**
     * {@return the localized text for a translation key, or {@code null} when the
     * table is absent or has no entry for it}
     *
     * @param translationKey a vanilla translation key such as {@code item.minecraft.stick}
     */
    public String translate(String translationKey) {
        if (Texts.isBlank(translationKey)) {
            return null;
        }
        String value = translations.get().get(translationKey);
        return Texts.isBlank(value) ? null : value;
    }

    /**
     * {@return the localized name for a material, or {@code null} when unavailable}
     *
     * <p>Uses the same item-then-block key resolution as
     * {@link ItemTextBridge#effectiveName}, because a material that is only a
     * block has no {@code item.minecraft.*} entry and vice versa.
     *
     * @param material the material to name, may be {@code null}
     */
    public String translateMaterial(Material material) {
        if (material == null) {
            return null;
        }
        String translated = translate(ItemTextBridge.itemTranslationKey(material));
        return translated != null ? translated : translate(ItemTextBridge.blockTranslationKey(material));
    }
}
