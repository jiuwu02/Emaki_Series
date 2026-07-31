package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Locale;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

/**
 * Resolves a potion effect name, keeping the v1 rules.
 *
 * <p>Lowercased, spaces turned into underscores, {@code minecraft:} assumed when no namespace is given, then
 * looked up in {@code Registry.EFFECT} rather than an enum — potion effect types stopped being an enum in
 * modern Bukkit, so registry lookup is the only correct path.</p>
 */
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
