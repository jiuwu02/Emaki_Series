package emaki.jiuwu.craft.corelib.api;

/**
 * Public service handle for the shared EmakiCoreLib runtime core (YAML, messages, actions, PDC, item sources, economy, scripting, web console, event bus).
 *
 * <p>Registered with the Bukkit {@code ServicesManager} by EmakiCoreLib once it is
 * enabled. Obtain it through {@link EmakiCoreLibApiProvider#get()}. The interface
 * intentionally exposes only lightweight readiness/identity probes; richer
 * behavior is offered through the plugin's other registered services.
 */
public interface EmakiCoreLibApi {

    /** {@return the semantic version string of this API} */
    String apiVersion();

    /** {@return the owning plugin's name} */
    String pluginName();

    /** {@return whether the plugin has finished initializing and is usable} */
    boolean isReady();
}
