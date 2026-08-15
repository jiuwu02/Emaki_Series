package emaki.jiuwu.craft.corelib.item;

import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceProvider;

interface ManagedItemSourceProvider extends ItemSourceProvider {

    default void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceProvider> loadedHandler) {

    }
}
