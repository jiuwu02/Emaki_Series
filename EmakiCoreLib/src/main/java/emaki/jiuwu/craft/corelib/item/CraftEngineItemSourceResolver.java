package emaki.jiuwu.craft.corelib.item;

import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.util.Key;

final class CraftEngineItemSourceResolver
        extends AbstractManagedItemSourceResolver<CraftEngineItemSourceResolver.DirectAccessor> {

    private static final String PLUGIN_NAME = "CraftEngine";

    CraftEngineItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new DirectAccessor());
    }

    CraftEngineItemSourceResolver(PluginAvailability pluginAvailability, DirectAccessor accessor) {
        super(pluginAvailability, accessor == null ? new DirectAccessor() : accessor);
    }

    @Override
    public String id() {
        return "corelib_craftengine";
    }

    @Override
    public int priority() {
        return 101;
    }

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    protected ItemSourceType sourceType() {
        return ItemSourceType.CRAFTENGINE;
    }

    @Override
    protected String waitingDetail() {
        return "CraftEngine items are not loaded yet.";
    }

    @Override
    public void registerLoadEventListener(JavaPlugin plugin, Consumer<ManagedItemSourceResolver> loadedHandler) {
        if (plugin == null || loadedHandler == null) {
            return;
        }
        plugin.getServer().getPluginManager().registerEvents(new CraftEngineReloadListener(this, loadedHandler), plugin);
    }

    static final class DirectAccessor implements AbstractManagedItemSourceResolver.Accessor {

        private String failureReason = "";

        @Override
        public boolean ensureAvailable() {
            try {
                CraftEngineItems.loadedItems();
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
                Map<Key, CustomItem<ItemStack>> items = CraftEngineItems.loadedItems();
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
                Key key = CraftEngineItems.getCustomItemId(itemStack);
                return key == null ? "" : Texts.trim(key.asString());
            } catch (RuntimeException | LinkageError exception) {
                return "";
            }
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            try {
                if (Texts.isBlank(identifier)) {
                    return null;
                }
                CustomItem<ItemStack> customItem = CraftEngineItems.byId(Key.of(identifier));
                return customItem == null
                        ? null
                        : customItem.buildItemStack(ItemBuildContext.empty(), Math.max(1, amount));
            } catch (RuntimeException | LinkageError exception) {
                return null;
            }
        }

        @Override
        public void reset() {
            failureReason = "";
        }
    }

    private static final class CraftEngineReloadListener implements Listener {

        private final ManagedItemSourceResolver resolver;
        private final Consumer<ManagedItemSourceResolver> loadedHandler;

        private CraftEngineReloadListener(ManagedItemSourceResolver resolver,
                Consumer<ManagedItemSourceResolver> loadedHandler) {
            this.resolver = resolver;
            this.loadedHandler = loadedHandler;
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onReload(CraftEngineReloadEvent event) {
            loadedHandler.accept(resolver);
        }
    }
}
