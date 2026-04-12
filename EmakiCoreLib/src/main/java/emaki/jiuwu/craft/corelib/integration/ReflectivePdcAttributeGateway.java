package emaki.jiuwu.craft.corelib.integration;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;

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
        String nextSourceId = normalizeId(sourceId);
        String previousSourceId = normalizeId(registeredSourceId);
        if (Texts.isNotBlank(previousSourceId) && !previousSourceId.equals(nextSourceId)) {
            unregisterSource(previousSourceId);
        }
        if (Texts.isNotBlank(nextSourceId)) {
            registerSource(nextSourceId);
        }
        registeredSourceId = Texts.isNotBlank(nextSourceId) ? nextSourceId : null;
    }

    public void shutdown() {
        String sourceId = normalizeId(registeredSourceId);
        if (Texts.isNotBlank(sourceId)) {
            unregisterSource(sourceId);
        }
        registeredSourceId = null;
    }

    public boolean registerSource(String sourceId) {
        String normalized = normalizeId(sourceId);
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
        String normalized = normalizeId(sourceId);
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
        String normalized = normalizeId(sourceId);
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
        String normalized = normalizeId(sourceId);
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
        String normalized = normalizeId(sourceId);
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

    private boolean ensureRegisteredSource(String sourceId) {
        if (Texts.isBlank(sourceId)) {
            return false;
        }
        if (isRegisteredSource(sourceId)) {
            return true;
        }
        return registerSource(sourceId);
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
