package emaki.jiuwu.craft.item.trigger;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;

import emaki.jiuwu.craft.corelib.trigger.TriggerRegistry;

/**
 * Resolves projectile entity types to their corresponding trigger IDs via a
 * registration table, replacing inline {@code instanceof} dispatch in event
 * listeners.
 *
 * <p><b>Registration order matters:</b> more-specific types (e.g. {@link Trident})
 * must be registered before their supertypes (e.g. {@link AbstractArrow})
 * because resolution uses {@link Class#isInstance} with first-match semantics.
 */
public final class ProjectileTriggerResolver {

    public record ProjectileTriggers(String launchTrigger, String hitTrigger, String landTrigger) {
    }

    // Ordered map: specific subtypes must appear before their supertypes.
    private static final Map<Class<? extends Projectile>, ProjectileTriggers> TABLE;

    static {
        TABLE = new LinkedHashMap<>();
        TABLE.put(Trident.class,
                new ProjectileTriggers(TriggerRegistry.SHOOT_TRIDENT, TriggerRegistry.TRIDENT_HIT, TriggerRegistry.TRIDENT_LAND));
        TABLE.put(AbstractArrow.class,
                new ProjectileTriggers(null, TriggerRegistry.ARROW_HIT, TriggerRegistry.ARROW_LAND));
    }

    private ProjectileTriggerResolver() {
    }

    /**
     * Returns the trigger set registered for this projectile type, or
     * {@code null} if the type is unregistered.
     */
    public static ProjectileTriggers resolve(Projectile projectile) {
        for (var entry : TABLE.entrySet()) {
            if (entry.getKey().isInstance(projectile)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Convenience accessor for the hit or land trigger of this projectile.
     *
     * @param hitEntity {@code true} when the projectile hit a living entity,
     *                  {@code false} when it landed on a block
     * @return the matching trigger ID, or {@code null} when unregistered
     */
    public static String hitOrLandTrigger(Projectile projectile, boolean hitEntity) {
        ProjectileTriggers triggers = resolve(projectile);
        if (triggers == null) return null;
        return hitEntity ? triggers.hitTrigger() : triggers.landTrigger();
    }
}
