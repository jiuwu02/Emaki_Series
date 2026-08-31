package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

final class PotionEffects {

    private PotionEffects() {
    }

    static PotionEffectType resolve(String rawType) {
        String normalized = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(
                normalized.contains(":") ? normalized : "minecraft:" + normalized);
        return key == null ? null : Registry.EFFECT.get(key);
    }
}
