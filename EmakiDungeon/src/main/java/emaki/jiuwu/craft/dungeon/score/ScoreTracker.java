package emaki.jiuwu.craft.dungeon.score;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class ScoreTracker {

    public enum Stat {
        DAMAGE,
        KILLS,
        HEAL,
        DEATH,
        DEFENSE
    }

    private final Map<String, Map<UUID, EnumMap<Stat, LongAdder>>> instances = new ConcurrentHashMap<>();

    public void add(String instanceId, UUID playerId, Stat stat, double amount) {
        if (instanceId == null || playerId == null || stat == null || amount <= 0.0D) {
            return;
        }
        Map<UUID, EnumMap<Stat, LongAdder>> players =
                instances.computeIfAbsent(instanceId, key -> new ConcurrentHashMap<>());
        EnumMap<Stat, LongAdder> stats = players.computeIfAbsent(playerId,
                key -> new EnumMap<>(Stat.class));
        stats.computeIfAbsent(stat, key -> new LongAdder()).add(Math.round(amount));
    }

    public Map<UUID, ScoreStats> snapshot(String instanceId) {
        Map<UUID, EnumMap<Stat, LongAdder>> players = instances.get(instanceId);
        if (players == null || players.isEmpty()) {
            return Map.of();
        }
        Map<UUID, ScoreStats> result = new HashMap<>();
        for (Map.Entry<UUID, EnumMap<Stat, LongAdder>> entry : players.entrySet()) {
            EnumMap<Stat, LongAdder> stats = entry.getValue();
            result.put(entry.getKey(), new ScoreStats(
                    sum(stats, Stat.DAMAGE),
                    longSum(stats, Stat.KILLS),
                    sum(stats, Stat.HEAL),
                    longSum(stats, Stat.DEATH),
                    sum(stats, Stat.DEFENSE)));
        }
        return result;
    }

    public void remove(String instanceId) {
        if (instanceId != null) {
            instances.remove(instanceId);
        }
    }

    private static double sum(EnumMap<Stat, LongAdder> stats, Stat stat) {
        return longSum(stats, stat);
    }

    private static long longSum(EnumMap<Stat, LongAdder> stats, Stat stat) {
        LongAdder adder = stats.get(stat);
        return adder == null ? 0L : adder.sum();
    }
}
