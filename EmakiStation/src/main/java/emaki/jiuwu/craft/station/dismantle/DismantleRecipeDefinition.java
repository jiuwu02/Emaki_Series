package emaki.jiuwu.craft.station.dismantle;

import java.util.List;
import java.util.Set;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.condition.ConditionBlock;

public record DismantleRecipeDefinition(
        String id,
        String displayName,
        String stationId,
        Set<String> tags,
        ItemSourceRef inputSource,
        RollsRange rolls,
        List<DismantlePoolEntry> pool,
        String permission,
        ConditionBlock condition) {

    public DismantleRecipeDefinition {
        if (id == null) {
            throw new NullPointerException("id");
        }
        if (inputSource == null) {
            throw new NullPointerException("inputSource");
        }
        if (rolls == null) {
            throw new NullPointerException("rolls");
        }
        if (pool == null) {
            throw new NullPointerException("pool");
        }
        displayName = displayName == null ? id : displayName;
        stationId = stationId == null ? "" : stationId;
        tags = tags == null ? Set.of() : Set.copyOf(tags);
        pool = List.copyOf(pool);
        permission = permission == null ? "" : permission;
        condition = condition == null ? ConditionBlock.empty() : condition;
    }

    public boolean hasPermission() {
        return !permission.isBlank();
    }

    public boolean hasScopedStation() {
        return !stationId.isBlank();
    }
}
