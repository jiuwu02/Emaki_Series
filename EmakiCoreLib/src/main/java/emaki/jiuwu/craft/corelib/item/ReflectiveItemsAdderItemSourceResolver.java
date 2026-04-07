package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveItemsAdderItemSourceResolver implements ManagedItemSourceResolver {

    private static final String PLUGIN_NAME = "ItemsAdder";
    private static final String LOAD_EVENT_CLASS = "dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent";

    private final PluginAvailability pluginAvailability;
    private final Accessor accessor;
    private volatile boolean loaded;

    ReflectiveItemsAdderItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new ReflectiveAccessor());
    }

    ReflectiveItemsAdderItemSourceResolver(PluginAvailability pluginAvailability, Accessor accessor) {
        this.pluginAvailability = pluginAvailability == null ? PluginAvailability.BUKKIT : pluginAvailability;
        this.accessor = accessor == null ? new ReflectiveAccessor() : accessor;
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
    public boolean supports(ItemSource source) {
        return source != null && source.getType() == ItemSourceType.ITEMSADDER;
    }

    @Override
    public boolean isAvailable(ItemSource source) {
        return supports(source) && isOperational();
    }

    @Override
    public ItemSource identify(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !isOperational()) {
            return null;
        }
        String identifier = accessor.identifyIdentifier(itemStack);
        return Texts.isBlank(identifier) ? null : new ItemSource(ItemSourceType.ITEMSADDER, identifier);
    }

    @Override
    public ItemStack create(ItemSource source, int amount) {
        if (!supports(source) || !isOperational()) {
            return null;
        }
        ItemStack itemStack = accessor.createItem(source.getIdentifier());
        if (itemStack == null) {
            return null;
        }
        ItemStack cloned = itemStack.clone();
        cloned.setAmount(Math.max(1, amount));
        return cloned;
    }

    @Override
    public Status bootstrap() {
        return refresh(false);
    }

    @Override
    public Status onPluginEnabled() {
        return refresh(false);
    }

    @Override
    public Status onItemsLoaded() {
        return refresh(true);
    }

    @Override
    public void onPluginDisabled() {
        loaded = false;
        accessor.reset();
    }

    private Status refresh(boolean loadedSignal) {
        if (!pluginAvailability.isPluginEnabled(pluginName())) {
            loaded = false;
            accessor.reset();
            return new Status(State.ABSENT, "");
        }
        if (!accessor.ensureAvailable()) {
            loaded = false;
            return new Status(State.INCOMPATIBLE, accessor.failureReason());
        }
        if (loadedSignal) {
            loaded = true;
        }
        if (!loaded) {
            loaded = accessor.detectLoaded();
        }
        return loaded
                ? new Status(State.READY, "")
                : new Status(State.WAITING, "ItemsAdder items are not loaded yet.");
    }

    private boolean isOperational() {
        return loaded && pluginAvailability.isPluginEnabled(pluginName()) && accessor.ensureAvailable();
    }

    interface Accessor {

        boolean ensureAvailable();

        String failureReason();

        boolean detectLoaded();

        String identifyIdentifier(ItemStack itemStack);

        ItemStack createItem(String identifier);

        void reset();
    }

    private static final class ReflectiveAccessor implements Accessor {

        private boolean initialized;
        private boolean available;
        private String failureReason = "";
        private Method customStackByItemStackMethod;
        private Method customStackGetInstanceMethod;
        private Method customStackGetItemStackMethod;
        private Method customStackGetNamespacedIdMethod;
        private Method itemsAdderAreItemsLoadedMethod;
        private Method itemsAdderGetAllItemsMethod;

        @Override
        public synchronized boolean ensureAvailable() {
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
                customStackByItemStackMethod = customStackClass.getMethod("byItemStack", ItemStack.class);
                customStackGetInstanceMethod = customStackClass.getMethod("getInstance", String.class);
                customStackGetItemStackMethod = customStackClass.getMethod("getItemStack");
                customStackGetNamespacedIdMethod = customStackClass.getMethod("getNamespacedID");
                Class<?> itemsAdderClass = Class.forName("dev.lone.itemsadder.api.ItemsAdder");
                itemsAdderAreItemsLoadedMethod = getOptionalMethod(itemsAdderClass, "areItemsLoaded");
                itemsAdderGetAllItemsMethod = getOptionalMethod(itemsAdderClass, "getAllItems");
                available = true;
                failureReason = "";
            } catch (Throwable throwable) {
                available = false;
                failureReason = throwable.getClass().getSimpleName() + ": " + Texts.toStringSafe(throwable.getMessage());
            }
            return available;
        }

        @Override
        public String failureReason() {
            return failureReason;
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
        public ItemStack createItem(String identifier) {
            Object customStack = invoke(customStackGetInstanceMethod, null, identifier);
            return asItemStack(invoke(customStackGetItemStackMethod, customStack));
        }

        @Override
        public synchronized void reset() {
            initialized = false;
            available = false;
            failureReason = "";
            customStackByItemStackMethod = null;
            customStackGetInstanceMethod = null;
            customStackGetItemStackMethod = null;
            customStackGetNamespacedIdMethod = null;
            itemsAdderAreItemsLoadedMethod = null;
            itemsAdderGetAllItemsMethod = null;
        }

        private Method getOptionalMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
            try {
                return type.getMethod(methodName, parameterTypes);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private Object invoke(Method method, Object target, Object... arguments) {
            if (method == null || target == null && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            try {
                return method.invoke(target, arguments);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private ItemStack asItemStack(Object value) {
            return value instanceof ItemStack itemStack ? itemStack : null;
        }
    }
}
