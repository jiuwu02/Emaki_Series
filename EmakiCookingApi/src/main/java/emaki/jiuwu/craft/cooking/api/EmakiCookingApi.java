package emaki.jiuwu.craft.cooking.api;

/**
 * Public service handle for the EmakiCooking cooking-station system.
 *
 * <p>Registered with the Bukkit {@code ServicesManager} by EmakiCooking once it is
 * enabled. Obtain it through {@link EmakiCookingApiProvider#get()}. The interface
 * intentionally exposes only lightweight readiness/identity probes; richer
 * behavior is offered through the plugin's other registered services.
 */
public interface EmakiCookingApi {

    /** {@return the semantic version string of this API} */
    String apiVersion();

    /** {@return the owning plugin's name} */
    String pluginName();

    /** {@return whether the plugin has finished initializing and is usable} */
    boolean isReady();
}
