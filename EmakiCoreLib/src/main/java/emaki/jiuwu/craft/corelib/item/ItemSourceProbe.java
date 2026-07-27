package emaki.jiuwu.craft.corelib.item;

import java.util.Objects;

public record ItemSourceProbe(
        ItemSourceProbeStatus status,
        ItemSource source,
        String resolverId,
        String detail) {

    public ItemSourceProbe {
        Objects.requireNonNull(status, "status");
        resolverId = resolverId == null ? "" : resolverId.trim();
        detail = detail == null ? "" : detail.trim();
    }

    public boolean ready() {
        return status == ItemSourceProbeStatus.READY;
    }

    public static ItemSourceProbe of(ItemSourceProbeStatus status, ItemSource source, String resolverId, String detail) {
        return new ItemSourceProbe(status, source, resolverId, detail);
    }

    public static ItemSourceProbe ready(ItemSource source, String resolverId) {
        return of(ItemSourceProbeStatus.READY, source, resolverId, "");
    }
}
