/**
 * Action pipeline v2: register sources, gates and actions into EmakiCoreLib's single stage registry.
 *
 * <h2>Model</h2>
 * A pipeline is one line of text, {@code [source] | [gate]* | [action]+}. Data flows left to right;
 * reading order equals execution order. The three roles are
 * {@link emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSource},
 * {@link emaki.jiuwu.craft.corelib.api.action.v2.CoreActionGate} and
 * {@link emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage}.
 *
 * <h2>Stability</h2>
 * These types coexist with the v1 {@code emaki.jiuwu.craft.corelib.api.action} package during the
 * migration. v1 will be removed once every module has moved; do not build new integrations on it.
 *
 * <h2>Threading</h2>
 * Sources and actions must declare {@code executionTarget(...)}; there is no default, because an
 * undeclared domain is a crash on Folia rather than an error. Gates default to
 * {@link emaki.jiuwu.craft.corelib.api.action.v2.CoreGateThread#PURE} and may widen it. A stage
 * declaring the async domain must declare
 * {@link emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement#NONE}, or CoreLib refuses the
 * registration.
 *
 * <h2>Lifecycle</h2>
 * Keep the {@link emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration} handle and close it
 * in {@code onDisable}. Registering an id that is already taken fails and names the first owner; it
 * never silently overwrites.
 *
 * <h2>Degradation</h2>
 * When EmakiCoreLib is absent, registration returns an inactive handle from
 * {@link emaki.jiuwu.craft.corelib.api.action.v2.CoreStageRegistration#unavailable}.
 */
package emaki.jiuwu.craft.corelib.api.action.v2;
