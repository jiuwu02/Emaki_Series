package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveCraftEngineItemSourceResolver implements ManagedItemSourceResolver {

    private static final String PLUGIN_NAME = "CraftEngine";
    private static final String LOAD_EVENT_CLASS = "net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent";

    private final PluginAvailability pluginAvailability;
    private final Accessor accessor;
    private volatile boolean loaded;

    ReflectiveCraftEngineItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new ReflectiveAccessor());
    }

    ReflectiveCraftEngineItemSourceResolver(PluginAvailability pluginAvailability, Accessor accessor) {
        this.pluginAvailability = pluginAvailability == null ? PluginAvailability.BUKKIT : pluginAvailability;
        this.accessor = accessor == null ? new ReflectiveAccessor() : accessor;
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
    public String loadEventClassName() {
        return LOAD_EVENT_CLASS;
    }

    @Override
    public boolean supports(ItemSource source) {
        return source != null && source.getType() == ItemSourceType.CRAFTENGINE;
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
        return Texts.isBlank(identifier) ? null : new ItemSource(ItemSourceType.CRAFTENGINE, identifier);
    }

    @Override
    public ItemStack create(ItemSource source, int amount) {
        if (!supports(source) || !isOperational()) {
            return null;
        }
        ItemStack itemStack = accessor.createItem(source.getIdentifier(), amount);
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
                : new Status(State.WAITING, "CraftEngine items are not loaded yet.");
    }

    private boolean isOperational() {
        return loaded && pluginAvailability.isPluginEnabled(pluginName()) && accessor.ensureAvailable();
    }

    interface Accessor {

        boolean ensureAvailable();

        String failureReason();

        boolean detectLoaded();

        String identifyIdentifier(ItemStack itemStack);

        ItemStack createItem(String identifier, int amount);

        void reset();
    }

    private static final class ReflectiveAccessor implements Accessor {

        private boolean initialized;
        private boolean available;
        private String failureReason = "";
        private Method loadedItemsMethod;
        private Method byIdMethod;
        private Method getCustomItemIdMethod;
        private Method keyOfMethod;
        private Method keyAsStringMethod;
        private Method buildContextEmptyMethod;
        private Class<?> itemBuildContextClass;
        private final Map<Class<?>, BuildPlan> buildPlans = new ConcurrentHashMap<>();

        @Override
        public synchronized boolean ensureAvailable() {
            if (initialized) {
                return available;
            }
            initialized = true;
            try {
                Class<?> craftEngineItemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
                Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
                itemBuildContextClass = Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext");

                loadedItemsMethod = craftEngineItemsClass.getMethod("loadedItems");
                byIdMethod = craftEngineItemsClass.getMethod("byId", keyClass);
                getCustomItemIdMethod = craftEngineItemsClass.getMethod("getCustomItemId", ItemStack.class);
                keyOfMethod = keyClass.getMethod("of", String.class);
                keyAsStringMethod = keyClass.getMethod("asString");
                buildContextEmptyMethod = itemBuildContextClass.getMethod("empty");
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
            Object items = invoke(loadedItemsMethod, null);
            return items instanceof Map<?, ?>;
        }

        @Override
        public String identifyIdentifier(ItemStack itemStack) {
            Object key = invoke(getCustomItemIdMethod, null, itemStack);
            return Texts.trim(invoke(keyAsStringMethod, key));
        }

        @Override
        public ItemStack createItem(String identifier, int amount) {
            Object key = invoke(keyOfMethod, null, identifier);
            Object customItem = invoke(byIdMethod, null, key);
            Object buildContext = invoke(buildContextEmptyMethod, null);
            return buildItem(customItem, buildContext, Math.max(1, amount));
        }

        @Override
        public synchronized void reset() {
            initialized = false;
            available = false;
            failureReason = "";
            loadedItemsMethod = null;
            byIdMethod = null;
            getCustomItemIdMethod = null;
            keyOfMethod = null;
            keyAsStringMethod = null;
            buildContextEmptyMethod = null;
            itemBuildContextClass = null;
            buildPlans.clear();
        }

        private ItemStack buildItem(Object customItem, Object buildContext, int amount) {
            if (customItem == null) {
                return null;
            }
            BuildPlan buildPlan = buildPlans.computeIfAbsent(customItem.getClass(), this::resolveBuildPlan);
            if (!buildPlan.isSupported()) {
                return null;
            }
            return asItemStack(buildPlan.invoke(this, customItem, buildContext, amount));
        }

        private BuildPlan resolveBuildPlan(Class<?> itemClass) {
            BuildPlan buildPlan = resolveDirectBuildPlan(itemClass, "buildItemStack");
            if (buildPlan != null) {
                return buildPlan;
            }
            buildPlan = resolveDirectBuildPlan(itemClass, "buildBukkitItem");
            if (buildPlan != null) {
                return buildPlan;
            }
            buildPlan = resolveWrappedBuildPlan(itemClass, "buildItem");
            return buildPlan == null ? BuildPlan.unsupported() : buildPlan;
        }

        private BuildPlan resolveDirectBuildPlan(Class<?> itemClass, String methodName) {
            Method method = getOptionalMethod(itemClass, methodName, itemBuildContextClass, int.class);
            if (method != null) {
                return new BuildPlan(method, InvocationMode.CONTEXT_AND_AMOUNT, null);
            }
            method = getOptionalMethod(itemClass, methodName, itemBuildContextClass);
            if (method != null) {
                return new BuildPlan(method, InvocationMode.CONTEXT_ONLY, null);
            }
            method = getOptionalMethod(itemClass, methodName, int.class);
            if (method != null) {
                return new BuildPlan(method, InvocationMode.AMOUNT_ONLY, null);
            }
            method = getOptionalMethod(itemClass, methodName);
            if (method != null) {
                return new BuildPlan(method, InvocationMode.NONE, null);
            }
            return null;
        }

        private BuildPlan resolveWrappedBuildPlan(Class<?> itemClass, String methodName) {
            BuildPlan buildPlan = resolveWrappedBuildPlan(itemClass, methodName, InvocationMode.CONTEXT_AND_AMOUNT,
                    itemBuildContextClass, int.class);
            if (buildPlan != null) {
                return buildPlan;
            }
            buildPlan = resolveWrappedBuildPlan(itemClass, methodName, InvocationMode.CONTEXT_ONLY, itemBuildContextClass);
            if (buildPlan != null) {
                return buildPlan;
            }
            buildPlan = resolveWrappedBuildPlan(itemClass, methodName, InvocationMode.AMOUNT_ONLY, int.class);
            if (buildPlan != null) {
                return buildPlan;
            }
            return resolveWrappedBuildPlan(itemClass, methodName, InvocationMode.NONE);
        }

        private BuildPlan resolveWrappedBuildPlan(Class<?> itemClass,
                String methodName,
                InvocationMode invocationMode,
                Class<?>... parameterTypes) {
            Method method = getOptionalMethod(itemClass, methodName, parameterTypes);
            if (method == null) {
                return null;
            }
            if (ItemStack.class.isAssignableFrom(method.getReturnType())) {
                return new BuildPlan(method, invocationMode, null);
            }
            Method unwrapMethod = resolveUnwrapMethod(method.getReturnType());
            return unwrapMethod == null ? null : new BuildPlan(method, invocationMode, unwrapMethod);
        }

        private Method resolveUnwrapMethod(Class<?> itemClass) {
            Method method = getOptionalMethod(itemClass, "getBukkitItem");
            if (method != null) {
                return method;
            }
            method = getOptionalMethod(itemClass, "getItem");
            if (method != null) {
                return method;
            }
            method = getOptionalMethod(itemClass, "minecraftItem");
            if (method != null) {
                return method;
            }
            return getOptionalMethod(itemClass, "platformItem");
        }

        private Method getOptionalMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
            if (type == null) {
                return null;
            }
            try {
                return type.getMethod(methodName, parameterTypes);
            } catch (Throwable throwable) {
                return null;
            }
        }

        private Object invoke(Method method, Object target, Object... arguments) {
            if (method == null || target == null && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            try {
                return method.invoke(target, arguments);
            } catch (Throwable throwable) {
                return null;
            }
        }

        private ItemStack asItemStack(Object value) {
            return value instanceof ItemStack itemStack ? itemStack : null;
        }

        private record BuildPlan(Method buildMethod, InvocationMode invocationMode, Method unwrapMethod) {

            private static BuildPlan unsupported() {
                return new BuildPlan(null, InvocationMode.NONE, null);
            }

            private boolean isSupported() {
                return buildMethod != null;
            }

            private Object invoke(ReflectiveAccessor accessor, Object target, Object buildContext, int amount) {
                Object builtItem = accessor.invoke(buildMethod, target, invocationMode.arguments(buildContext, amount));
                return unwrapMethod == null ? builtItem : accessor.invoke(unwrapMethod, builtItem);
            }
        }

        private enum InvocationMode {
            CONTEXT_AND_AMOUNT {
                @Override
                Object[] arguments(Object buildContext, int amount) {
                    return new Object[]{buildContext, amount};
                }
            },
            CONTEXT_ONLY {
                @Override
                Object[] arguments(Object buildContext, int amount) {
                    return new Object[]{buildContext};
                }
            },
            AMOUNT_ONLY {
                @Override
                Object[] arguments(Object buildContext, int amount) {
                    return new Object[]{amount};
                }
            },
            NONE {
                @Override
                Object[] arguments(Object buildContext, int amount) {
                    return new Object[0];
                }
            };

            abstract Object[] arguments(Object buildContext, int amount);
        }
    }
}
