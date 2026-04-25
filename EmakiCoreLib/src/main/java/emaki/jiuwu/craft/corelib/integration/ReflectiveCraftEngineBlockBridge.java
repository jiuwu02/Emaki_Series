package emaki.jiuwu.craft.corelib.integration;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ReflectiveCraftEngineBlockBridge implements CraftEngineBlockBridge {

    private static final String PLUGIN_NAME = "CraftEngine";

    private final JavaPlugin owner;
    private volatile boolean initialized;
    private volatile boolean supported;
    private volatile Method identifyMethod;
    private volatile Method isCustomMethod;
    private volatile Method stateKeyMethod;

    public ReflectiveCraftEngineBlockBridge(JavaPlugin owner) {
        this.owner = owner;
    }

    @Override
    public boolean available() {
        ensureBindings();
        return supported;
    }

    @Override
    public boolean isCustomBlock(Block block) {
        if (block == null) {
            return false;
        }
        ensureBindings();
        if (!supported) {
            return false;
        }
        if (isCustomMethod != null) {
            Object result = invoke(isCustomMethod, null, block);
            if (result instanceof Boolean bool) {
                return bool;
            }
        }
        return Texts.isNotBlank(identifyBlock(block));
    }

    @Override
    public String identifyBlock(Block block) {
        if (block == null) {
            return "";
        }
        ensureBindings();
        if (!supported || identifyMethod == null) {
            return "";
        }
        Object state = invoke(identifyMethod, null, block);
        if (state == null) {
            return "";
        }
        Object key = stateKeyMethod == null ? state : invoke(stateKeyMethod, state);
        return normalizeCraftEngineId(key == null ? state : key);
    }

    @Override
    public boolean matches(Block block, String identifier) {
        String actual = normalizeCraftEngineId(identifyBlock(block));
        String expected = normalizeCraftEngineId(identifier);
        return Texts.isNotBlank(actual) && actual.equals(expected);
    }

    private void ensureBindings() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            supported = false;
            identifyMethod = null;
            isCustomMethod = null;
            stateKeyMethod = null;
            Plugin plugin = Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
            if (plugin == null || !plugin.isEnabled()) {
                initialized = true;
                return;
            }
            try {
                ClassLoader classLoader = plugin.getClass().getClassLoader();
                Class<?> blocksClass = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineBlocks", true, classLoader);
                identifyMethod = resolveMethod(blocksClass,
                        Set.of("getCustomBlockState", "customBlockState", "getBlockState", "getPlacedBlockState"),
                        Block.class);
                isCustomMethod = resolveMethod(blocksClass,
                        Set.of("isCustomBlock", "hasCustomBlock"),
                        Block.class);
                Class<?> stateClass = resolveStateClass(blocksClass, identifyMethod);
                stateKeyMethod = stateClass == null
                        ? null
                        : resolveMethod(stateClass, Set.of("id", "key", "getId", "getKey", "asString", "blockId"));
                supported = identifyMethod != null || isCustomMethod != null;
            } catch (Exception exception) {
                owner.getLogger().warning("Failed to initialize CraftEngine block bridge: " + exception.getMessage());
            } finally {
                initialized = true;
            }
        }
    }

    private Class<?> resolveStateClass(Class<?> blocksClass, Method identify) {
        if (identify != null) {
            return identify.getReturnType();
        }
        for (Method method : blocksClass.getMethods()) {
            if (method == null || method.getParameterCount() != 1 || method.getParameterTypes()[0] != Block.class) {
                continue;
            }
            String name = method.getName().toLowerCase(Locale.ROOT);
            if (name.contains("state") || name.contains("block")) {
                return method.getReturnType();
            }
        }
        return null;
    }

    private Method resolveMethod(Class<?> type, Set<String> candidates, Class<?>... parameterTypes) {
        if (type == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (String candidate : candidates) {
            try {
                return type.getMethod(candidate, parameterTypes);
            } catch (NoSuchMethodException _) {
            }
        }
        LinkedHashSet<String> lowered = new LinkedHashSet<>();
        for (String candidate : candidates) {
            lowered.add(candidate.toLowerCase(Locale.ROOT));
        }
        for (Method method : type.getMethods()) {
            if (method == null || !lowered.contains(method.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Class<?>[] actualParameterTypes = method.getParameterTypes();
            if (actualParameterTypes.length != parameterTypes.length) {
                continue;
            }
            boolean matches = true;
            for (int index = 0; index < actualParameterTypes.length; index++) {
                if (!actualParameterTypes[index].isAssignableFrom(parameterTypes[index])
                        && !parameterTypes[index].isAssignableFrom(actualParameterTypes[index])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return method;
            }
        }
        return null;
    }

    private Method resolveMethod(Class<?> type, Set<String> candidates) {
        if (type == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (String candidate : candidates) {
            try {
                return type.getMethod(candidate);
            } catch (NoSuchMethodException _) {
            }
        }
        for (Method method : type.getMethods()) {
            if (method != null
                    && method.getParameterCount() == 0
                    && candidates.contains(method.getName())) {
                return method;
            }
        }
        return null;
    }

    private Object invoke(Method method, Object target, Object... arguments) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (Exception exception) {
            owner.getLogger().warning("Failed to invoke CraftEngine block bridge method '" + method.getName() + "': "
                    + exception.getMessage());
            supported = false;
            return null;
        }
    }

    private String normalizeCraftEngineId(Object raw) {
        String text = Texts.trim(raw);
        if (Texts.isBlank(text)) {
            return "";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("craftengine-")) {
            return normalized.substring("craftengine-".length());
        }
        if (normalized.startsWith("craftengine ")) {
            return normalized.substring("craftengine ".length());
        }
        return normalized;
    }
}
