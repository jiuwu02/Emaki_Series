package emaki.jiuwu.craft.structure.spawn;

import emaki.jiuwu.craft.structure.generate.StructureFacing;
import emaki.jiuwu.craft.structure.generate.TemplateTransform;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public final class SpawnPointSampler {

    private SpawnPointSampler() {
    }

    public static Location sample(SpawnContext context, SpawnEntry entry) {
        World world = context.world();
        Vector point = switch (entry.mode()) {
            case FIXED -> fixed(context, entry);
            case SPHERE -> sphere(context, entry);
            case REGION -> region(context, entry);
        };
        return point.toLocation(world);
    }

    private static Vector fixed(SpawnContext context, SpawnEntry entry) {
        int[] pos = entry.pos();
        Vector offset = TemplateTransform.localToWorld(pos[0], pos[1], pos[2],
                context.template().sizeX(), context.template().sizeZ(), context.facing());
        return atAnchor(context, offset);
    }

    private static Vector sphere(SpawnContext context, SpawnEntry entry) {
        Vector center = fixed(context, entry);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double radius = entry.radius();
        if (radius <= 0.0d) {
            return center;
        }
        double u = random.nextDouble(-1.0d, 1.0d);
        double theta = random.nextDouble(0.0d, Math.PI * 2.0d);
        double radial = Math.sqrt(Math.max(0.0d, 1.0d - u * u));
        double distance = radius * Math.cbrt(random.nextDouble(0.0d, 1.0d));
        return center.clone().add(new Vector(
                radial * Math.cos(theta) * distance,
                u * distance,
                radial * Math.sin(theta) * distance));
    }

    private static Vector region(SpawnContext context, SpawnEntry entry) {
        int[] min = entry.regionMin();
        int[] max = entry.regionMax();
        Vector first = TemplateTransform.localToWorld(min[0], min[1], min[2],
                context.template().sizeX(), context.template().sizeZ(), context.facing());
        Vector second = TemplateTransform.localToWorld(max[0], max[1], max[2],
                context.template().sizeX(), context.template().sizeZ(), context.facing());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int lowX = (int) Math.min(first.getX(), second.getX());
        int highX = (int) Math.max(first.getX(), second.getX());
        int lowY = (int) Math.min(first.getY(), second.getY());
        int highY = (int) Math.max(first.getY(), second.getY());
        int lowZ = (int) Math.min(first.getZ(), second.getZ());
        int highZ = (int) Math.max(first.getZ(), second.getZ());
        int x = random.nextInt(lowX, highX + 1);
        int y = random.nextInt(lowY, highY + 1);
        int z = random.nextInt(lowZ, highZ + 1);
        return atAnchor(context, new Vector(x, y, z));
    }

    private static Vector atAnchor(SpawnContext context, Vector offset) {
        return new Vector(context.anchorX() + offset.getX() + 0.5d,
                context.anchorY() + offset.getY(),
                context.anchorZ() + offset.getZ() + 0.5d);
    }

    public static float combinedYaw(SpawnContext context, SpawnEntry entry) {
        if (entry.facing() == null) {
            return context.templateYaw();
        }
        StructureFacing base = StructureFacing.parse(entry.facing());
        float baseYaw = base == null ? 0.0f : base.yawDegrees();
        float combined = baseYaw + context.facing().yawDegrees();
        float normalized = combined % 360.0f;
        return normalized < 0.0f ? normalized + 360.0f : normalized;
    }
}
