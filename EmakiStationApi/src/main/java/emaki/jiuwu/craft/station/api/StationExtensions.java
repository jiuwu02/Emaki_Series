package emaki.jiuwu.craft.station.api;

import org.jetbrains.annotations.ApiStatus;

/**
 * Reserved extension surface for EmakiStation.
 *
 * <p>Intentionally empty in 1.0. The facade shape is kept consistent with the other Emaki modules so
 * a later extension point can be added without changing how callers reach it, but no extension
 * contract is invented before a real consumer exists.
 */
@ApiStatus.NonExtendable
public interface StationExtensions {
}
