package emaki.jiuwu.craft.dungeon.party;

import java.util.UUID;

public final class PendingEntry {

    public enum Kind {
        INVITE,
        APPLY
    }

    private final Kind kind;
    private final UUID fromId;
    private final UUID partyId;
    private final long expiresAtMillis;

    public PendingEntry(Kind kind, UUID fromId, UUID partyId, long expiresAtMillis) {
        this.kind = kind;
        this.fromId = fromId;
        this.partyId = partyId;
        this.expiresAtMillis = expiresAtMillis;
    }

    public Kind kind() {
        return kind;
    }

    public UUID fromId() {
        return fromId;
    }

    public UUID partyId() {
        return partyId;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean expired(long nowMillis) {
        return nowMillis > expiresAtMillis;
    }
}
