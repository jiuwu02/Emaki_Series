package emaki.jiuwu.craft.corelib.item;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent;
import io.th0rgal.oraxen.items.ItemBuilder;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class OraxenItemSourceResolver
        extends AbstractManagedItemSourceProvider<OraxenItemSourceResolver.DirectAccessor> {

    private static final String PLUGIN_NAME = "Oraxen";

    OraxenItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new DirectAccessor());
    }

    OraxenItemSourceResolver(PluginAvailability pluginAvailability, DirectAccessor accessor) {
        super(pluginAvailability, accessor == null ? new DirectAccessor() : accessor);
    }

    @Override
    public ItemSourceKind kind() {
        return ItemSourceKind.ORAXEN;
    }

    @Override
    public Set<String> shorthandPrefixes() {
        return Set.of("oraxen-", "ox-");
    }

    @Override
    public int priority() {
        return 96;
    }

    @Override
    public String providerPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected String waitingDetail() {
        return "Oraxen items are not loaded yet.";
    }

    @Override
    public void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceProvider> loadedHandler) {
        if (plugin == null || loadedHandler == null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(new OraxenLoadListener(this, loadedHandler), plugin);
    }

    static final class DirectAccessor implements AbstractManagedItemSourceProvider.Accessor {

        private String failureReason = "";

        @Override
        public boolean ensureAvailable() {
            try {
                OraxenItems.getEntriesAsMap();
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
                Map<String, ItemBuilder> entries = OraxenItems.getEntriesAsMap();
                return entries != null && !entries.isEmpty();
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
                return Texts.trim(OraxenItems.getIdByItem(itemStack));
            } catch (RuntimeException | LinkageError exception) {
                return "";
            }
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            try {
                ItemBuilder itemBuilder = Texts.isBlank(identifier) ? null : OraxenItems.getItemById(identifier);
                return itemBuilder == null ? null : itemBuilder.build();
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public String displayName(String identifier) {
            try {
                ItemBuilder itemBuilder = Texts.isBlank(identifier) ? null : OraxenItems.getItemById(identifier);
                if (itemBuilder == null) {
                    return null;
                }
                String displayName = itemBuilder.getDisplayName();
                if (Texts.isNotBlank(displayName)) {
                    return displayName;
                }
                return itemBuilder.getItemName();
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public void reset() {
            failureReason = "";
        }
    }

    private static final class OraxenLoadListener implements Listener {

        private final ManagedItemSourceProvider resolver;
        private final Consumer<ManagedItemSourceProvider> loadedHandler;

        private OraxenLoadListener(ManagedItemSourceProvider resolver,
                Consumer<ManagedItemSourceProvider> loadedHandler) {
            this.resolver = resolver;
            this.loadedHandler = loadedHandler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onItemsLoaded(OraxenItemsLoadedEvent event) {
            loadedHandler.accept(resolver);
        }
    }
}
