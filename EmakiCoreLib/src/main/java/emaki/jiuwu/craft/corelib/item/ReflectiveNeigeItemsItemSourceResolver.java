package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveNeigeItemsItemSourceResolver implements ManagedItemSourceResolver {

    private static final String PLUGIN_NAME = "NeigeItems";
    private static final String LOAD_EVENT_CLASS = "pers.neige.neigeitems.event.PluginReloadEvent$Post";

    private final PluginAvailability pluginAvailability;
    private final Accessor accessor;
    private volatile boolean loaded;

    ReflectiveNeigeItemsItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new ReflectiveAccessor());
    }

    ReflectiveNeigeItemsItemSourceResolver(PluginAvailability pluginAvailability, Accessor accessor) {
        this.pluginAvailability = pluginAvailability == null ? PluginAvailability.BUKKIT : pluginAvailability;
        this.accessor = accessor == null ? new ReflectiveAccessor() : accessor;
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
    public boolean supports(ItemSource source) {
        return source != null && source.getType() == ItemSourceType.NEIGEITEMS;
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
        return Texts.isBlank(identifier) ? null : new ItemSource(ItemSourceType.NEIGEITEMS, identifier);
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
                : new Status(State.WAITING, "NeigeItems items are not loaded yet.");
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
        private Field itemManagerInstanceField;
        private Method getItemsMethod;
        private Method getItemIdMethod;
        private Method getItemStackMethod;

        @Override
        public synchronized boolean ensureAvailable() {
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                Class<?> itemManagerClass = Class.forName("pers.neige.neigeitems.manager.ItemManager");
                itemManagerInstanceField = itemManagerClass.getField("INSTANCE");
                getItemsMethod = itemManagerClass.getMethod("getItems");
                getItemIdMethod = itemManagerClass.getMethod("getItemId", ItemStack.class);
                getItemStackMethod = itemManagerClass.getMethod("getItemStack", String.class);
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
            Object itemManager = instance();
            Object items = invoke(getItemsMethod, itemManager);
            return items instanceof Map<?, ?>;
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            return Texts.trim(invoke(getItemIdMethod, instance(), itemStack));
        }

        @Override
        public ItemStack createItem(String identifier) {
            return asItemStack(invoke(getItemStackMethod, instance(), identifier));
        }

        @Override
        public synchronized void reset() {
            initialized = false;
            available = false;
            failureReason = "";
            itemManagerInstanceField = null;
            getItemsMethod = null;
            getItemIdMethod = null;
            getItemStackMethod = null;
        }

        private Object instance() {
            if (itemManagerInstanceField == null) {
                return null;
            }
            try {
                return itemManagerInstanceField.get(null);
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
