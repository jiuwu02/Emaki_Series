package emaki.jiuwu.craft.kits.store;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

public final class KitState {

    private final Set<String> idempotencyKeys = new LinkedHashSet<>();
    private final Map<String, Long> purchaseCounts = new LinkedHashMap<>();
    private final Map<String, Long> openCounts = new LinkedHashMap<>();
    private final Map<String, Long> claimTotals = new LinkedHashMap<>();
    private final Map<UUID, String> inviteBinds = new LinkedHashMap<>();
    private final Map<UUID, String> firstJoin = new LinkedHashMap<>();
    private final Map<String, String> selections = new LinkedHashMap<>();
    private final Map<String, AuditRecord> audits = new LinkedHashMap<>();
    private final Set<String> cdkDisabled = new LinkedHashSet<>();
    private long sequence;

    public Set<String> idempotencyKeys() {
        return idempotencyKeys;
    }

    public Map<String, Long> purchaseCounts() {
        return purchaseCounts;
    }

    public Map<String, Long> openCounts() {
        return openCounts;
    }

    public Map<String, Long> claimTotals() {
        return claimTotals;
    }

    public Map<UUID, String> inviteBinds() {
        return inviteBinds;
    }

    public Map<UUID, String> firstJoin() {
        return firstJoin;
    }

    public Map<String, String> selections() {
        return selections;
    }

    public Map<String, AuditRecord> audits() {
        return audits;
    }

    public Set<String> cdkDisabled() {
        return cdkDisabled;
    }

    public long nextSequence() {
        return ++sequence;
    }

    public void sequence(long value) {
        sequence = Math.max(sequence, value);
    }

    public long sequence() {
        return sequence;
    }

    public static String countKey(String kind, String kitId, UUID player) {
        return kind + "|" + kitId.toLowerCase(Locale.ROOT) + "|" + player;
    }

    public record AuditRecord(String id, long atMillis, UUID player, String kitId,
                              String action, String detail) {}
}
