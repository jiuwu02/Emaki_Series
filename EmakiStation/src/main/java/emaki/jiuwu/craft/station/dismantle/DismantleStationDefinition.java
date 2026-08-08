package emaki.jiuwu.craft.station.dismantle;

import emaki.jiuwu.craft.corelib.condition.ConditionBlock;
import emaki.jiuwu.craft.station.api.model.OutputRouting;

/**
 * One loaded dismantle station.
 *
 * <p>A dismantle station is fully independent of the crafting station: it has its own id, layout,
 * and output routing, loaded from {@code stations_dismantle/}.
 *
 * @param id            the station id, which is also its file name stem
 * @param displayName   the configured display name, unrendered
 * @param layoutId      the GUI layout to open for this dismantle station
 * @param permission    the access permission, or an empty string to skip the check
 * @param outputRouting the default output destination when a dismantle is confirmed
 * @param condition     the gate evaluated before the GUI opens
 */
public record DismantleStationDefinition(
        String id,
        String displayName,
        String layoutId,
        String permission,
        OutputRouting outputRouting,
        ConditionBlock condition) {

    /** Layout id used when a station declares no {@code layout}. */
    public static final String DEFAULT_LAYOUT = "station_dismantle";

    /**
     * Creates a definition with null-safe defaults.
     *
     * @param id            the station id; must not be null or blank
     * @param displayName   the display name; {@code null} becomes the id
     * @param layoutId      the layout id; {@code null} or blank becomes {@link #DEFAULT_LAYOUT}
     * @param permission    the access permission; {@code null} becomes an empty string
     * @param outputRouting the output destination; {@code null} becomes
     *                      {@link OutputRouting#STORAGE_FIRST}
     * @param condition     the open gate; {@code null} becomes an empty block
     */
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

    /** {@return whether this station declares its own access permission} */
    public boolean hasOwnPermission() {
        return !permission.isBlank();
    }

    /**
     * Resolves the permission a player needs to open this dismantle station.
     *
     * @param fallback the node to require when this station declares none
     * @return the station's own node when configured, otherwise {@code fallback}
     */
    public String effectivePermission(String fallback) {
        if (hasOwnPermission()) {
            return permission;
        }
        return fallback == null ? "" : fallback;
    }
}
