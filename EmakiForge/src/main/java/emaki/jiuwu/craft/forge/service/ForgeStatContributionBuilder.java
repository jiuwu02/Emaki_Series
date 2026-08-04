package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.assembly.EmakiStatContribution;
import emaki.jiuwu.craft.corelib.api.math.Numbers;

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
                double value = scaleContribution(entry.getValue(), material, multiplier);
                stats.add(new EmakiStatContribution(
                        entry.getKey(),
                        value,
                        material.material().key() + "#" + material.sequence(),
                        sequence++
                ));
            }
        }
        return stats;
    }

    Map<String, Object> buildDisplayVariables(List<ForgeMaterialContribution> materials, double multiplier, String numberFormat) {
        Map<String, Double> totals = new LinkedHashMap<>();
        if (materials == null) {
            return Map.of();
        }
        for (ForgeMaterialContribution material : materials) {
            if (material == null || material.amount() <= 0 || material.material() == null) {
                continue;
            }
            for (Map.Entry<String, Double> entry : material.material().statContributions().entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                totals.merge(entry.getKey(), scaleContribution(entry.getValue(), material, multiplier), Double::sum);
            }
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : totals.entrySet()) {
            variables.put(entry.getKey(), Numbers.formatNumber(entry.getValue(), numberFormat));
        }
        return variables;
    }

    private double scaleContribution(double value, ForgeMaterialContribution material, double multiplier) {
        return value * material.amount() * multiplier;
    }
}
