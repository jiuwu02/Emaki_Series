package emaki.jiuwu.craft.dungeon.score;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    public static Map<UUID, Double> calculate(ScoreWeights weights, Map<UUID, ScoreStats> team) {
        if (team == null || team.isEmpty()) {
            return Map.of();
        }
        int size = team.size();
        double totalDamage = 0.0D;
        long totalKills = 0L;
        double totalHeal = 0.0D;
        long totalDeath = 0L;
        double totalDefense = 0.0D;
        for (ScoreStats stats : team.values()) {
            totalDamage += stats.damage();
            totalKills += stats.kills();
            totalHeal += stats.heal();
            totalDeath += stats.death();
            totalDefense += stats.defense();
        }
        double totalWeight = weights.totalWeight();
        if (totalWeight <= 0.0D) {
            return uniform(team.keySet());
        }
        Map<UUID, Double> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, ScoreStats> entry : team.entrySet()) {
            ScoreStats stats = entry.getValue();
            double weighted = 0.0D;
            weighted += weights.damageWeight() * share(stats.damage(), totalDamage, size);
            weighted += weights.killWeight() * share(stats.kills(), totalKills, size);
            weighted += weights.healWeight() * share(stats.heal(), totalHeal, size);
            weighted += weights.deathWeight()
                    * share(totalDeath - stats.death(), totalDeath, size);
            weighted += weights.defenseWeight() * share(stats.defense(), totalDefense, size);
            double score = weights.max() * weighted / totalWeight;
            result.put(entry.getKey(), round1(score));
        }
        return result;
    }

    private static double share(double value, double total, int size) {
        if (total <= 0.0D) {
            return 1.0D / size;
        }
        return Math.max(0.0D, value / total);
    }

    private static double share(long value, long total, int size) {
        if (total <= 0L) {
            return 1.0D / size;
        }
        return Math.max(0.0D, (double) value / (double) total);
    }

    private static Map<UUID, Double> uniform(Iterable<UUID> members) {
        Map<UUID, Double> result = new LinkedHashMap<>();
        for (UUID memberId : members) {
            result.put(memberId, 0.0D);
        }
        return result;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}
