package emaki.jiuwu.craft.attribute.config;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record ShieldConfig(boolean attributeModeEnabled,
        boolean requireFacing,
        double facingAngleDegrees) {

    public static final String MODE_VANILLA = "vanilla";

    public static final String MODE_ATTRIBUTE = "attribute";

    private static final double DEFAULT_FACING_ANGLE_DEGREES = 180D;

    public ShieldConfig {
        facingAngleDegrees = Math.min(180D, Math.max(0D, facingAngleDegrees));
    }

    public static ShieldConfig defaults() {
        return new ShieldConfig(false, true, DEFAULT_FACING_ANGLE_DEGREES);
    }

    public static ShieldConfig fromConfig(YamlSection configuration) {
        if (configuration == null) {
            return defaults();
        }
        String mode = ConfigNodes.string(configuration, "mode", MODE_VANILLA)
                .trim()
                .toLowerCase(Locale.ROOT);
        return new ShieldConfig(
                MODE_ATTRIBUTE.equals(mode),
                Boolean.TRUE.equals(configuration.getBoolean("require_facing", true)),
                configuration.getDouble("facing_angle_degrees", DEFAULT_FACING_ANGLE_DEGREES)
        );
    }
}
