package emaki.jiuwu.craft.corelib.item;

import java.util.function.Consumer;

import org.bukkit.plugin.java.JavaPlugin;

interface ManagedItemSourceResolver extends ItemSourceResolver {

    String pluginName();

    Status bootstrap();

    Status onPluginEnabled();

    Status onItemsLoaded();

    void onPluginDisabled();

    default void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceResolver> loadedHandler) {
    }

    record Status(State state, String detail) {

        public Status {
            detail = detail == null ? "" : detail.trim();
        }
    }

    enum State {
        ABSENT,
        WAITING,
        READY,
        INCOMPATIBLE
    }
}
