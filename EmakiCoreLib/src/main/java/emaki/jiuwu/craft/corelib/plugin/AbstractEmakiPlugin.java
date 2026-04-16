package emaki.jiuwu.craft.corelib.plugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;

public abstract class AbstractEmakiPlugin extends JavaPlugin implements EmakiServiceRegistry {

    private final Map<Class<?>, Object> serviceRegistry = new ConcurrentHashMap<>();

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    @Override
    public <T> T getService(Class<T> type) {
        if (type == null) {
            return null;
        }
        Object service = serviceRegistry.get(type);
        return type.isInstance(service) ? type.cast(service) : null;
    }

    protected final void registerServices(Map<Class<?>, Object> services) {
        serviceRegistry.clear();
        if (services != null) {
            serviceRegistry.putAll(services);
        }
    }

    protected final void registerServices(RuntimeComponents components) {
        registerServices(components == null ? Map.of() : components.services());
    }
}
