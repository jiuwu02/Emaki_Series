package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveItemsAdderItemSourceResolver
        extends AbstractManagedItemSourceResolver<ReflectiveItemsAdderItemSourceResolver.ReflectiveAccessor> {

    private static final String PLUGIN_NAME = "ItemsAdder";
    private static final String LOAD_EVENT_CLASS = "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent";

    ReflectiveItemsAdderItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new ReflectiveAccessor());
    }

    ReflectiveItemsAdderItemSourceResolver(PluginAvailability pluginAvailability, ReflectiveAccessor accessor) {
        super(pluginAvailability, accessor == null ? new ReflectiveAccessor() : accessor);
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
    public String loadEventClassName() {
        return LOAD_EVENT_CLASS;
    }

    @Override
    protected ItemSourceType sourceType() {
        return ItemSourceType.ITEMSADDER;
    }

    @Override
    protected String waitingDetail() {
        return "ItemsAdder items are not loaded yet.";
    }

    static final class ReflectiveAccessor extends AbstractReflectiveAccessor
            implements AbstractManagedItemSourceResolver.Accessor {

        private Method customStackByItemStackMethod;
        private Method customStackGetInstanceMethod;
        private Method customStackGetItemStackMethod;
        private Method customStackGetNamespacedIdMethod;
        private Method itemsAdderAreItemsLoadedMethod;
        private Method itemsAdderGetAllItemsMethod;

        @Override
        protected void initializeBindings() throws Exception {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            customStackByItemStackMethod = customStackClass.getMethod("byItemStack", ItemStack.class);
            customStackGetInstanceMethod = customStackClass.getMethod("getInstance", String.class);
            customStackGetItemStackMethod = customStackClass.getMethod("getItemStack");
            customStackGetNamespacedIdMethod = customStackClass.getMethod("getNamespacedID");
            Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
            itemsAdderAreItemsLoadedMethod = getOptionalMethod(itemsAdderClass, "areItemsLoaded");
            itemsAdderGetAllItemsMethod = getOptionalMethod(itemsAdderClass, "getAllItems");
        }

        @Override
        protected void resetBindings() {
            customStackByItemStackMethod = null;
            customStackGetInstanceMethod = null;
            customStackGetItemStackMethod = null;
            customStackGetNamespacedIdMethod = null;
            itemsAdderAreItemsLoadedMethod = null;
            itemsAdderGetAllItemsMethod = null;
        }

        @Override
        public boolean detectLoaded() {
            if (!ensureAvailable()) {
                return false;
            }
            Object loaded = invoke(itemsAdderAreItemsLoadedMethod, null);
            if (loaded instanceof Boolean bool) {
                return bool;
            }
            Object items = invoke(itemsAdderGetAllItemsMethod, null);
            return items instanceof List<?>;
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            Object customStack = invoke(customStackByItemStackMethod, null, itemStack);
            return Texts.trim(invoke(customStackGetNamespacedIdMethod, customStack));
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            Object customStack = invoke(customStackGetInstanceMethod, null, identifier);
            return asItemStack(invoke(customStackGetItemStackMethod, customStack));
        }
    }
}
