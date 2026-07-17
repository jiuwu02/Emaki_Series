package emaki.jiuwu.craft.corelib.script;

import java.lang.reflect.Method;
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

    /**
     * Captures module membership and overrides at invocation submission time.
     */
    public ScriptModulesApi api(ScriptModuleContext context) {
        return new ScriptModulesApi(this, context);
    }

    public List<String> moduleIds() {
        return factories.keySet().stream().sorted().toList();
    }

    public static final class ScriptModulesApi {

        private final Map<String, ModuleSnapshot> modules;

        private ScriptModulesApi(ScriptModuleRegistry registry, ScriptModuleContext context) {
            Map<String, ModuleSnapshot> captured = new LinkedHashMap<>();
            for (String id : registry.moduleIds()) {
                captured.put(id, snapshot(registry.create(id, context)));
            }
            Map<String, Object> overrides = context == null ? Map.of() : context.moduleOverrides();
            if (overrides != null) {
                for (Map.Entry<String, Object> entry : overrides.entrySet()) {
                    String id = Texts.normalizeId(entry.getKey());
                    if (Texts.isNotBlank(id) && entry.getValue() != null) {
                        captured.put(id, snapshot(entry.getValue()));
                    }
                }
            }
            this.modules = Map.copyOf(captured);
        }

        @HostAccess.Export
        public Object get(String id) {
            String normalizedId = Texts.normalizeId(id);
            ModuleSnapshot snapshot = modules.get(normalizedId);
            if (snapshot != null) {
                return snapshot.value();
            }
            return ScriptHostObjectProxy.wrapIfExported(new UnavailableScriptModuleApi(normalizedId));
        }

        @HostAccess.Export
        public boolean available(String id) {
            ModuleSnapshot snapshot = modules.get(Texts.normalizeId(id));
            return snapshot != null && snapshot.available();
        }

        @HostAccess.Export
        public List<String> ids() {
            return modules.keySet().stream().sorted().toList();
        }

        private static ModuleSnapshot snapshot(Object module) {
            Object safeModule = module == null ? new UnavailableScriptModuleApi("") : module;
            boolean available = moduleAvailable(safeModule);
            return new ModuleSnapshot(ScriptHostObjectProxy.wrapIfExported(safeModule), available);
        }

        private static boolean moduleAvailable(Object module) {
            if (module instanceof UnavailableScriptModuleApi) {
                return false;
            }
            try {
                Method method = module.getClass().getMethod("available");
                Object result = method.invoke(module);
                return result instanceof Boolean value ? value : true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return true;
            }
        }
    }

    private record ModuleSnapshot(Object value, boolean available) {
    }
}
