package emaki.jiuwu.craft.corelib.api.script.modules;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.script.ScriptSnapshots;
import emaki.jiuwu.craft.corelib.script.ScriptWorkerBoundary;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptServiceApiSupport {

    private ScriptServiceApiSupport() {
    }

    public record ServiceSnapshot(boolean available, String apiVersion, String pluginName, boolean ready) {

        public ServiceSnapshot {
            apiVersion = Texts.toStringSafe(apiVersion);
            pluginName = Texts.toStringSafe(pluginName);
        }

        public static ServiceSnapshot unavailable() {
            return new ServiceSnapshot(false, "", "", false);
        }
    }

    public static ServiceSnapshot serviceSnapshot(String serviceClassName) {
        return service(serviceClassName)
                .map(service -> new ServiceSnapshot(
                        true,
                        invokeString(service, "apiVersion", new Class<?>[0]),
                        invokeString(service, "pluginName", new Class<?>[0]),
                        invokeBoolean(service, "isReady", new Class<?>[0])
                ))
                .orElseGet(ServiceSnapshot::unavailable);
    }

    public static Optional<Object> service(String serviceClassName) {
        if (ScriptWorkerBoundary.active() || Texts.isBlank(serviceClassName)) {
            return Optional.empty();
        }
        Optional<Class<?>> serviceClass = serviceClass(serviceClassName);
        if (serviceClass.isPresent()) {
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(serviceClass.get());
            return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
        }
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin == null || !plugin.isEnabled()) {
                continue;
            }
            Optional<Object> provider = serviceFromPluginClassLoader(plugin, serviceClassName);
            if (provider.isPresent()) {
                return provider;
            }
        }
        return Optional.empty();
    }

    public static boolean available(String serviceClassName) {
        return service(serviceClassName).isPresent();
    }

    private static Optional<Class<?>> serviceClass(String serviceClassName) {
        try {
            return Optional.of(Class.forName(serviceClassName));
        } catch (ClassNotFoundException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private static Optional<Object> serviceFromPluginClassLoader(Plugin plugin, String serviceClassName) {
        try {
            Class<?> serviceClass = Class.forName(serviceClassName, false, plugin.getClass().getClassLoader());
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(serviceClass);
            return provider == null ? Optional.empty() : Optional.ofNullable(provider.getProvider());
        } catch (ClassNotFoundException | LinkageError exception) {
            return Optional.empty();
        }
    }

    private static boolean implementsInterface(Class<?> type, String serviceClassName) {
        if (type == null) {
            return false;
        }
        for (Class<?> interfaceType : type.getInterfaces()) {
            if (serviceClassName.equals(interfaceType.getName()) || implementsInterface(interfaceType, serviceClassName)) {
                return true;
            }
        }
        return implementsInterface(type.getSuperclass(), serviceClassName);
    }

    public static Object invoke(Object service, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        if (ScriptWorkerBoundary.active() || service == null) {
            return null;
        }
        try {
            Method method = service.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(service, arguments);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    public static boolean invokeBoolean(Object service, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        Object result = invoke(service, methodName, parameterTypes, arguments);
        return result instanceof Boolean value && value;
    }

    public static String invokeString(Object service, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        Object result = invoke(service, methodName, parameterTypes, arguments);
        return Texts.toStringSafe(result);
    }

    public static List<String> toStringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : collection) {
            if (entry != null) {
                result.add(Texts.toStringSafe(entry));
            }
        }
        return List.copyOf(result);
    }

    public static ItemStack item(ActionContext context, String itemKey) {
        if (ScriptWorkerBoundary.active() || context == null || Texts.isBlank(itemKey)) {
            return null;
        }
        Object value = context.attribute(itemKey);
        return value instanceof ItemStack itemStack ? itemStack : null;
    }

    public static Map<String, Object> itemSnapshot(ActionContext context, String itemKey) {
        if (ScriptWorkerBoundary.active() || context == null || Texts.isBlank(itemKey)) {
            return Map.of();
        }
        Object value = context.attribute(itemKey);
        if (value instanceof Map<?, ?> map) {
            return ScriptSnapshots.immutableMap(map);
        }
        return itemSummary(value instanceof ItemStack itemStack ? itemStack : null);
    }

    public static Map<String, Object> itemSummary(ItemStack itemStack) {
        if (ScriptWorkerBoundary.active() || itemStack == null || itemStack.getType().isAir()) {
            return Map.of();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", itemStack.getType().name().toLowerCase(java.util.Locale.ROOT));
        summary.put("amount", itemStack.getAmount());
        summary.put("displayName", ItemTextBridge.effectiveNamePlain(itemStack));
        List<String> lore = loreLinesPlain(itemStack);
        if (!lore.isEmpty()) {
            summary.put("lore", lore);
        }
        Integer customModelData = customModelData(itemStack);
        if (customModelData != null) {
            summary.put("customModelData", customModelData);
        }
        return ScriptSnapshots.immutableMap(summary);
    }

    private static List<String> loreLinesPlain(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return List.of();
        }
        List<net.kyori.adventure.text.Component> lore = ItemTextBridge.lore(itemStack.getItemMeta());
        if (lore == null || lore.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(lore.size());
        for (net.kyori.adventure.text.Component line : lore) {
            lines.add(emaki.jiuwu.craft.corelib.text.MiniMessages.plain(line));
        }
        return List.copyOf(lines);
    }

    private static Integer customModelData(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return null;
        }
        try {
            org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                return null;
            }
            org.bukkit.inventory.meta.components.CustomModelDataComponent component = meta.getCustomModelDataComponent();
            List<Float> floats = component == null ? List.of() : component.getFloats();
            if (floats != null && !floats.isEmpty() && floats.get(0) != null) {
                return Math.round(floats.get(0));
            }
        } catch (RuntimeException | LinkageError ignored) {

        }
        return null;
    }

    public static Map<String, Object> payloadToMap(Object payload) {
        if (payload == null) {
            return null;
        }
        if (ScriptWorkerBoundary.active()) {
            return Map.of();
        }
        if (payload instanceof Map<?, ?> map) {
            return ScriptSnapshots.immutableMap(map);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sourceId", invoke(payload, "sourceId", new Class<?>[0]));
        map.put("attributes", copyMap(invoke(payload, "attributes", new Class<?>[0])));
        map.put("meta", copyMap(invoke(payload, "meta", new Class<?>[0])));
        map.put("conditions", copyMap(invoke(payload, "conditions", new Class<?>[0])));
        map.put("schemaVersion", invoke(payload, "schemaVersion", new Class<?>[0]));
        map.put("updatedAt", invoke(payload, "updatedAt", new Class<?>[0]));
        return ScriptSnapshots.immutableMap(map);
    }

    public static Map<String, Object> payloadsToMap(Object payloads) {
        if (ScriptWorkerBoundary.active() || !(payloads instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(Texts.toStringSafe(entry.getKey()), payloadToMap(entry.getValue()));
        }
        return ScriptSnapshots.immutableMap(result);
    }

    public static Map<String, Object> damageResultToMap(Object result) {
        if (result == null) {
            return Map.of();
        }
        if (ScriptWorkerBoundary.active()) {
            return Map.of();
        }
        if (result instanceof Map<?, ?> map) {
            return ScriptSnapshots.immutableMap(map);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "damageTypeId", invoke(result, "damageTypeId", new Class<?>[0]));
        put(map, "finalDamage", invoke(result, "finalDamage", new Class<?>[0]));
        put(map, "critical", invoke(result, "critical", new Class<?>[0]));
        put(map, "roll", invoke(result, "roll", new Class<?>[0]));
        put(map, "stageValues", copyMap(invoke(result, "stageValues", new Class<?>[0])));
        put(map, "context", copyMap(invoke(result, "context", new Class<?>[0])));
        return ScriptSnapshots.immutableMap(map);
    }

    public static Map<String, Object> strengthenStateToMap(Object state) {
        if (state == null) {
            return Map.of();
        }
        if (ScriptWorkerBoundary.active()) {
            return Map.of();
        }
        if (state instanceof Map<?, ?> map) {
            return ScriptSnapshots.immutableMap(map);
        }
        Map<String, Object> map = new LinkedHashMap<>();
        put(map, "eligible", invoke(state, "eligible", new Class<?>[0]));
        put(map, "eligibleReason", invoke(state, "eligibleReason", new Class<?>[0]));
        put(map, "hasLayer", invoke(state, "hasLayer", new Class<?>[0]));
        put(map, "baseSource", Texts.toStringSafe(invoke(state, "baseSource", new Class<?>[0])));
        put(map, "baseSourceSignature", invoke(state, "baseSourceSignature", new Class<?>[0]));
        put(map, "recipeId", invoke(state, "recipeId", new Class<?>[0]));
        put(map, "currentStar", invoke(state, "currentStar", new Class<?>[0]));
        put(map, "crackLevel", invoke(state, "crackLevel", new Class<?>[0]));
        put(map, "milestoneFlags", new ArrayList<>(asCollection(invoke(state, "milestoneFlags", new Class<?>[0]))));
        put(map, "successCount", invoke(state, "successCount", new Class<?>[0]));
        put(map, "failureCount", invoke(state, "failureCount", new Class<?>[0]));
        put(map, "lastAttemptAt", invoke(state, "lastAttemptAt", new Class<?>[0]));
        put(map, "branchPath", invoke(state, "branchPath", new Class<?>[0]));
        put(map, "fractureLevel", invoke(state, "fractureLevel", new Class<?>[0]));
        return ScriptSnapshots.immutableMap(map);
    }

    public static Map<String, Double> doubleMap(Map<String, ?> source) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (entry.getKey() == null || value == null) {
                continue;
            }
            Double number = parseDouble(value);
            if (number != null) {
                result.put(entry.getKey(), number);
            }
        }
        return result;
    }

    public static Map<String, String> stringMap(Map<String, ?> source) {
        Map<String, String> result = new LinkedHashMap<>();
        if (source == null) {
            return result;
        }
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(entry.getKey(), Texts.toStringSafe(entry.getValue()));
            }
        }
        return result;
    }

    private static Map<String, Object> copyMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() != null) {
                result.put(Texts.toStringSafe(entry.getKey()), entry.getValue());
            }
        }
        return ScriptSnapshots.immutableMap(result);
    }

    private static Collection<?> asCollection(Object value) {
        return value instanceof Collection<?> collection ? collection : List.of();
    }

    private static void put(Map<String, Object> map, String key, Object value) {
        map.put(key, value == null ? "" : value);
    }

    private static Double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
