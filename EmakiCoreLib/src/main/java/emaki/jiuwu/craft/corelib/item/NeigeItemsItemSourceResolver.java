package emaki.jiuwu.craft.corelib.item;

import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import pers.neige.neigeitems.event.PluginReloadEvent;
import pers.neige.neigeitems.manager.ItemManager;

final class NeigeItemsItemSourceResolver
        extends AbstractManagedItemSourceResolver<NeigeItemsItemSourceResolver.DirectAccessor> {

    private static final String PLUGIN_NAME = "NeigeItems";

    NeigeItemsItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new DirectAccessor());
    }

    NeigeItemsItemSourceResolver(PluginAvailability pluginAvailability, DirectAccessor accessor) {
        super(pluginAvailability, accessor == null ? new DirectAccessor() : accessor);
    }

    @Override
    public String id() {
        return "corelib_neigeitems";
    }

    @Override
    public int priority() {
        return 102;
    }

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected ItemSourceType sourceType() {
        return ItemSourceType.NEIGEITEMS;
    }

    @Override
    protected String waitingDetail() {
        return "NeigeItems items are not loaded yet.";
    }

    @Override
    public void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceResolver> loadedHandler) {
        if (plugin == null || loadedHandler == null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(new NeigeItemsLoadListener(this, loadedHandler), plugin);
    }

    static final class DirectAccessor implements AbstractManagedItemSourceResolver.Accessor {

        private String failureReason = "";

        @Override
        public boolean ensureAvailable() {
            try {
                ItemManager.INSTANCE.getItems();
                failureReason = "";
                return true;
            } catch (RuntimeException | LinkageError exception) {
                failureReason = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                return false;
            }
        }

        @Override
        public String failureReason() {
            return failureReason;
        }

        @Override
        public boolean detectLoaded() {
            try {
                Map<?, ?> items = ItemManager.INSTANCE.getItems();
                return items != null;
            } catch (RuntimeException | LinkageError exception) {
                failureReason = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                return false;
            }
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            try {
                return Texts.trim(ItemManager.INSTANCE.getItemId(itemStack));
            } catch (RuntimeException | LinkageError exception) {
                return "";
            }
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            try {
                return Texts.isBlank(identifier) ? null : ItemManager.INSTANCE.getItemStack(identifier);
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public void reset() {
            failureReason = "";
        }
    }

    private static final class NeigeItemsLoadListener implements Listener {

        private final ManagedItemSourceResolver resolver;
        private final Consumer<ManagedItemSourceResolver> loadedHandler;

        private NeigeItemsLoadListener(ManagedItemSourceResolver resolver,
                Consumer<ManagedItemSourceResolver> loadedHandler) {
            this.resolver = resolver;
            this.loadedHandler = loadedHandler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onItemsLoaded(PluginReloadEvent.Post event) {
            loadedHandler.accept(resolver);
        }
    }
}
