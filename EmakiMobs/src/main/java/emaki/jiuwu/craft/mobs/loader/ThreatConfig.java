package emaki.jiuwu.craft.mobs.loader;

/**
 * 威胁值系统配置，对应 YAML 中的 {@code threat:} 节点。
 *
 * <p>配置示例：
 * <pre>
 * threat:
 *   enabled: true
 *   weights:
 *     damage: 1.0
 *     healing: 0.5
 *   decay:
 *     rate: 0.05
 *     out_of_range: true
 *   max_range: 64
 * </pre>
 */
public record ThreatConfig(
        boolean enabled,
        ThreatWeightsConfig weights,
        ThreatDecayConfig decay,
        double maxRange
) {

    /** 伤害和治疗事件对威胁值的权重系数。 */
    public record ThreatWeightsConfig(double damage, double healing) {}

    /** 威胁值衰减参数。 */
    public record ThreatDecayConfig(
            /** 每秒衰减比例（0.05 = 每秒降低5%）。 */
            double rate,
            /** 玩家超出 max_range 时是否立即清除威胁值。 */
            boolean outOfRange
    ) {}
}
