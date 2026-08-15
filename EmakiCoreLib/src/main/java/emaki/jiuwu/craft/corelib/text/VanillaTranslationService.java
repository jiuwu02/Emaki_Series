package emaki.jiuwu.craft.corelib.text;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;

import emaki.jiuwu.craft.corelib.api.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class VanillaTranslationService {

    private final AtomicReference<Map<String, String>> translations = new AtomicReference<>(Map.of());

    public void install(Map<String, String> table) {
        translations.set(table == null || table.isEmpty() ? Map.of() : Map.copyOf(table));
    }

    public boolean isAvailable() {
        return !translations.get().isEmpty();
    }

    public int size() {
        return translations.get().size();
    }

    public String translate(String translationKey) {
        if (Texts.isBlank(translationKey)) {
            return null;
        }
        String value = translations.get().get(translationKey);
        return Texts.isBlank(value) ? null : value;
    }

    public String translateMaterial(Material material) {
        if (material == null) {
            return null;
        }
        String translated = translate(ItemTextBridge.itemTranslationKey(material));
        return translated != null ? translated : translate(ItemTextBridge.blockTranslationKey(material));
    }
}
