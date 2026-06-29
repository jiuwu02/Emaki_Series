package emaki.jiuwu.craft.corelib.action.loop;

import java.util.UUID;

public record LoopTaskSnapshot(
        String id,
        String key,
        String template,
        String plugin,
        UUID playerUuid,
        boolean async,
        int index,
        int times,
        long intervalTicks
) {
}
