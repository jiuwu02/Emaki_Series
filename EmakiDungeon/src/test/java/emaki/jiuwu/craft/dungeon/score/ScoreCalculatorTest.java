package emaki.jiuwu.craft.dungeon.score;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ScoreCalculatorTest {

    @Test
    void soloPerfectorTeamScoresMax() {
        UUID solo = UUID.randomUUID();
        Map<UUID, ScoreStats> team = Map.of(solo,
                new ScoreStats(100.0D, 5L, 50.0D, 0L, 10.0D));
        Map<UUID, Double> scores = ScoreCalculator.calculate(ScoreWeights.defaults(), team);
        assertEquals(100.0D, scores.get(solo), 0.001D);
    }

    @Test
    void damageOnlyWeightSplitsByShare() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<UUID, ScoreStats> team = new LinkedHashMap<>();
        team.put(alice, new ScoreStats(750.0D, 0L, 0.0D, 0L, 0.0D));
        team.put(bob, new ScoreStats(250.0D, 0L, 0.0D, 0L, 0.0D));
        ScoreWeights weights = new ScoreWeights(2.0D, 0.0D, 0.0D, 0.0D, 0.0D, 100.0D);
        Map<UUID, Double> scores = ScoreCalculator.calculate(weights, team);
        assertEquals(75.0D, scores.get(alice), 0.001D);
        assertEquals(25.0D, scores.get(bob), 0.001D);
    }

    @Test
    void deathWeightPenalizesFrequentDeaths() {
        UUID clean = UUID.randomUUID();
        UUID reckless = UUID.randomUUID();
        Map<UUID, ScoreStats> team = new LinkedHashMap<>();
        team.put(clean, new ScoreStats(0.0D, 0L, 0.0D, 0L, 0.0D));
        team.put(reckless, new ScoreStats(0.0D, 0L, 0.0D, 2L, 0.0D));
        ScoreWeights weights = new ScoreWeights(0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 100.0D);
        Map<UUID, Double> scores = ScoreCalculator.calculate(weights, team);
        assertEquals(100.0D, scores.get(clean), 0.001D);
        assertEquals(0.0D, scores.get(reckless), 0.001D);
    }

    @Test
    void scoresRoundToOneDecimal() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<UUID, ScoreStats> team = new LinkedHashMap<>();
        team.put(alice, new ScoreStats(1.0D, 0L, 0.0D, 0L, 0.0D));
        team.put(bob, new ScoreStats(2.0D, 0L, 0.0D, 0L, 0.0D));
        ScoreWeights weights = new ScoreWeights(1.0D, 0.0D, 0.0D, 0.0D, 0.0D, 100.0D);
        Map<UUID, Double> scores = ScoreCalculator.calculate(weights, team);
        assertEquals(33.3D, scores.get(alice), 0.001D);
        assertEquals(66.7D, scores.get(bob), 0.001D);
    }

    @Test
    void emptyTeamReturnsEmptyResult() {
        assertTrue(ScoreCalculator.calculate(ScoreWeights.defaults(), Map.of()).isEmpty());
        assertTrue(ScoreCalculator.calculate(ScoreWeights.defaults(), null).isEmpty());
    }

    @Test
    void zeroTotalWeightFallsBackToUniformZero() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<UUID, ScoreStats> team = new LinkedHashMap<>();
        team.put(alice, new ScoreStats(10.0D, 1L, 1.0D, 0L, 1.0D));
        team.put(bob, new ScoreStats(5.0D, 1L, 1.0D, 0L, 1.0D));
        ScoreWeights weights = new ScoreWeights(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 100.0D);
        Map<UUID, Double> scores = ScoreCalculator.calculate(weights, team);
        assertEquals(0.0D, scores.get(alice), 0.001D);
        assertEquals(0.0D, scores.get(bob), 0.001D);
    }

    @Test
    void teamScoresNeverExceedMax() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Map<UUID, ScoreStats> team = new LinkedHashMap<>();
        team.put(alice, new ScoreStats(1_000_000.0D, 100L, 5_000.0D, 0L, 2_000.0D));
        team.put(bob, new ScoreStats(0.0D, 0L, 0.0D, 0L, 0.0D));
        Map<UUID, Double> scores = ScoreCalculator.calculate(ScoreWeights.defaults(), team);
        for (Double score : scores.values()) {
            assertTrue(score >= 0.0D && score <= 100.0D, "score out of range: " + score);
        }
        assertTrue(scores.get(alice) >= scores.get(bob));
    }
}
