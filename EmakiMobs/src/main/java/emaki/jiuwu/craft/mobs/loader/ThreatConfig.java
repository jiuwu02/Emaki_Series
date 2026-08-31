package emaki.jiuwu.craft.mobs.loader;

public record ThreatConfig(
        boolean enabled,
        ThreatWeightsConfig weights,
        ThreatDecayConfig decay,
        double maxRange
) {

    public record ThreatWeightsConfig(double damage, double healing) {}

    public record ThreatDecayConfig(
            double rate,
            boolean outOfRange
    ) {}
}
