package emaki.jiuwu.craft.corelib.action.v2.exec;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;

/**
 * Minimal stand-ins for the two Bukkit types the interpreter touches.
 *
 * <p>Interface proxies rather than a mocking framework: the interpreter only reads {@code isValid()},
 * {@code getUniqueId()} and {@code isEnabled()}, so a proxy keeps the test honest about how little of
 * Bukkit the pipeline actually needs. Anything else throws, which would expose an accidental new
 * dependency on live server state.</p>
 */
final class TestSubjects {

    private TestSubjects() {
    }

    /** A toggleable entity subject, used to model a target dying mid-iteration. */
    record MutableEntity(Entity entity, AtomicBoolean valid, String name) {

        CoreActionSubject subject() {
            return CoreActionSubject.of(entity);
        }

        void invalidate() {
            valid.set(false);
        }
    }

    static MutableEntity entity(String name) {
        AtomicBoolean valid = new AtomicBoolean(true);
        UUID id = UUID.randomUUID();
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "isValid" -> valid.get();
            case "getUniqueId" -> id;
            case "getName", "toString" -> name;
            case "equals" -> proxy == (args == null ? null : args[0]);
            case "hashCode" -> id.hashCode();
            default -> throw new UnsupportedOperationException(
                    "test entity does not implement " + method.getName());
        };
        Entity entity = (Entity) Proxy.newProxyInstance(TestSubjects.class.getClassLoader(),
                new Class<?>[] {Entity.class}, handler);
        return new MutableEntity(entity, valid, name);
    }

    static Plugin plugin(String name, AtomicBoolean enabled) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "isEnabled" -> enabled.get();
            case "getName", "toString" -> name;
            case "equals" -> proxy == (args == null ? null : args[0]);
            case "hashCode" -> name.hashCode();
            default -> throw new UnsupportedOperationException(
                    "test plugin does not implement " + method.getName());
        };
        return (Plugin) Proxy.newProxyInstance(TestSubjects.class.getClassLoader(),
                new Class<?>[] {Plugin.class}, handler);
    }

    static Plugin enabledPlugin() {
        return plugin("TestPlugin", new AtomicBoolean(true));
    }
}
