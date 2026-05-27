package emaki.jiuwu.craft.skills.script.builtin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

/**
 * Area-of-effect damage skill action.
 * <p>
 * Damages all valid entities within a radius around a center point.
 * Writes {@code aoe_hit_count} to the context shared state for use by subsequent actions.
 */
public final class AoeDamageSkillAction extends AbstractSkillScriptAction {

    public AoeDamageSkillAction() {
        super("aoe_damage", "combat", "Deal damage to entities in an area.",
                ActionParameter.required("amount", ActionParameterType.DOUBLE, "Damage amount"),
                ActionParameter.optional("radius", ActionParameterType.DOUBLE, "5", "Area radius"),
                ActionParameter.optional("center", ActionParameterType.STRING, "target", "Center point (caster/target/look)"),
                ActionParameter.optional("shape", ActionParameterType.STRING, "sphere", "Area shape (sphere/cylinder)"),
                ActionParameter.optional("filter", ActionParameterType.STRING, "hostile", "Target filter (hostile/all)"),
                ActionParameter.optional("max_targets", ActionParameterType.INTEGER, "20", "Maximum targets"),
                ActionParameter.optional("exclude_caster", ActionParameterType.STRING, "true", "Exclude caster from targets"));
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        double amount = doubleArg(arguments, "amount", 0D);
        double radius = doubleArg(arguments, "radius", 5D);
        int maxTargets = intArg(arguments, "max_targets", 20);
        String shape = arg(arguments, "shape", "sphere").toLowerCase();
        String filter = arg(arguments, "filter", "hostile").toLowerCase();
        boolean excludeCaster = Boolean.parseBoolean(arg(arguments, "exclude_caster", "true"));

        Location center = resolveCenterLocation(context, arguments);
        if (center == null || center.getWorld() == null) {
            return completed(ActionResult.ok());
        }

        radius = Math.max(0.5D, Math.min(radius, 64D));
        double radiusSquared = radius * radius;
        boolean isCylinder = "cylinder".equals(shape);

        Player caster = context.caster();
        Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        List<LivingEntity> targets = new ArrayList<>();

        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (excludeCaster && entity.equals(caster)) {
                continue;
            }
            if ("hostile".equals(filter) && entity instanceof Player) {
                continue;
            }
            Location entityLocation = entity.getLocation();
            double distanceSquared;
            if (isCylinder) {
                double dx = entityLocation.getX() - center.getX();
                double dz = entityLocation.getZ() - center.getZ();
                distanceSquared = dx * dx + dz * dz;
            } else {
                distanceSquared = entityLocation.distanceSquared(center);
            }
            if (distanceSquared <= radiusSquared) {
                targets.add(living);
            }
            if (targets.size() >= maxTargets) {
                break;
            }
        }

        int hitCount = 0;
        for (LivingEntity target : targets) {
            target.damage(Math.max(0D, amount), caster);
            hitCount++;
        }

        context.putVariable("aoe_hit_count", hitCount);
        context.putSharedValue("aoe_hit_count", hitCount);
        return completed(ActionResult.ok());
    }

    private Location resolveCenterLocation(SkillScriptContext context, Map<String, String> arguments) {
        String centerArg = arg(arguments, "center", "target").toLowerCase();
        if ("caster".equals(centerArg) || "self".equals(centerArg) || "player".equals(centerArg)) {
            return context.caster().getLocation();
        }
        if ("look".equals(centerArg)) {
            return context.caster().getEyeLocation().add(context.caster().getLocation().getDirection().multiply(3));
        }
        Object stored = context.sharedValue(centerArg);
        if (stored instanceof Entity entity) {
            return entity.getLocation();
        }
        if (stored instanceof Location location) {
            return location.clone();
        }
        Entity entity = context.targetEntity();
        if (entity != null) {
            return entity.getLocation();
        }
        Location location = context.targetLocation();
        return location == null ? context.caster().getLocation() : location;
    }
}
