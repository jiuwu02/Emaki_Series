package emaki.jiuwu.craft.corelib.integration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Bridges to EmakiAttribute's optional PDC API without creating a hard class link.
 */
public final class ReflectivePdcAttributeGateway {

    private static final String ATTRIBUTE_PLUGIN_NAME = "EmakiAttribute";
    private static final String API_CLASS_NAME = "emaki.jiuwu.craft.attribute.api.PdcAttributeApi";

    private final Plugin owner;
    private volatile Class<?> apiClass;
    private volatile Object apiInstance;
    private volatile String registeredSourceId;

    public ReflectivePdcAttributeGateway(Plugin owner) {
        this.owner = owner;
    }

    public boolean available() {
        return resolveApiInstance() != null;
    }

    public void syncRegistration(String sourceId) {
        String nextSourceId = Texts.normalizeId(sourceId);
        String previousSourceId = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(previousSourceId) && !previousSourceId.equals(nextSourceId)) {
            unregisterSource(previousSourceId);
        }
        if (Texts.isNotBlank(nextSourceId)) {
            registerSource(nextSourceId);
        }
        registeredSourceId = Texts.isNotBlank(nextSourceId) ? nextSourceId : null;
    }

    public void shutdown() {
        String sourceId = Texts.normalizeId(registeredSourceId);
        if (Texts.isNotBlank(sourceId)) {
            unregisterSource(sourceId);
        }
        registeredSourceId = null;
    }

    public boolean registerSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (Texts.isBlank(normalized)) {
            return false;
        }
        Object instance = resolveApiInstance();
        if (instance == null) {
            return false;
        }
        Object result = invoke(instance, "registerSource", new Class<?>[]{String.class}, normalized);
        return result instanceof Boolean bool && bool;
    }

    public void unregisterSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (Texts.isBlank(normalized)) {
            return;
        }
        Object instance = resolveApiInstance();
        if (instance == null) {
            return;
        }
        invoke(instance, "unregisterSource", new Class<?>[]{String.class}, normalized);
    }

    public boolean isRegisteredSource(String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (Texts.isBlank(normalized)) {
            return false;
        }
        Object instance = resolveApiInstance();
        if (instance == null) {
            return false;
        }
        Object result = invoke(instance, "isRegisteredSource", new Class<?>[]{String.class}, normalized);
        return result instanceof Boolean bool && bool;
    }

    public boolean write(ItemStack itemStack,
            String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized) || attributes == null || attributes.isEmpty()) {
            return false;
        }
        Object instance = resolveApiInstance();
        if (instance == null || !ensureRegisteredSource(normalized)) {
            return false;
        }
        Map<String, Double> attributeCopy = new LinkedHashMap<>(attributes);
        Map<String, String> metaCopy = meta == null ? Map.of() : new LinkedHashMap<>(meta);
        Object result = invoke(
                instance,
                "write",
                new Class<?>[]{ItemStack.class, String.class, Map.class, Map.class},
                itemStack,
                normalized,
                attributeCopy,
                metaCopy
        );
        return result instanceof Boolean bool && bool;
    }

    public boolean clear(ItemStack itemStack, String sourceId) {
        String normalized = Texts.normalizeId(sourceId);
        if (itemStack == null || Texts.isBlank(normalized)) {
            return false;
        }
        Object instance = resolveApiInstance();
        if (instance == null) {
            return false;
        }
        Object result = invoke(instance, "clear", new Class<?>[]{ItemStack.class, String.class}, itemStack, normalized);
        return result instanceof Boolean bool && bool;
    }

    public Map<String, AttributePayloadSnapshot> readAll(ItemStack itemStack) {
        if (itemStack == null) {
            return Map.of();
        }
        Object instance = resolveApiInstance();
        if (instance == null) {
            return Map.of();
        }
        Object result = invoke(instance, "readAll", new Class<?>[]{ItemStack.class}, itemStack);
        if (!(result instanceof Map<?, ?> payloadMap) || payloadMap.isEmpty()) {
            return Map.of();
        }
        Map<String, AttributePayloadSnapshot> snapshots = new LinkedHashMap<>();
        for (Object value : payloadMap.values()) {
            AttributePayloadSnapshot snapshot = snapshotOf(value);
            if (snapshot != null && Texts.isNotBlank(snapshot.sourceId())) {
                snapshots.put(snapshot.sourceId(), snapshot);
            }
        }
        return snapshots.isEmpty() ? Map.of() : Map.copyOf(snapshots);
    }

    public void copyPayloads(ItemStack fromItem, ItemStack toItem, Set<String> excludedSourceIds) {
        if (fromItem == null || toItem == null) {
            return;
        }
        Set<String> excluded = normalizeIds(excludedSourceIds);
        for (AttributePayloadSnapshot snapshot : readAll(fromItem).values()) {
            if (snapshot == null || excluded.contains(snapshot.sourceId())) {
                continue;
            }
            write(toItem, snapshot.sourceId(), snapshot.attributes(), snapshot.meta());
        }
    }

    private Object resolveApiInstance() {
        Plugin attributePlugin = Bukkit.getPluginManager().getPlugin(ATTRIBUTE_PLUGIN_NAME);
        if (attributePlugin == null || !attributePlugin.isEnabled()) {
            apiClass = null;
            apiInstance = null;
            return null;
        }
        Object instance = apiInstance;
        if (instance != null) {
            return instance;
        }
        try {
            ClassLoader classLoader = attributePlugin.getClass().getClassLoader();
            Class<?> resolvedApiClass = Class.forName(API_CLASS_NAME, true, classLoader);
            @SuppressWarnings("unchecked")
            RegisteredServiceProvider<Object> provider = (RegisteredServiceProvider<Object>) Bukkit.getServicesManager()
                    .getRegistration((Class<Object>) resolvedApiClass);
            if (provider == null) {
                apiClass = resolvedApiClass;
                apiInstance = null;
                return null;
            }
            apiClass = resolvedApiClass;
            apiInstance = provider.getProvider();
            return apiInstance;
        } catch (Exception exception) {
            owner.getLogger().warning("Failed to resolve EmakiAttribute PDC API: " + exception.getMessage());
            apiClass = null;
            apiInstance = null;
            return null;
        }
    }

    private Object invoke(Object instance, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        if (instance == null || Texts.isBlank(methodName)) {
            return null;
        }
        try {
            Class<?> targetClass = apiClass != null ? apiClass : instance.getClass();
            Method method = targetClass.getMethod(methodName, parameterTypes);
            return method.invoke(instance, arguments);
        } catch (Exception exception) {
            owner.getLogger().warning("Failed to invoke EmakiAttribute PDC API method '" + methodName + "': " + exception.getMessage());
            apiInstance = null;
            return null;
        }
    }

    private Object invokeInstanceMethod(Object instance, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        if (instance == null || Texts.isBlank(methodName)) {
            return null;
        }
        try {
            Method method = instance.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(instance, arguments);
        } catch (Exception exception) {
            owner.getLogger().warning("Failed to inspect EmakiAttribute payload method '" + methodName + "': " + exception.getMessage());
            return null;
        }
    }

    private boolean ensureRegisteredSource(String sourceId) {
        if (Texts.isBlank(sourceId)) {
            return false;
        }
        if (isRegisteredSource(sourceId)) {
            return true;
        }
        return registerSource(sourceId);
    }
private Set<String> normalizeIds(Set<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Set.of();
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (String sourceId : sourceIds) {
            String value = Texts.normalizeId(sourceId);
            if (Texts.isNotBlank(value)) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? Set.of() : Set.copyOf(normalized);
    }

    private AttributePayloadSnapshot snapshotOf(Object payload) {
        if (payload == null) {
            return null;
        }
        String sourceId = Texts.normalizeId(asString(invokeInstanceMethod(payload, "sourceId", new Class<?>[]{})));
        if (Texts.isBlank(sourceId)) {
            return null;
        }
        return new AttributePayloadSnapshot(
                sourceId,
                asDoubleMap(invokeInstanceMethod(payload, "attributes", new Class<?>[]{})),
                asStringMap(invokeInstanceMethod(payload, "meta", new Class<?>[]{}))
        );
    }

    private Map<String, Double> asDoubleMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Number number)) {
                continue;
            }
            values.put(Texts.normalizeId(String.valueOf(entry.getKey())), number.doubleValue());
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    private Map<String, String> asStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            values.put(Texts.normalizeId(String.valueOf(entry.getKey())), asString(entry.getValue()));
        }
        return values.isEmpty() ? Map.of() : Map.copyOf(values);
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record AttributePayloadSnapshot(String sourceId,
            Map<String, Double> attributes,
            Map<String, String> meta) {

        public AttributePayloadSnapshot {
            sourceId = sourceId == null ? "" : sourceId;
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
            meta = meta == null ? Map.of() : Map.copyOf(meta);
        }
    }
}

