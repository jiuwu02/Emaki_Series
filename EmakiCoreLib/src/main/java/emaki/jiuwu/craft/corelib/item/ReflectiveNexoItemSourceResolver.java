package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Method;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveNexoItemSourceResolver implements ManagedItemSourceResolver {

    private static final String PLUGIN_NAME = "Nexo";
    private static final String LOAD_EVENT_CLASS = "com.nexomc.nexo.api.events.NexoItemsLoadedEvent";

    private final PluginAvailability pluginAvailability;
    private final Accessor accessor;
    private volatile boolean loaded;

    ReflectiveNexoItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new ReflectiveAccessor());
    }

    ReflectiveNexoItemSourceResolver(PluginAvailability pluginAvailability, Accessor accessor) {
        this.pluginAvailability = pluginAvailability == null ? PluginAvailability.BUKKIT : pluginAvailability;
        this.accessor = accessor == null ? new ReflectiveAccessor() : accessor;
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
    public boolean supports(ItemSource source) {
        return source != null && source.getType() == ItemSourceType.NEXO;
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
        return Texts.isBlank(identifier) ? null : new ItemSource(ItemSourceType.NEXO, identifier);
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
                : new Status(State.WAITING, "Nexo items are not loaded yet.");
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
        private Method itemFromIdMethod;
        private Method idFromItemMethod;
        private Method entriesMethod;
        private Method itemBuilderBuildMethod;

        @Override
        public synchronized boolean ensureAvailable() {
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                Class<?> nexoItemsClass = Class.forName("com.nexomc.nexo.api.NexoItems");
                itemFromIdMethod = nexoItemsClass.getMethod("itemFromId", String.class);
                idFromItemMethod = nexoItemsClass.getMethod("idFromItem", ItemStack.class);
                entriesMethod = getOptionalMethod(nexoItemsClass, "entries");

                Class<?> itemBuilderClass = Class.forName("com.nexomc.nexo.items.ItemBuilder");
                itemBuilderBuildMethod = resolveBuildMethod(itemBuilderClass);
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
            Object entries = invoke(entriesMethod, null);
            return entries instanceof Map<?, ?>;
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            return Texts.trim(invoke(idFromItemMethod, null, itemStack));
        }

        @Override
        public ItemStack createItem(String identifier) {
            Object itemBuilder = invoke(itemFromIdMethod, null, identifier);
            return buildItem(itemBuilder);
        }

        @Override
        public synchronized void reset() {
            initialized = false;
            available = false;
            failureReason = "";
            itemFromIdMethod = null;
            idFromItemMethod = null;
            entriesMethod = null;
            itemBuilderBuildMethod = null;
        }

        private Method resolveBuildMethod(Class<?> itemBuilderClass) throws NoSuchMethodException {
            try {
                return itemBuilderClass.getMethod("build");
            } catch (NoSuchMethodException ignored) {
                return itemBuilderClass.getMethod("getFinalItemStack");
            }
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

        private ItemStack buildItem(Object itemBuilder) {
            return asItemStack(invoke(itemBuilderBuildMethod, itemBuilder));
        }

        private ItemStack asItemStack(Object value) {
            return value instanceof ItemStack itemStack ? itemStack : null;
        }
    }
}
