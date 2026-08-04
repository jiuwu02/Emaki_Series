/**
 * Public API surface for EmakiStation, the configurable crafting-station module.
 *
 * <p>{@link emaki.jiuwu.craft.station.api.EmakiStationApi} is the only entry point. Consumers depend on
 * this jar with {@code provided} scope and must never shade or relocate it: EmakiStation ships the same
 * classes inside its own runtime jar, and a second copy under a different class identity would break the
 * static facade and Bukkit event routing.
 */
package emaki.jiuwu.craft.station.api;
