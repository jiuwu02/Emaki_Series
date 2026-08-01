/**
 * Types shared by the pipeline extension point in {@link emaki.jiuwu.craft.corelib.api.action.v2}.
 *
 * <h2>Scope</h2>
 * These three types sit outside the {@code v2} package because they are not tied to one stage role:
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain} and
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget} are declared by sources, gates
 * and actions alike, and {@link emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget} is a plain
 * holder a caller passes through a pipeline and reads back afterwards.
 *
 * <h2>Where to start</h2>
 * Implement {@link emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage} and register it with
 * {@code EmakiCoreLibApi.registerActionStage}. Nothing in this package is meant to be implemented.
 *
 * <h2>Threading</h2>
 * Declare {@link emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget} honestly: EmakiCoreLib
 * routes a stage to a thread or region based on it, and on Folia a wrong declaration is refused rather
 * than silently tolerated.
 */
package emaki.jiuwu.craft.corelib.api.action;
