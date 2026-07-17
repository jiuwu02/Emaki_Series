package emaki.jiuwu.craft.corelib.script;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.item.ItemTextBridge;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 通过 Graal 代理成员暴露项目脚本 API 宿主对象，并在所有参数/返回值边界执行深度快照。
 *
 * <p>Bukkit 插件类加载器可能让依赖插件类上的 {@code HostAccess.Export}
 * 注解无法被 CoreLib 创建的 Graal 上下文直接识别。该代理只按
 * HostAccess.Export 注解名称导出成员，避免放宽到 {@code HostAccess.ALL}
 * 或全量 public host access。除显式导出的 API 代理外，传给 JavaScript 的值
 * 只能是基础值、不可变集合或 detached Bukkit 快照。</p>
 */
public final class ScriptHostObjectProxy implements ProxyObject {

    private static final String HOST_ACCESS_EXPORT = "org.graalvm.polyglot.HostAccess$Export";
    private static final int MAX_SNAPSHOT_DEPTH = 24;
    private static final int MAX_COLLECTION_SIZE = 4_096;

    private final Object target;
    private final Map<String, List<Method>> methods;
    private final Map<String, Field> fields;

    private ScriptHostObjectProxy(Object target, Map<String, List<Method>> methods, Map<String, Field> fields) {
        this.target = target;
        this.methods = methods;
        this.fields = fields;
    }

    /**
     * Converts a script binding or exported API result to a safe guest value.
     */
    public static Object wrapIfExported(Object value) {
        return expose(value, true, new IdentityHashMap<>(), 0);
    }

    /**
     * Produces a detached result value and rejects API/host proxies.
     */
    public static Object snapshotValue(Object value) {
        return expose(value, false, new IdentityHashMap<>(), 0);
    }

    @Override
    public Object getMember(String key) {
        Field field = fields.get(key);
        if (field != null) {
            try {
                return wrapIfExported(field.get(target));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot read script API field: " + key, exception);
            }
        }
        List<Method> candidates = methods.get(key);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return (ProxyExecutable) arguments -> invoke(key, candidates, arguments);
    }

    @Override
    public Object getMemberKeys() {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>(fields.keySet());
        keys.addAll(methods.keySet());
        return keys.toArray(String[]::new);
    }

    @Override
    public boolean hasMember(String key) {
        return fields.containsKey(key) || methods.containsKey(key);
    }

    @Override
    public void putMember(String key, Value value) {
        throw new UnsupportedOperationException("Script API module members are read-only: " + key);
    }

    private Object invoke(String key, List<Method> candidates, Value[] arguments) {
        RuntimeException lastFailure = null;
        for (Method method : candidates) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != arguments.length) {
                continue;
            }
            try {
                Object[] converted = new Object[arguments.length];
                for (int index = 0; index < arguments.length; index++) {
                    converted[index] = convert(arguments[index], parameterTypes[index]);
                }
                return wrapIfExported(method.invoke(target, converted));
            } catch (IllegalArgumentException exception) {
                lastFailure = exception;
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot access script API method: " + key, exception);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException("Script API method failed: " + key, cause);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalArgumentException("No script API overload accepts " + arguments.length + " argument(s): " + key);
    }

    private static Object expose(Object value,
            boolean allowExportedProxy,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        if (value == null) {
            return null;
        }
        if (depth > MAX_SNAPSHOT_DEPTH) {
            throw new IllegalArgumentException("Script value snapshot exceeds maximum depth " + MAX_SNAPSHOT_DEPTH + ".");
        }
        if (value instanceof CharSequence sequence) {
            return sequence.toString();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Character character) {
            return character.toString();
        }
        if (value instanceof Value polyglotValue) {
            return expose(detach(polyglotValue), false, visiting, depth + 1);
        }
        if (value instanceof UUID || value instanceof Path || value instanceof java.net.URI
                || value instanceof java.net.URL || value instanceof TemporalAccessor) {
            return value.toString();
        }
        if (value.getClass().isEnum()) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Class<?>) {
            throw unsupportedHostValue(value);
        }
        if (value instanceof ActionContext actionContext) {
            return snapshotActionContext(actionContext, visiting, depth + 1);
        }
        if (value instanceof Plugin plugin) {
            return snapshotPlugin(plugin, visiting, depth + 1);
        }
        if (value instanceof Server server) {
            return snapshotServer(server, visiting, depth + 1);
        }
        if (value instanceof Player player) {
            return snapshotPlayer(player, visiting, depth + 1);
        }
        if (value instanceof LivingEntity livingEntity) {
            return snapshotLivingEntity(livingEntity, visiting, depth + 1);
        }
        if (value instanceof Entity entity) {
            return snapshotEntity(entity, visiting, depth + 1);
        }
        if (value instanceof World world) {
            return snapshotWorld(world, visiting, depth + 1);
        }
        if (value instanceof Inventory inventory) {
            return snapshotInventory(inventory, visiting, depth + 1);
        }
        if (value instanceof ItemStack itemStack) {
            return snapshotItem(itemStack, visiting, depth + 1);
        }
        if (value instanceof Location location) {
            return snapshotLocation(location, visiting, depth + 1);
        }
        if (value instanceof Optional<?> optional) {
            return expose(optional.orElse(null), allowExportedProxy, visiting, depth + 1);
        }
        if (value instanceof Map<?, ?> map) {
            enter(value, visiting);
            try {
                Map<String, Object> result = new LinkedHashMap<>();
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (++count > MAX_COLLECTION_SIZE) {
                        throw new IllegalArgumentException("Script map snapshot exceeds maximum size " + MAX_COLLECTION_SIZE + ".");
                    }
                    result.put(Texts.toStringSafe(entry.getKey()), expose(entry.getValue(), allowExportedProxy, visiting, depth + 1));
                }
                return Collections.unmodifiableMap(result);
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof Iterable<?> iterable) {
            enter(value, visiting);
            try {
                List<Object> result = new ArrayList<>();
                for (Object element : iterable) {
                    if (result.size() >= MAX_COLLECTION_SIZE) {
                        throw new IllegalArgumentException("Script list snapshot exceeds maximum size " + MAX_COLLECTION_SIZE + ".");
                    }
                    result.add(expose(element, allowExportedProxy, visiting, depth + 1));
                }
                return Collections.unmodifiableList(result);
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof Iterator<?> iterator) {
            List<Object> result = new ArrayList<>();
            while (iterator.hasNext()) {
                if (result.size() >= MAX_COLLECTION_SIZE) {
                    throw new IllegalArgumentException("Script iterator snapshot exceeds maximum size " + MAX_COLLECTION_SIZE + ".");
                }
                result.add(expose(iterator.next(), allowExportedProxy, visiting, depth + 1));
            }
            return Collections.unmodifiableList(result);
        }
        if (value.getClass().isArray()) {
            enter(value, visiting);
            try {
                int length = Array.getLength(value);
                if (length > MAX_COLLECTION_SIZE) {
                    throw new IllegalArgumentException("Script array snapshot exceeds maximum size " + MAX_COLLECTION_SIZE + ".");
                }
                List<Object> result = new ArrayList<>(length);
                for (int index = 0; index < length; index++) {
                    result.add(expose(Array.get(value, index), allowExportedProxy, visiting, depth + 1));
                }
                return Collections.unmodifiableList(result);
            } finally {
                visiting.remove(value);
            }
        }
        if (value instanceof Throwable throwable) {
            return Collections.unmodifiableMap(Map.of(
                    "type", throwable.getClass().getName(),
                    "message", Texts.toStringSafe(throwable.getMessage())
            ));
        }
        if (value.getClass().isRecord()) {
            return snapshotRecord(value, allowExportedProxy, visiting, depth + 1);
        }
        if (value instanceof ScriptHostObjectProxy) {
            if (allowExportedProxy) {
                return value;
            }
            throw unsupportedHostValue(value);
        }
        if (value instanceof ProxyObject || value instanceof ProxyArray || value instanceof ProxyExecutable) {
            throw unsupportedHostValue(value);
        }
        Map<String, List<Method>> exportedMethods = exportedMethods(value.getClass());
        Map<String, Field> exportedFields = exportedFields(value.getClass());
        if (allowExportedProxy && (!exportedMethods.isEmpty() || !exportedFields.isEmpty())) {
            return new ScriptHostObjectProxy(value, exportedMethods, exportedFields);
        }
        throw unsupportedHostValue(value);
    }

    private static Map<String, Object> snapshotActionContext(ActionContext context,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(context, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("phase", context.phase());
            result.put("silent", context.silent());
            result.put("placeholders", expose(context.placeholders(), false, visiting, depth + 1));
            result.put("attributes", expose(context.attributes(), false, visiting, depth + 1));
            result.put("plugin", expose(context.sourcePlugin(), false, visiting, depth + 1));
            result.put("player", expose(context.player(), false, visiting, depth + 1));
            result.put("item", expose(context.attribute("item"), false, visiting, depth + 1));
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(context);
        }
    }

    private static Map<String, Object> snapshotPlugin(Plugin plugin,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(plugin, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", safeString(plugin::getName));
            result.put("enabled", safeBoolean(plugin::isEnabled));
            result.put("version", safeString(() -> plugin.getPluginMeta().getVersion()));
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(plugin);
        }
    }

    private static Map<String, Object> snapshotServer(Server server,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(server, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", safeString(server::getName));
            result.put("version", safeString(server::getVersion));
            result.put("bukkitVersion", safeString(server::getBukkitVersion));
            result.put("onlineMode", safeBoolean(server::getOnlineMode));
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(server);
        }
    }

    private static Map<String, Object> snapshotPlayer(Player player,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        Map<String, Object> result = new LinkedHashMap<>(snapshotLivingEntity(player, visiting, depth));
        result.put("player", true);
        result.put("online", safeBoolean(player::isOnline));
        List<String> permissions = new ArrayList<>();
        try {
            for (org.bukkit.permissions.PermissionAttachmentInfo permission : player.getEffectivePermissions()) {
                if (permission != null && permission.getValue() && permission.getPermission() != null) {
                    permissions.add(permission.getPermission());
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        permissions.sort(String::compareTo);
        result.put("permissions", Collections.unmodifiableList(permissions));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> snapshotLivingEntity(LivingEntity entity,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        Map<String, Object> result = new LinkedHashMap<>(snapshotEntity(entity, visiting, depth));
        result.put("living", true);
        result.put("health", safeDouble(entity::getHealth));
        result.put("maxHealth", safeDouble(entity::getMaxHealth));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Object> snapshotEntity(Entity entity,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(entity, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("exists", safeBoolean(entity::isValid));
            result.put("name", safeString(entity::getName));
            result.put("uuid", safeString(() -> entity.getUniqueId().toString()));
            result.put("type", safeString(() -> entity.getType().name().toLowerCase(java.util.Locale.ROOT)));
            World world = safeObject(entity::getWorld);
            result.put("world", world == null ? Map.of() : snapshotWorld(world, visiting, depth + 1));
            Location location = safeObject(entity::getLocation);
            result.put("location", location == null ? Map.of() : snapshotLocation(location, visiting, depth + 1));
            result.put("living", false);
            result.put("player", false);
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(entity);
        }
    }

    private static Map<String, Object> snapshotWorld(World world,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(world, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", safeString(world::getName));
            result.put("uuid", safeString(() -> world.getUID().toString()));
            result.put("environment", safeString(() -> world.getEnvironment().name().toLowerCase(java.util.Locale.ROOT)));
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(world);
        }
    }

    private static Map<String, Object> snapshotInventory(Inventory inventory,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(inventory, visiting);
        try {
            int size;
            try {
                size = Math.max(0, Math.min(MAX_COLLECTION_SIZE, inventory.getSize()));
            } catch (RuntimeException | LinkageError exception) {
                size = 0;
            }
            List<Object> items = new ArrayList<>(size);
            for (int slot = 0; slot < size; slot++) {
                int index = slot;
                ItemStack item = safeObject(() -> inventory.getItem(index));
                items.add(item == null ? null : snapshotItem(item, visiting, depth + 1));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("size", size);
            result.put("items", Collections.unmodifiableList(items));
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(inventory);
        }
    }

    private static Map<String, Object> snapshotItem(ItemStack itemStack,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(itemStack, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", safeString(() -> itemStack.getType().name().toLowerCase(java.util.Locale.ROOT)));
            result.put("amount", safeInt(itemStack::getAmount));
            result.put("displayName", safeString(() -> ItemTextBridge.effectiveNamePlain(itemStack)));
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(itemStack);
        }
    }

    private static Map<String, Object> snapshotLocation(Location location,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(location, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            World world = safeObject(location::getWorld);
            result.put("world", world == null ? "" : safeString(world::getName));
            result.put("x", location.getX());
            result.put("y", location.getY());
            result.put("z", location.getZ());
            result.put("yaw", location.getYaw());
            result.put("pitch", location.getPitch());
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(location);
        }
    }

    private static Map<String, Object> snapshotRecord(Object record,
            boolean allowExportedProxy,
            IdentityHashMap<Object, Boolean> visiting,
            int depth) {
        enter(record, visiting);
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                try {
                    Method accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    result.put(component.getName(), expose(accessor.invoke(record), allowExportedProxy, visiting, depth + 1));
                } catch (IllegalAccessException | InvocationTargetException exception) {
                    throw new IllegalArgumentException("Cannot snapshot record component: " + component.getName(), exception);
                }
            }
            return Collections.unmodifiableMap(result);
        } finally {
            visiting.remove(record);
        }
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cyclic host object graph cannot be exposed to JavaScript: " + value.getClass().getName());
        }
    }

    private static IllegalArgumentException unsupportedHostValue(Object value) {
        return new IllegalArgumentException("Host object type is not allowed in JavaScript bindings: " + value.getClass().getName());
    }

    private static Map<String, List<Method>> exportedMethods(Class<?> type) {
        Map<String, List<Method>> result = new LinkedHashMap<>();
        for (Method method : type.getMethods()) {
            if (method.isBridge() || method.isSynthetic() || !Modifier.isPublic(method.getModifiers()) || !isExported(method)) {
                continue;
            }
            try {
                method.setAccessible(true);
            } catch (RuntimeException ignored) {
            }
            result.computeIfAbsent(method.getName(), ignored -> new ArrayList<>()).add(method);
        }
        return result;
    }

    private static Map<String, Field> exportedFields(Class<?> type) {
        Map<String, Field> result = new LinkedHashMap<>();
        for (Field field : type.getFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || !isExported(field)) {
                continue;
            }
            try {
                field.setAccessible(true);
            } catch (RuntimeException ignored) {
            }
            result.put(field.getName(), field);
        }
        return result;
    }

    private static boolean isExported(java.lang.reflect.AnnotatedElement element) {
        for (Annotation annotation : element.getAnnotations()) {
            if (HOST_ACCESS_EXPORT.equals(annotation.annotationType().getName())) {
                return true;
            }
        }
        return false;
    }

    private static Object convert(Value value, Class<?> expectedType) {
        if (expectedType == Value.class) {
            return value;
        }
        if (value == null || value.isNull()) {
            return defaultValue(expectedType);
        }
        if (expectedType == String.class) {
            return value.isString() ? value.asString() : Texts.toStringSafe(detach(value));
        }
        if (expectedType == boolean.class || expectedType == Boolean.class) {
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            return Boolean.parseBoolean(Texts.toStringSafe(detach(value)));
        }
        if (expectedType == int.class || expectedType == Integer.class) {
            return number(value).intValue();
        }
        if (expectedType == long.class || expectedType == Long.class) {
            return number(value).longValue();
        }
        if (expectedType == double.class || expectedType == Double.class) {
            return number(value).doubleValue();
        }
        if (expectedType == float.class || expectedType == Float.class) {
            return number(value).floatValue();
        }
        if (expectedType == short.class || expectedType == Short.class) {
            return number(value).shortValue();
        }
        if (expectedType == byte.class || expectedType == Byte.class) {
            return number(value).byteValue();
        }
        if (Map.class.isAssignableFrom(expectedType)) {
            Object detached = detach(value);
            if (detached instanceof Map<?, ?> map) {
                return map;
            }
            throw new IllegalArgumentException("Expected object/map argument but got: " + value);
        }
        if (List.class.isAssignableFrom(expectedType) || Iterable.class.isAssignableFrom(expectedType)) {
            Object detached = detach(value);
            if (detached instanceof List<?> list) {
                return list;
            }
            throw new IllegalArgumentException("Expected array/list argument but got: " + value);
        }
        Object detached = detach(value);
        if (detached == null || expectedType == Object.class || expectedType.isInstance(detached)) {
            return detached;
        }
        throw new IllegalArgumentException("Cannot convert script argument to " + expectedType.getName() + ": " + value);
    }

    private static Object defaultValue(Class<?> expectedType) {
        if (!expectedType.isPrimitive()) {
            return null;
        }
        if (expectedType == boolean.class) {
            return false;
        }
        if (expectedType == char.class) {
            return '\0';
        }
        if (expectedType == byte.class) {
            return (byte) 0;
        }
        if (expectedType == short.class) {
            return (short) 0;
        }
        if (expectedType == int.class) {
            return 0;
        }
        if (expectedType == long.class) {
            return 0L;
        }
        if (expectedType == float.class) {
            return 0F;
        }
        if (expectedType == double.class) {
            return 0D;
        }
        return null;
    }

    private static Number number(Value value) {
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        try {
            return Double.parseDouble(Texts.toStringSafe(detach(value)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected number argument but got: " + value, exception);
        }
    }

    private static Object detach(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return snapshotValue(value.asHostObject());
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasArrayElements()) {
            List<Object> result = new ArrayList<>();
            long size = value.getArraySize();
            if (size > MAX_COLLECTION_SIZE) {
                throw new IllegalArgumentException("Script array argument exceeds maximum size " + MAX_COLLECTION_SIZE + ".");
            }
            for (long index = 0; index < size; index++) {
                result.add(detach(value.getArrayElement(index)));
            }
            return Collections.unmodifiableList(result);
        }
        if (value.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                if (result.size() >= MAX_COLLECTION_SIZE) {
                    throw new IllegalArgumentException("Script object argument exceeds maximum size " + MAX_COLLECTION_SIZE + ".");
                }
                result.put(key, detach(value.getMember(key)));
            }
            return Collections.unmodifiableMap(result);
        }
        return value.toString();
    }

    private static String safeString(java.util.function.Supplier<String> supplier) {
        String value = safeObject(supplier);
        return value == null ? "" : value;
    }

    private static boolean safeBoolean(BooleanSupplier supplier) {
        try {
            return supplier.getAsBoolean();
        } catch (RuntimeException | LinkageError exception) {
            return false;
        }
    }

    private static int safeInt(IntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (RuntimeException | LinkageError exception) {
            return 0;
        }
    }

    private static double safeDouble(DoubleSupplier supplier) {
        try {
            return supplier.getAsDouble();
        } catch (RuntimeException | LinkageError exception) {
            return 0D;
        }
    }

    private static <T> T safeObject(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException | LinkageError exception) {
            return null;
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    @FunctionalInterface
    private interface IntSupplier {
        int getAsInt();
    }

    @FunctionalInterface
    private interface DoubleSupplier {
        double getAsDouble();
    }
}
