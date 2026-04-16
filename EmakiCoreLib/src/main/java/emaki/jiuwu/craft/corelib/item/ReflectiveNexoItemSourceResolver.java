package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Method;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveNexoItemSourceResolver
        extends AbstractManagedItemSourceResolver<ReflectiveNexoItemSourceResolver.ReflectiveAccessor> {

    private static final String PLUGIN_NAME = "Nexo";
    private static final String LOAD_EVENT_CLASS = "com.nexomc.nexo.api.events.NexoItemsLoadedEvent";

    ReflectiveNexoItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new ReflectiveAccessor());
    }

    ReflectiveNexoItemSourceResolver(PluginAvailability pluginAvailability, ReflectiveAccessor accessor) {
        super(pluginAvailability, accessor == null ? new ReflectiveAccessor() : accessor);
    }

    @Override
    public String id() {
        return "corelib_nexo";
    }

    @Override
    public int priority() {
        return 97;
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
        return ItemSourceType.NEXO;
    }

    @Override
    protected String waitingDetail() {
        return "Nexo items are not loaded yet.";
    }

    static final class ReflectiveAccessor extends AbstractReflectiveAccessor
            implements AbstractManagedItemSourceResolver.Accessor {

        private Method itemFromIdMethod;
        private Method idFromItemMethod;
        private Method entriesMethod;
        private Method itemBuilderBuildMethod;

        @Override
        protected void initializeBindings() throws Exception {
            Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
            itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
            idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
            entriesMethod = getOptionalMethod(nexoItemsClass, "entries");

            Class<?> itemBuilderClass = Class.forName("com.nexomc.nexo.items.ItemBuilder");
            itemBuilderBuildMethod = resolveBuildMethod(itemBuilderClass);
        }

        @Override
        protected void resetBindings() {
            itemFromIdMethod = null;
            idFromItemMethod = null;
            entriesMethod = null;
            itemBuilderBuildMethod = null;
        }

        @Override
        public boolean detectLoaded() {
            if (!ensureAvailable()) {
                return false;
            }
            Object entries = invoke(entriesMethod, null);
            return entries instanceof Map<?, ?>;
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            return Texts.trim(invoke(idFromItemMethod, null, itemStack));
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            Object itemBuilder = invoke(itemFromIdMethod, null, identifier);
            return asItemStack(invoke(itemBuilderBuildMethod, itemBuilder));
        }

        private Method resolveBuildMethod(Class<?> itemBuilderClass) throws NoSuchMethodException {
            Method method = getOptionalMethod(itemBuilderClass, "build");
            return method == null ? itemBuilderClass.getMethod("getFinalItemStack") : method;
        }
    }
}
