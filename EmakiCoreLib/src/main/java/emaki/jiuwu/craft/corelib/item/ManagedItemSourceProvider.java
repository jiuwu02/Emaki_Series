package emaki.jiuwu.craft.corelib.item;

import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider;

/**
 * Extra hooks CoreLib's own third-party bridges need, kept out of the public
 * {@link ItemSourceProvider} contract.
 *
 * <p>{@link #registerLoadEventListener} exists because each bridged plugin signals "items are loaded"
 * through its own event type, which is knowledge only the bridge has. A third-party provider does not
 * need it: it can call {@code onProviderReady(true)} on itself whenever it likes.
 */
interface ManagedItemSourceProvider extends ItemSourceProvider {

    /**
     * Subscribes to the backing plugin's own "items loaded" event, if it has one.
     *
     * @param plugin the CoreLib plugin instance to register listeners against
     * @param loadedHandler callback to invoke once items are loaded
     */
    default void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceProvider> loadedHandler) {
        // Most bridges have no load event and rely on detection instead.
    }
}
