/**
 * Action extension point: register your own actions into EmakiCoreLib's shared action registry.
 *
 * <h2>Stability</h2>
 * Stable. {@link emaki.jiuwu.craft.corelib.api.action.CoreAction} is the one interface third-party
 * plugins are expected to implement; every other type here is a value object produced by
 * EmakiCoreLib.
 *
 * <h2>Threading</h2>
 * Registration and every descriptor query may be called from any thread. Action execution itself
 * happens on the thread implied by the action's declared
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionMode} and
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain}; declare these honestly,
 * because EmakiCoreLib routes based on them.
 *
 * <h2>Lifecycle</h2>
 * Keep the {@link emaki.jiuwu.craft.corelib.api.action.CoreActionRegistration} handle and close it in
 * your {@code onDisable}, or call {@code EmakiCoreLibApi.unregisterActions(Plugin)}. Registrations
 * that outlive their owner leak across reloads.
 *
 * <h2>Degradation</h2>
 * When EmakiCoreLib is absent, registration returns an unavailable
 * {@link emaki.jiuwu.craft.corelib.api.action.CoreActionRegistration} and every query returns an
 * empty list or optional.
 */
package emaki.jiuwu.craft.corelib.api.action;
