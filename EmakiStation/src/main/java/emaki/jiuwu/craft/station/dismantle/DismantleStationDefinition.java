package emaki.jiuwu.craft.station.dismantle;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.station.api.model.OutputRouting;

public record DismantleStationDefinition(
        String id,
        String displayName,
        String layoutId,
        String permission,
        OutputRouting outputRouting,
        ConditionBlock condition) {

    public static final String DEFAULT_LAYOUT = "station_dismantle";

    public DismantleStationDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("DismantleStationDefinition id must not be blank");
        }
        displayName = displayName == null ? id : displayName;
        layoutId = layoutId == null || layoutId.isBlank() ? DEFAULT_LAYOUT : layoutId;
        permission = permission == null ? "" : permission;
        outputRouting = outputRouting == null ? OutputRouting.STORAGE_FIRST : outputRouting;
        condition = condition == null ? ConditionBlock.empty() : condition;
    }

    public boolean hasOwnPermission() {
        return !permission.isBlank();
    }

    public String effectivePermission(String fallback) {
        if (hasOwnPermission()) {
            return permission;
        }
        return fallback == null ? "" : fallback;
    }
}
