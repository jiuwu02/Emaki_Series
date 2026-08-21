package emaki.jiuwu.craft.item.trigger;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;

import emaki.jiuwu.craft.corelib.trigger.TriggerRegistry;

public final class ProjectileTriggerResolver {

    public record ProjectileTriggers(String launchTrigger, String hitTrigger, String landTrigger) {
    }

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

    public static ProjectileTriggers resolve(Projectile projectile) {
        for (var entry : TABLE.entrySet()) {
            if (entry.getKey().isInstance(projectile)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public static String hitOrLandTrigger(Projectile projectile, boolean hitEntity) {
        ProjectileTriggers triggers = resolve(projectile);
        if (triggers == null) return null;
        return hitEntity ? triggers.hitTrigger() : triggers.landTrigger();
    }
}
