package emaki.jiuwu.craft.corelib.item;

import java.util.List;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.ItemsAdder;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import emaki.jiuwu.craft.corelib.text.Texts;

final class ItemsAdderItemSourceResolver
        extends AbstractManagedItemSourceResolver<ItemsAdderItemSourceResolver.DirectAccessor> {

    private static final String PLUGIN_NAME = "ItemsAdder";

    ItemsAdderItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new DirectAccessor());
    }

    ItemsAdderItemSourceResolver(PluginAvailability pluginAvailability, DirectAccessor accessor) {
        super(pluginAvailability, accessor == null ? new DirectAccessor() : accessor);
    }

    @Override
    public String id() {
        return "corelib_itemsadder";
    }

    @Override
    public int priority() {
        return 98;
    }

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected ItemSourceType sourceType() {
        return ItemSourceType.ITEMSADDER;
    }

    @Override
    protected String waitingDetail() {
        return "ItemsAdder items are not loaded yet.";
    }

    @Override
    public void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceResolver> loadedHandler) {
        if (plugin == null || loadedHandler == null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(new ItemsAdderLoadListener(this, loadedHandler), plugin);
    }

    static final class DirectAccessor implements AbstractManagedItemSourceResolver.Accessor {

        private String failureReason = "";

        @Override
        public boolean ensureAvailable() {
            try {
                ItemsAdder.areItemsLoaded();
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
                if (ItemsAdder.areItemsLoaded()) {
                    return true;
                }
                List<CustomStack> items = ItemsAdder.getAllItems();
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
                CustomStack customStack = CustomStack.byItemStack(itemStack);
                return customStack == null ? "" : Texts.trim(customStack.getNamespacedID());
            } catch (RuntimeException | LinkageError exception) {
                return "";
            }
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            try {
                CustomStack customStack = Texts.isBlank(identifier) ? null : CustomStack.getInstance(identifier);
                return customStack == null ? null : customStack.getItemStack();
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public void reset() {
            failureReason = "";
        }
    }

    private static final class ItemsAdderLoadListener implements Listener {

        private final ManagedItemSourceResolver resolver;
        private final Consumer<ManagedItemSourceResolver> loadedHandler;

        private ItemsAdderLoadListener(ManagedItemSourceResolver resolver,
                Consumer<ManagedItemSourceResolver> loadedHandler) {
            this.resolver = resolver;
            this.loadedHandler = loadedHandler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onItemsLoaded(ItemsAdderLoadDataEvent event) {
            loadedHandler.accept(resolver);
        }
    }
}
