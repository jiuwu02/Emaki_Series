package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

final class ReflectiveCraftEngineItemSourceResolver
        extends AbstractManagedItemSourceResolver<ReflectiveCraftEngineItemSourceResolver.ReflectiveAccessor> {

    private static final String PLUGIN_NAME = "CraftEngine";
    private static final String LOAD_EVENT_CLASS = "net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent";

    ReflectiveCraftEngineItemSourceResolver() {
        this(PluginAvailability.BUKKIT, new ReflectiveAccessor());
    }

    ReflectiveCraftEngineItemSourceResolver(PluginAvailability pluginAvailability, ReflectiveAccessor accessor) {
        super(pluginAvailability, accessor == null ? new ReflectiveAccessor() : accessor);
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
    protected ItemSourceType sourceType() {
        return ItemSourceType.CRAFTENGINE;
    }

    @Override
    protected String waitingDetail() {
        return "CraftEngine items are not loaded yet.";
    }

    static final class ReflectiveAccessor extends AbstractReflectiveAccessor
            implements AbstractManagedItemSourceResolver.Accessor {

        private Method loadedItemsMethod;
        private Method byIdMethod;
        private Method getCustomItemIdMethod;
        private Method keyOfMethod;
        private Method keyAsStringMethod;
        private Method buildContextEmptyMethod;
        private Class<?> itemBuildContextClass;
        private final Map<Class<?>, BuildPlan> buildPlans = new ConcurrentHashMap<>();

        @Override
        protected void initializeBindings() throws Exception {
            Class<?> craftEngineItemsClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Class<?> keyClass = Class.forName("net.momirealms.craftengine.core.util.Key");
            itemBuildContextClass = Class.forName("net.momirealms.craftengine.core.item.ItemBuildContext");

            loadedItemsMethod = craftEngineItemsClass.getMethod("loadedItems");
            byIdMethod = craftEngineItemsClass.getMethod("byId", keyClass);
            getCustomItemIdMethod = craftEngineItemsClass.getMethod("getCustomItemId", ItemStack.class);
            keyOfMethod = keyClass.getMethod("of", String.class);
            keyAsStringMethod = keyClass.getMethod("asString");
            buildContextEmptyMethod = itemBuildContextClass.getMethod("empty");
        }

        @Override
        protected void resetBindings() {
            loadedItemsMethod = null;
            byIdMethod = null;
            getCustomItemIdMethod = null;
            keyOfMethod = null;
            keyAsStringMethod = null;
            buildContextEmptyMethod = null;
            itemBuildContextClass = null;
            buildPlans.clear();
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
