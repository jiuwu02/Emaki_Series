/**
 * Registry for optional methods callable in the installed provider version.
 *
 * <p>Providers publish only live capabilities and close the handle before uninstalling their bridge.
 * Consumers construct literal ids with {@link ApiCapability#of(String)} and keep calls inside the guard.
 */
package emaki.jiuwu.craft.corelib.api.capability;
