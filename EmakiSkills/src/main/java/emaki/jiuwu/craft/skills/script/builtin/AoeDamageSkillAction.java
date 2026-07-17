package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class AoeDamageSkillAction extends AbstractSkillScriptAction {

    public AoeDamageSkillAction() {
        super("aoe_damage", "combat", "Deal damage to entities in an area.",
                SkillActionParameter.required("amount", SkillActionParameterType.DOUBLE, "Damage amount"),
                SkillActionParameter.optional("radius", SkillActionParameterType.DOUBLE, "5", "Area radius"),
                SkillActionParameter.optional("center", SkillActionParameterType.STRING, "target", "Center point (caster/target/look)"),
                SkillActionParameter.optional("shape", SkillActionParameterType.STRING, "sphere", "Area shape (sphere/cylinder)"),
                SkillActionParameter.optional("filter", SkillActionParameterType.STRING, "hostile", "Target filter (hostile/all)"),
                SkillActionParameter.optional("max_targets", SkillActionParameterType.INTEGER, "20", "Maximum targets"),
                SkillActionParameter.optional("exclude_caster", SkillActionParameterType.STRING, "true", "Exclude caster from targets"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        double amount = Math.max(0D, doubleArg(arguments, "amount", 0D));
        double radius = Math.max(0.5D, Math.min(doubleArg(arguments, "radius", 5D), 64D));
        int maxTargets = Math.max(0, intArg(arguments, "max_targets", 20));
        boolean cylinder = "cylinder".equals(arg(arguments, "shape", "sphere")
                .toLowerCase(java.util.Locale.ROOT));
        boolean hostileOnly = "hostile".equals(arg(arguments, "filter", "hostile")
                .toLowerCase(java.util.Locale.ROOT));
        boolean excludeCaster = Boolean.parseBoolean(arg(arguments, "exclude_caster", "true"));
        Player caster = context.caster();

        return callAtResolvedLocation(context, arguments, "center", "target", center -> {
            World world = center.getWorld();
            Collection<Entity> nearby = world == null
                    ? List.of()
                    : world.getNearbyEntities(center, radius, radius, radius);
            return new AreaScan(world, center.getX(), center.getY(), center.getZ(), List.copyOf(nearby));
        }).thenCompose(scan -> {
            if (scan.world() == null || maxTargets == 0) {
                return recordHitCount(context, caster, 0);
            }
            AtomicInteger hitCount = new AtomicInteger();
            List<CompletableFuture<Integer>> hits = scan.entities().stream()
                    .filter(LivingEntity.class::isInstance)
                    .map(LivingEntity.class::cast)
                    .filter(target -> !excludeCaster || !target.equals(caster))
                    .filter(target -> !hostileOnly || !(target instanceof Player))
                    .map(target -> callOnEntity(context, target, () -> {
                        Location targetLocation = target.getLocation();
                        if (targetLocation.getWorld() != scan.world()
                                || !inside(targetLocation, scan, radius * radius, cylinder)
                                || !reserveHit(hitCount, maxTargets)) {
                            return 0;
                        }
                        target.damage(amount, caster);
                        return 1;
                    }))
                    .toList();
            return CompletableFuture.allOf(hits.toArray(CompletableFuture[]::new))
                    .thenCompose(ignored -> recordHitCount(context, caster, hitCount.get()));
        });
    }

    private CompletableFuture<SkillActionResult> recordHitCount(SkillScriptContext context,
            Player caster,
            int hitCount) {
        return callOnEntity(context, caster, () -> {
            context.putVariable("aoe_hit_count", hitCount);
            context.putSharedValue("aoe_hit_count", hitCount);
            return SkillActionResult.ok();
        });
    }

    private boolean inside(Location location, AreaScan scan, double radiusSquared, boolean cylinder) {
        double dx = location.getX() - scan.x();
        double dz = location.getZ() - scan.z();
        if (cylinder) {
            return dx * dx + dz * dz <= radiusSquared;
        }
        double dy = location.getY() - scan.y();
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    private boolean reserveHit(AtomicInteger hitCount, int maxTargets) {
        int current;
        do {
            current = hitCount.get();
            if (current >= maxTargets) {
                return false;
            }
        } while (!hitCount.compareAndSet(current, current + 1));
        return true;
    }

    private record AreaScan(World world, double x, double y, double z, List<Entity> entities) {
    }
}
