package emaki.jiuwu.craft.corelib.api.data;

import java.util.Arrays;

/**
 * Immutable snapshot of one player-domain payload as stored by {@link PlayerDomainRepository}.
 *
 * @param revision optimistic-lock revision of the stored row; {@code 0} means no row exists
 * @param schemaVersion payload format version recorded with the row
 * @param payload serialized domain payload; never {@code null}
 */
public record DomainSnapshot(long revision, int schemaVersion, byte[] payload) {

    public DomainSnapshot {
        payload = payload == null ? new byte[0] : Arrays.copyOf(payload, payload.length);
    }

    @Override
    public byte[] payload() {
        return Arrays.copyOf(payload, payload.length);
    }
}
