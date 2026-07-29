/**
 * Folia-safe scheduling view for third-party plugins.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling} and
 * {@link emaki.jiuwu.craft.corelib.api.scheduling.TaskToken} are
 * {@link org.jetbrains.annotations.ApiStatus.NonExtendable}; EmakiCoreLib supplies the only
 * implementations. This view is intentionally narrower than EmakiCoreLib's internal dispatcher and
 * will not be widened to mirror it.
 *
 * <h2>Threading</h2>
 * The three {@code owns*} predicates may be called from any thread and describe the caller's own
 * thread. The {@code run*} methods may be called from any thread; each routes the task to the right
 * owner thread. Never touch Bukkit state from a task submitted through {@code runAsync}.
 *
 * <h2>Degradation</h2>
 * When EmakiCoreLib is absent, {@code owns*} return {@code false}, nothing is scheduled, and every
 * {@code run*} returns {@link emaki.jiuwu.craft.corelib.api.scheduling.TaskToken#UNAVAILABLE}, whose
 * {@code cancelled()} is already {@code true}. Callers that store tokens therefore need no null
 * checks.
 */
package emaki.jiuwu.craft.corelib.api.scheduling;
