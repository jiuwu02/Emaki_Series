package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.assembly.EmakiStatContribution;

final class ForgeStatContributionBuilder {

    List<EmakiStatContribution> buildStatContributions(List<ForgeMaterialContribution> materials, double multiplier) {
        List<EmakiStatContribution> stats = new ArrayList<>();
        int sequence = 0;
        if (materials == null) {
            return stats;
        }
        for (ForgeMaterialContribution material : materials) {
            if (material == null || material.amount() <= 0 || material.material() == null) {
                continue;
            }
            for (Map.Entry<String, Double> entry : material.material().statContributions().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                stats.add(new EmakiStatContribution(
                        entry.getKey(),
                        entry.getValue() * material.amount() * multiplier,
                        material.material().key() + "#" + material.sequence(),
                        sequence++
                ));
            }
        }
        return stats;
    }
}
