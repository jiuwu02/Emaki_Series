package emaki.jiuwu.craft.corelib.item;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.ItemsAdder;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class ItemsAdderItemSourceResolver
        extends AbstractManagedItemSourceProvider<ItemsAdderItemSourceResolver.DirectAccessor> {

    private static final String PLUGIN_NAME = "ItemsAdder";

    ItemsAdderItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new DirectAccessor());
    }

    ItemsAdderItemSourceResolver(PluginAvailability pluginAvailability, DirectAccessor accessor) {
        super(pluginAvailability, accessor == null ? new DirectAccessor() : accessor);
    }

    @Override
    public ItemSourceKind kind() {
        return ItemSourceKind.ITEMSADDER;
    }

    @Override
    public Set<String> shorthandPrefixes() {
        return Set.of("itemsadder-", "ia-");
    }

    @Override
    public int priority() {
        return 98;
    }

    @Override
    public String providerPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected String waitingDetail() {
        return "ItemsAdder items are not loaded yet.";
    }

    @Override
    public void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceProvider> loadedHandler) {
        if (plugin == null || loadedHandler == null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(new ItemsAdderLoadListener(this, loadedHandler), plugin);
    }

    static final class DirectAccessor implements AbstractManagedItemSourceProvider.Accessor {

        private String failureReason = "";

        @SuppressWarnings("deprecation")
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

        @SuppressWarnings("deprecation")
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
        public String displayName(String identifier) {
            try {
                CustomStack customStack = Texts.isBlank(identifier) ? null : CustomStack.getInstance(identifier);
                if (customStack == null) {
                    return null;
                }
                String displayName = customStack.getDisplayName();
                if (Texts.isNotBlank(displayName)) {
                    return displayName;
                }
                return MiniMessages.serialize(customStack.itemName());
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

        private final ManagedItemSourceProvider resolver;
        private final Consumer<ManagedItemSourceProvider> loadedHandler;

        private ItemsAdderLoadListener(ManagedItemSourceProvider resolver,
                Consumer<ManagedItemSourceProvider> loadedHandler) {
            this.resolver = resolver;
            this.loadedHandler = loadedHandler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onItemsLoaded(ItemsAdderLoadDataEvent event) {
            loadedHandler.accept(resolver);
        }
    }
}
