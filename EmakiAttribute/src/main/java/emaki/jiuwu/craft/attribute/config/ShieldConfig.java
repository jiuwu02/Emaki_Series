package emaki.jiuwu.craft.attribute.config;

import java.util.Locale;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.yaml.YamlSection;

/**
 * 盾牌格挡设置。
 *
 * <p>{@code mode} 决定格挡由谁结算：
 * <ul>
 *   <li>{@code vanilla} —— 保持原版 {@code BLOCKING} 减伤，举盾按原版规则完全免除该次伤害。
 *       此模式下 {@code target_blocking} 恒为 {@code 0}，伤害管线中的格挡阶段不会触发。</li>
 *   <li>{@code attribute} —— 清零原版 {@code BLOCKING}，改由伤害类型的格挡阶段读取
 *       {@code target_blocking} 与目标属性结算，可实现百分比减伤。</li>
 * </ul>
 *
 * <p>{@code require_facing} 只在 {@code attribute} 模式下有意义：原版 {@code BLOCKING}
 * 自带正面判定，一旦由 EA 接管就必须自行重建，否则会退化成全向格挡。
 */
public record ShieldConfig(boolean attributeModeEnabled,
        boolean requireFacing,
        double facingAngleDegrees) {

    /** 原版结算格挡，EA 不介入。 */
    public static final String MODE_VANILLA = "vanilla";

    /** EA 通过伤害阶段结算格挡。 */
    public static final String MODE_ATTRIBUTE = "attribute";

    /** 与原版一致的格挡张角：原版判定等价于以视线为中心的 180 度半平面。 */
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
