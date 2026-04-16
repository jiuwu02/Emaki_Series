package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveNeigeItemsItemSourceResolver
        extends AbstractManagedItemSourceResolver<ReflectiveNeigeItemsItemSourceResolver.ReflectiveAccessor> {

    private static final String PLUGIN_NAME = "NeigeItems";
    private static final String LOAD_EVENT_CLASS = "pers.neige.neigeitems.event.PluginReloadEvent$Post";

    ReflectiveNeigeItemsItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new ReflectiveAccessor());
    }

    ReflectiveNeigeItemsItemSourceResolver(PluginAvailability pluginAvailability, ReflectiveAccessor accessor) {
        super(pluginAvailability, accessor == null ? new ReflectiveAccessor() : accessor);
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
    public String loadEventClassName() {
        return LOAD_EVENT_CLASS;
    }

    @Override
    protected ItemSourceType sourceType() {
        return ItemSourceType.NEIGEITEMS;
    }

    @Override
    protected String waitingDetail() {
        return "NeigeItems items are not loaded yet.";
    }

    static final class ReflectiveAccessor extends AbstractReflectiveAccessor
            implements AbstractManagedItemSourceResolver.Accessor {

        private Field itemManagerInstanceField;
        private Method getItemsMethod;
        private Method getItemIdMethod;
        private Method getItemStackMethod;

        @Override
        protected void initializeBindings() throws Exception {
            Class<?> itemManagerClass = Class.forName("pers.neige.neigeitems.manager.ItemManager");
            itemManagerInstanceField = itemManagerClass.getField("INSTANCE");
            getItemsMethod = itemManagerClass.getMethod("getItems");
            getItemIdMethod = itemManagerClass.getMethod("getItemId", ItemStack.class);
            getItemStackMethod = itemManagerClass.getMethod("getItemStack", String.class);
        }

        @Override
        protected void resetBindings() {
            itemManagerInstanceField = null;
            getItemsMethod = null;
            getItemIdMethod = null;
            getItemStackMethod = null;
        }

        @Override
        public boolean detectLoaded() {
            if (!ensureAvailable()) {
                return false;
            }
            Object items = invoke(getItemsMethod, instance());
            return items instanceof Map<?, ?>;
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            return Texts.trim(invoke(getItemIdMethod, instance(), itemStack));
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            return asItemStack(invoke(getItemStackMethod, instance(), identifier));
        }

        private Object instance() {
            return readStaticField(itemManagerInstanceField);
        }
    }
}
