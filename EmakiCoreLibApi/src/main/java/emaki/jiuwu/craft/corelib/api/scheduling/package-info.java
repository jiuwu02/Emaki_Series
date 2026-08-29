/**
 * Paper/Folia-safe owner-thread scheduling and cancellable task handles.
 *
 * <p>Ownership checks and submissions may be called from any thread. Async work must not touch Bukkit
 * state. Without CoreLib, ownership is false and submissions return unavailable/cancelled handles.
 */
package emaki.jiuwu.craft.corelib.api.scheduling;
