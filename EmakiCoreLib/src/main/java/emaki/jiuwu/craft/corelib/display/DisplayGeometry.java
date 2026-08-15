package emaki.jiuwu.craft.corelib.display;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class DisplayGeometry {

    private DisplayGeometry() {
    }

    public record Vector3(double x, double y, double z) {

        public static final Vector3 ZERO = new Vector3(0D, 0D, 0D);
        public static final Vector3 ONE = new Vector3(1D, 1D, 1D);

        public Vector3f toVector3f() {
            return new Vector3f((float) x, (float) y, (float) z);
        }
    }

    public record TextProfile(Vector3 offset,
            Vector3 scale,
            String billboard,
            int lineWidth,
            int backgroundArgb,
            boolean shadow,
            boolean seeThrough) {

        public TextProfile {
            offset = offset == null ? new Vector3(0.5D, 1.6D, 0.5D) : offset;
            scale = scale == null ? Vector3.ONE : scale;
            billboard = billboard == null || billboard.isBlank() ? "center" : billboard;
            lineWidth = Math.max(1, lineWidth);
        }

        public static TextProfile defaults() {
            return new TextProfile(null, null, "center", 200, 0, true, false);
        }

        public Transformation transformation() {
            return new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    scale.toVector3f(),
                    new Quaternionf()
            );
        }
    }

    public record AxisRotation(double min, double max) {

        public AxisRotation {
            if (min > max) {
                double swapped = min;
                min = max;
                max = swapped;
            }
        }

        public static AxisRotation fixed(double value) {
            return new AxisRotation(value, value);
        }

        public double resolve() {
            return min == max ? min : ThreadLocalRandom.current().nextDouble(min, max);
        }
    }

    public record RotationProfile(AxisRotation x, AxisRotation y, AxisRotation z) {

        public RotationProfile {
            x = x == null ? AxisRotation.fixed(0D) : x;
            y = y == null ? AxisRotation.fixed(0D) : y;
            z = z == null ? AxisRotation.fixed(0D) : z;
        }

        public static RotationProfile none() {
            return new RotationProfile(null, null, null);
        }
    }

    public record ItemProfile(Vector3 offset, RotationProfile rotation, Vector3 scale) {

        public ItemProfile {
            offset = offset == null ? new Vector3(0.5D, 1.0D, 0.5D) : offset;
            rotation = rotation == null ? RotationProfile.none() : rotation;
            scale = scale == null ? new Vector3(0.5D, 0.5D, 0.5D) : scale;
        }

        public static ItemProfile defaults() {
            return new ItemProfile(null, null, null);
        }

        public Location applyOffset(Location base) {
            if (base == null) {
                return null;
            }
            return new Location(
                    base.getWorld(),
                    base.getX() + offset.x(),
                    base.getY() + offset.y(),
                    base.getZ() + offset.z()
            );
        }

        public Transformation transformation() {
            Quaternionf rotationQuaternion = new Quaternionf().rotationXYZ(
                    (float) Math.toRadians(rotation.x().resolve()),
                    (float) Math.toRadians(rotation.y().resolve()),
                    (float) Math.toRadians(rotation.z().resolve())
            );
            return new Transformation(
                    new Vector3f(),
                    rotationQuaternion,
                    scale.toVector3f(),
                    new Quaternionf()
            );
        }
    }
}
