package emaki.jiuwu.craft.corelib.script;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * 通过 Graal 代理成员暴露项目脚本 API 宿主对象。
 *
 * <p>Bukkit 插件类加载器可能让依赖插件类上的 {@code HostAccess.Export}
 * 注解无法被 CoreLib 创建的 Graal 上下文直接识别。该代理只按
 * HostAccess.Export 注解名称导出成员，避免放宽到 {@code HostAccess.ALL}
 * 或全量 public host access。</p>
 */
public final class ScriptHostObjectProxy implements ProxyObject {

    private static final String HOST_ACCESS_EXPORT = "org.graalvm.polyglot.HostAccess$Export";

    private final Object target;
    private final Map<String, List<Method>> methods;
    private final Map<String, Field> fields;

    private ScriptHostObjectProxy(Object target, Map<String, List<Method>> methods, Map<String, Field> fields) {
        this.target = target;
        this.methods = methods;
        this.fields = fields;
    }

    public static Object wrapIfExported(Object value) {
        if (value == null
                || value instanceof ProxyObject
                || value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Map<?, ?>
                || value instanceof Iterable<?>
                || value.getClass().isArray()
                || value.getClass().isEnum()) {
            return value;
        }
        Map<String, List<Method>> exportedMethods = exportedMethods(value.getClass());
        Map<String, Field> exportedFields = exportedFields(value.getClass());
        if (exportedMethods.isEmpty() && exportedFields.isEmpty()) {
            return value;
        }
        return new ScriptHostObjectProxy(value, exportedMethods, exportedFields);
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
        if (value.isHostObject()) {
            Object hostObject = value.asHostObject();
            if (expectedType.isInstance(hostObject) || expectedType == Object.class) {
                return hostObject;
            }
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
            return value.asHostObject();
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
            for (long index = 0; index < size; index++) {
                result.add(detach(value.getArrayElement(index)));
            }
            return result;
        }
        if (value.hasMembers()) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (String key : value.getMemberKeys()) {
                result.put(key, detach(value.getMember(key)));
            }
            return result;
        }
        return value.toString();
    }
}
