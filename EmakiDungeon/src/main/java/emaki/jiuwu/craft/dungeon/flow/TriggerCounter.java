package emaki.jiuwu.craft.dungeon.flow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import emaki.jiuwu.craft.dungeon.flow.FlowModules.Head;

public final class TriggerCounter {

    public enum ExhaustedTier {
        NONE,
        PER_TRIGGER,
        PER_PLAYER,
        TOTAL
    }

    private static final class Counters {

        private int trigger;
        private int total;
        private final Map<UUID, Integer> perPlayer = new ConcurrentHashMap<>();
    }

    private final Map<String, Counters> counters = new ConcurrentHashMap<>();

    public ExhaustedTier check(Head head, UUID playerId) {
        if (head == null || !head.enabled()) {
            return ExhaustedTier.PER_TRIGGER;
        }
        Counters current = counters.computeIfAbsent(head.id(), key -> new Counters());
        FlowModules.TriggerLimits limits = head.limits();
        synchronized (current) {
            if (limits.perTrigger() > 0 && current.trigger >= limits.perTrigger()) {
                return ExhaustedTier.PER_TRIGGER;
            }
            if (limits.total() > 0 && current.total >= limits.total()) {
                return ExhaustedTier.TOTAL;
            }
            if (playerId != null && limits.perPlayer() > 0) {
                int used = current.perPlayer.getOrDefault(playerId, 0);
                if (used >= limits.perPlayer()) {
                    return ExhaustedTier.PER_PLAYER;
                }
            }
            return ExhaustedTier.NONE;
        }
    }

    public boolean consume(Head head, UUID playerId) {
        if (check(head, playerId) != ExhaustedTier.NONE) {
            return false;
        }
        Counters current = counters.computeIfAbsent(head.id(), key -> new Counters());
        synchronized (current) {
            if (check(head, playerId) != ExhaustedTier.NONE) {
                return false;
            }
            current.trigger++;
            current.total++;
            if (playerId != null) {
                current.perPlayer.merge(playerId, 1, Integer::sum);
            }
            return true;
        }
    }

    public int remaining(Head head, UUID playerId) {
        if (head == null) {
            return 0;
        }
        FlowModules.TriggerLimits limits = head.limits();
        if (limits.total() <= 0 && limits.perTrigger() <= 0 && limits.perPlayer() <= 0) {
            return Integer.MAX_VALUE;
        }
        Counters current = counters.get(head.id());
        if (current == null) {
            if (limits.total() > 0) {
                return limits.total();
            }
            if (limits.perTrigger() > 0) {
                return limits.perTrigger();
            }
            return limits.perPlayer();
        }
        synchronized (current) {
            int remaining = Integer.MAX_VALUE;
            if (limits.perTrigger() > 0) {
                remaining = Math.min(remaining, limits.perTrigger() - current.trigger);
            }
            if (limits.total() > 0) {
                remaining = Math.min(remaining, limits.total() - current.total);
            }
            if (playerId != null && limits.perPlayer() > 0) {
                remaining = Math.min(remaining,
                        limits.perPlayer() - current.perPlayer.getOrDefault(playerId, 0));
            }
            return Math.max(0, remaining);
        }
    }

    public void reset() {
        counters.clear();
    }
}
