package emaki.jiuwu.craft.item.trigger;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.item.model.ProficiencyGuardConfig;

public final class ProficiencyGuard {

    public enum Verdict {
        ALLOWED,
        DEDUPED,
        EVENT_LIMIT,
        TARGET_COOLDOWN,
        SOFT_CAP_DECAY
    }

    public final class Session {

        private final Player actor;
        private final String trigger;
        private final String targetId;
        private final Set<String> dispatched = new HashSet<>();
        private int accepted;

        private Session(Player actor, String trigger, String targetId) {
            this.actor = actor;
            this.trigger = trigger;
            this.targetId = targetId;
        }

        public boolean admits(String definitionId, String slotName) {
            Verdict verdict = evaluate(this, definitionId, slotName);
            if (verdict == Verdict.ALLOWED) {
                accepted++;
                return true;
            }
            logRejection(actor, trigger, definitionId, slotName, verdict);
            return false;
        }
    }

    private final Supplier<ProficiencyGuardConfig> configSupplier;
    private final Supplier<DebugLogger> debugLoggerSupplier;
    private final ConcurrentHashMap<String, Long> targetCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DailyCounter> dailyCounters = new ConcurrentHashMap<>();

    public ProficiencyGuard(Supplier<ProficiencyGuardConfig> configSupplier,
            Supplier<DebugLogger> debugLoggerSupplier) {
        this.configSupplier = configSupplier;
        this.debugLoggerSupplier = debugLoggerSupplier;
    }

    public ProficiencyGuardConfig config() {
        if (configSupplier == null) {
            return ProficiencyGuardConfig.defaults();
        }
        ProficiencyGuardConfig current = configSupplier.get();
        return current == null ? ProficiencyGuardConfig.defaults() : current;
    }

    public Session session(Player actor, String trigger, String targetId) {
        return new Session(actor, normalized(trigger), normalized(targetId));
    }

    public void forget(UUID playerId) {
        if (playerId == null) {
            return;
        }
        String prefix = playerId + "|";
        targetCooldowns.keySet().removeIf(key -> key.startsWith(prefix));
        dailyCounters.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public void reset() {
        targetCooldowns.clear();
        dailyCounters.clear();
    }

    private Verdict evaluate(Session session, String definitionId, String slotName) {
        ProficiencyGuardConfig config = config();
        if (session.actor == null || !config.guards(session.trigger)) {
            return Verdict.ALLOWED;
        }
        String normalizedDefinition = normalized(definitionId);
        if (config.dedupePerEvent() && !session.dispatched.add(normalizedDefinition)) {
            return Verdict.DEDUPED;
        }
        if (config.dispatchLimited() && session.accepted >= config.maxDispatchesPerEvent()) {
            return Verdict.EVENT_LIMIT;
        }
        if (config.cooldownEnabled() && cooling(session, normalizedDefinition, config)) {
            return Verdict.TARGET_COOLDOWN;
        }
        if (config.softCapEnabled() && !passesSoftCap(session, normalizedDefinition, config)) {
            return Verdict.SOFT_CAP_DECAY;
        }
        return Verdict.ALLOWED;
    }

    private boolean cooling(Session session, String definitionId, ProficiencyGuardConfig config) {
        if (session.targetId.isBlank()) {
            return false;
        }
        String key = session.actor.getUniqueId() + "|" + session.trigger + "|" + definitionId + "|" + session.targetId;
        long now = System.currentTimeMillis();
        Long previous = targetCooldowns.get(key);
        if (previous != null && now - previous < config.sameTargetCooldownMillis()) {
            return true;
        }
        targetCooldowns.put(key, now);
        pruneCooldowns(now, config.sameTargetCooldownMillis());
        return false;
    }

    private void pruneCooldowns(long now, long window) {
        if (targetCooldowns.size() < 4096) {
            return;
        }
        targetCooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= window * 4L);
    }

    private boolean passesSoftCap(Session session, String definitionId, ProficiencyGuardConfig config) {
        String key = session.actor.getUniqueId() + "|" + session.trigger + "|" + definitionId;
        LocalDate today = LocalDate.now();
        DailyCounter counter = dailyCounters.compute(key, (ignored, current) -> current == null || !current.day.equals(today)
                ? new DailyCounter(today)
                : current);
        int count = counter.increment();
        if (count <= config.dailySoftCap()) {
            return true;
        }
        if (!config.decayEnabled()) {
            return false;
        }
        int overflow = count - config.dailySoftCap();
        double probability = Math.max(config.decayMinimumRatio(), Math.pow(config.decayFactor(), overflow));
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

    private void logRejection(Player actor, String trigger, String definitionId, String slotName, Verdict verdict) {
        DebugLogger debugLogger = debugLoggerSupplier == null ? null : debugLoggerSupplier.get();
        if (debugLogger == null || !debugLogger.shouldLog("proficiency", actor)) {
            return;
        }
        debugLogger.log("proficiency", actor, "proficiency.guard_blocked", Map.of(
                "trigger", trigger,
                "definition", normalized(definitionId),
                "slot", normalized(slotName),
                "verdict", verdict.name().toLowerCase(Locale.ROOT)));
    }

    private static String normalized(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static final class DailyCounter {

        private final LocalDate day;
        private int count;

        private DailyCounter(LocalDate day) {
            this.day = day;
        }

        private synchronized int increment() {
            count = count == Integer.MAX_VALUE ? count : count + 1;
            return count;
        }
    }
}
