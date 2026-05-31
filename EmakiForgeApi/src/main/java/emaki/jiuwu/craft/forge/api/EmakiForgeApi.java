package emaki.jiuwu.craft.forge.api;

/**
 * Public service handle for the EmakiForge forging system.
 *
 * <p>Registered with the Bukkit {@code ServicesManager} by EmakiForge once it is
 * enabled. Obtain it through {@link EmakiForgeApiProvider#get()}. The interface
 * intentionally exposes only lightweight readiness/identity probes; richer
 * behavior is offered through the plugin's other registered services.
 */
public interface EmakiForgeApi {

    /** {@return the semantic version string of this API} */
    String apiVersion();

    /** {@return the owning plugin's name} */
    String pluginName();

    /** {@return whether the plugin has finished initializing and is usable} */
    boolean isReady();
}
