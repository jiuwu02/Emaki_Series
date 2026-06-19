package emaki.jiuwu.craft.corelib.script;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.polyglot.HostAccess;

import emaki.jiuwu.craft.corelib.text.Texts;

public final class ScriptModuleRegistry {

    private final Map<String, ScriptModuleFactory> factories = new ConcurrentHashMap<>();

    public void register(String id, ScriptModuleFactory factory) {
        if (Texts.isBlank(id) || factory == null) {
            return;
        }
        factories.put(Texts.normalizeId(id), factory);
    }

    public void unregister(String id) {
        if (Texts.isBlank(id)) {
            return;
        }
        factories.remove(Texts.normalizeId(id));
    }

    public Object create(String id, ScriptModuleContext context) {
        String normalizedId = Texts.normalizeId(id);
        if (Texts.isBlank(normalizedId)) {
            return new UnavailableScriptModuleApi(id);
        }
        ScriptModuleFactory factory = factories.get(normalizedId);
        if (factory == null) {
            return new UnavailableScriptModuleApi(normalizedId);
        }
        Object module = factory.create(context);
        return module == null ? new UnavailableScriptModuleApi(normalizedId) : module;
    }

    public ScriptModulesApi api(ScriptModuleContext context) {
        return new ScriptModulesApi(this, context);
    }

    public List<String> moduleIds() {
        return factories.keySet().stream().sorted().toList();
    }

    public static final class ScriptModulesApi {

        private final ScriptModuleRegistry registry;
        private final ScriptModuleContext context;
        private final Map<String, Object> moduleOverrides;
        private final Map<String, Object> localCache = new LinkedHashMap<>();

        private ScriptModulesApi(ScriptModuleRegistry registry, ScriptModuleContext context) {
            this.registry = registry;
            this.context = context;
            this.moduleOverrides = normalizeOverrides(context == null ? Map.of() : context.moduleOverrides());
        }

        @HostAccess.Export
        public Object get(String id) {
            String normalizedId = Texts.normalizeId(id);
            if (Texts.isBlank(normalizedId)) {
                return new UnavailableScriptModuleApi(id);
            }
            Object override = moduleOverrides.get(normalizedId);
            if (override != null) {
                return override;
            }
            return localCache.computeIfAbsent(normalizedId, key -> registry.create(key, context));
        }

        @HostAccess.Export
        public boolean available(String id) {
            Object module = get(id);
            if (module instanceof UnavailableScriptModuleApi) {
                return false;
            }
            try {
                java.lang.reflect.Method method = module.getClass().getMethod("available");
                Object result = method.invoke(module);
                return result instanceof Boolean value ? value : true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return true;
            }
        }

        @HostAccess.Export
        public List<String> ids() {
            return java.util.stream.Stream.concat(registry.moduleIds().stream(), moduleOverrides.keySet().stream())
                    .distinct()
                    .sorted()
                    .toList();
        }

        private static Map<String, Object> normalizeOverrides(Map<String, Object> overrides) {
            if (overrides == null || overrides.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : overrides.entrySet()) {
                String key = Texts.normalizeId(entry.getKey());
                if (Texts.isNotBlank(key) && entry.getValue() != null) {
                    normalized.put(key, entry.getValue());
                }
            }
            return Map.copyOf(normalized);
        }
    }
}
