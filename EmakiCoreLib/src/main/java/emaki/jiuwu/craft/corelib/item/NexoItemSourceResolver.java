package emaki.jiuwu.craft.corelib.item;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.items.ItemBuilder;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceKind;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class NexoItemSourceResolver
        extends AbstractManagedItemSourceProvider<NexoItemSourceResolver.DirectAccessor> {

    private static final String PLUGIN_NAME = "Nexo";

    NexoItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new DirectAccessor());
    }

    NexoItemSourceResolver(PluginAvailability pluginAvailability, DirectAccessor accessor) {
        super(pluginAvailability, accessor == null ? new DirectAccessor() : accessor);
    }

    @Override
    public ItemSourceKind kind() {
        return ItemSourceKind.NEXO;
    }

    @Override
    public Set<String> shorthandPrefixes() {
        return Set.of("nexo-", "no-");
    }

    @Override
    public int priority() {
        return 97;
    }

    @Override
    public String providerPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected String waitingDetail() {
        return "Nexo items are not loaded yet.";
    }

    @Override
    public void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceProvider> loadedHandler) {
        if (plugin == null || loadedHandler == null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(new NexoLoadListener(this, loadedHandler), plugin);
    }

    static final class DirectAccessor implements AbstractManagedItemSourceProvider.Accessor {

        private String failureReason = "";

        @Override
        public boolean ensureAvailable() {
            try {
                NexoItems.entries();
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
                Map<String, ItemBuilder> entries = NexoItems.entries();
                return entries != null;
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
                return Texts.trim(NexoItems.idFromItem(itemStack));
            } catch (RuntimeException | LinkageError exception) {
                return "";
            }
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            try {
                ItemBuilder itemBuilder = Texts.isBlank(identifier) ? null : NexoItems.itemFromId(identifier);
                return itemBuilder == null ? null : itemBuilder.build();
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public String displayName(String identifier) {
            try {
                ItemBuilder itemBuilder = Texts.isBlank(identifier) ? null : NexoItems.itemFromId(identifier);
                if (itemBuilder == null) {
                    return null;
                }
                String customName = MiniMessages.serialize(itemBuilder.getCustomName());
                if (Texts.isNotBlank(customName)) {
                    return customName;
                }
                return MiniMessages.serialize(itemBuilder.getItemName());
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public void reset() {
            failureReason = "";
        }
    }

    private static final class NexoLoadListener implements Listener {

        private final ManagedItemSourceProvider resolver;
        private final Consumer<ManagedItemSourceProvider> loadedHandler;

        private NexoLoadListener(ManagedItemSourceProvider resolver,
                Consumer<ManagedItemSourceProvider> loadedHandler) {
            this.resolver = resolver;
            this.loadedHandler = loadedHandler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onItemsLoaded(NexoItemsLoadedEvent event) {
            loadedHandler.accept(resolver);
        }
    }
}
