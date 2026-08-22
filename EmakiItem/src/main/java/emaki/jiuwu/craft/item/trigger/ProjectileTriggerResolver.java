package emaki.jiuwu.craft.item.trigger;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;

import emaki.jiuwu.craft.corelib.api.trigger.TriggerIds;

public final class ProjectileTriggerResolver {

    public record ProjectileTriggers(String launchTrigger, String hitTrigger, String landTrigger) {
    }

    private static final Map<Class<? extends Projectile>, ProjectileTriggers> TABLE;

    static {
        TABLE = new LinkedHashMap<>();
        TABLE.put(Trident.class,
                new ProjectileTriggers(TriggerIds.SHOOT_TRIDENT, TriggerIds.TRIDENT_HIT, TriggerIds.TRIDENT_LAND));
        TABLE.put(AbstractArrow.class,
                new ProjectileTriggers(null, TriggerIds.ARROW_HIT, TriggerIds.ARROW_LAND));
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
