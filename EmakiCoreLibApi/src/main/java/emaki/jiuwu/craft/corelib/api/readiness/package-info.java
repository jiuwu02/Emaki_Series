/**
 * Readiness registry for asynchronously loaded Emaki modules.
 *
 * <p>{@code whenReady} fires once and runs synchronously when already ready. A standing listener
 * follows every {@code LOADING}, {@code READY}, and {@code ABSENT} transition, replacing the same
 * owner/module registration. Callbacks run on the publishing thread; schedule before touching Bukkit
 * state and close handles on disable. Without CoreLib, registrations are inactive and readiness is false.
 */
package emaki.jiuwu.craft.corelib.api.readiness;
