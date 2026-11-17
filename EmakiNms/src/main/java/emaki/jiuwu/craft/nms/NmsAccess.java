package emaki.jiuwu.craft.nms;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NmsAccess {

    private static final Map<String, Class<?>> TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Constructor<?>> CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();

    private NmsAccess() {
    }

    public static Class<?> typeOrNull(String typeName) {
        return TYPE_CACHE.computeIfAbsent(typeName, NmsAccess::forName);
    }

    public static Class<?> requireType(String typeName) {
        Class<?> resolved = typeOrNull(typeName);
        if (resolved == null) {
            throw new IllegalStateException("Missing runtime type " + typeName);
        }
        return resolved;
    }

    public static Class<?>[] signature(String... typeNames) {
        Class<?>[] resolved = new Class<?>[typeNames.length];
        for (int index = 0; index < typeNames.length; index++) {
            resolved[index] = primitiveOrNull(typeNames[index]);
            if (resolved[index] == null) {
                resolved[index] = requireType(typeNames[index]);
            }
        }
        return resolved;
    }

    public static Method methodOrNull(String typeName, String methodName, String... typeNames) {
        Class<?> owner = typeOrNull(typeName);
        if (owner == null) {
            return null;
        }
        return methodOrNull(owner, methodName, typeNames);
    }

    public static Method methodOrNull(Class<?> owner, String methodName, String... typeNames) {
        Class<?>[] parameterTypes = new Class<?>[typeNames.length];
        for (int index = 0; index < typeNames.length; index++) {
            parameterTypes[index] = primitiveOrNull(typeNames[index]);
            if (parameterTypes[index] == null) {
                parameterTypes[index] = typeOrNull(typeNames[index]);
                if (parameterTypes[index] == null) {
                    return null;
                }
            }
        }
        return methodOrNull(owner, methodName, parameterTypes);
    }

    public static Method requireMethod(String typeName, String methodName, String... typeNames) {
        Method resolved = methodOrNull(typeName, methodName, typeNames);
        if (resolved == null) {
            throw new IllegalStateException("Missing runtime member " + typeName + "." + methodName + descriptor(typeNames));
        }
        return resolved;
    }

    public static Method requireMethod(Class<?> owner, String methodName, String... typeNames) {
        Class<?>[] parameterTypes = signature(typeNames);
        Method resolved = methodOrNull(owner, methodName, parameterTypes);
        if (resolved == null) {
            throw new IllegalStateException("Missing runtime member " + owner.getName() + "." + methodName + descriptor(typeNames));
        }
        return resolved;
    }

    public static Object call(Object receiver, Method method, Object... arguments) {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException invocationFailure) {
            throw rethrow(invocationFailure.getCause());
        } catch (IllegalAccessException accessFailure) {
            throw new IllegalStateException("Cannot access runtime member " + method, accessFailure);
        }
    }

    public static Object callStatic(Method method, Object... arguments) {
        return call(null, method, arguments);
    }

    public static Object readField(Object receiver, String fieldName) {
        Field field = fieldOrNull(receiver.getClass(), fieldName);
        if (field == null) {
            throw new IllegalStateException("Missing runtime field " + receiver.getClass().getName() + "." + fieldName);
        }
        return readFieldValue(field, receiver);
    }

    public static Object readStaticField(String typeName, String fieldName) {
        Field field = fieldOrNull(requireType(typeName), fieldName);
        if (field == null) {
            throw new IllegalStateException("Missing runtime field " + typeName + "." + fieldName);
        }
        return readFieldValue(field, null);
    }

    public static void writeField(Object receiver, String fieldName, Object value) {
        Field field = fieldOrNull(receiver.getClass(), fieldName);
        if (field == null) {
            throw new IllegalStateException("Missing runtime field " + receiver.getClass().getName() + "." + fieldName);
        }
        writeFieldValue(field, receiver, value);
    }

    public static void writeFieldValue(Field field, Object receiver, Object value) {
        try {
            field.set(receiver, value);
        } catch (IllegalAccessException accessFailure) {
            throw new IllegalStateException("Cannot write runtime field " + field, accessFailure);
        }
    }

    public static Object readFieldValue(Field field, Object receiver) {
        try {
            return field.get(receiver);
        } catch (IllegalAccessException accessFailure) {
            throw new IllegalStateException("Cannot read runtime field " + field, accessFailure);
        }
    }

    public static Field fieldOrNull(Class<?> owner, String fieldName) {
        String key = owner.getName() + "#" + fieldName;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field resolved = cursor.getDeclaredField(fieldName);
                resolved.setAccessible(true);
                FIELD_CACHE.put(key, resolved);
                return resolved;
            } catch (NoSuchFieldException absent) {
                continue;
            }
        }
        return null;
    }

    public static Object construct(String typeName, String[] typeNames, Object... arguments) {
        Class<?> owner = requireType(typeName);
        Class<?>[] parameterTypes = signature(typeNames);
        String key = owner.getName() + descriptor(typeNames);
        Constructor<?> constructor = CONSTRUCTOR_CACHE.get(key);
        if (constructor == null) {
            try {
                constructor = owner.getDeclaredConstructor(parameterTypes);
                constructor.setAccessible(true);
                CONSTRUCTOR_CACHE.put(key, constructor);
            } catch (NoSuchMethodException absent) {
                throw new IllegalStateException("Missing runtime constructor " + typeName + descriptor(typeNames), absent);
            }
        }
        try {
            return constructor.newInstance(arguments);
        } catch (InvocationTargetException invocationFailure) {
            throw rethrow(invocationFailure.getCause());
        } catch (InstantiationException | IllegalAccessException constructionFailure) {
            throw new IllegalStateException("Cannot construct runtime type " + typeName, constructionFailure);
        }
    }

    private static Method methodOrNull(Class<?> owner, String methodName, Class<?>[] parameterTypes) {
        String key = owner.getName() + "#" + methodName + descriptor(parameterTypes);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Method resolved = findMethod(owner, methodName, parameterTypes);
        if (resolved != null) {
            resolved.setAccessible(true);
            METHOD_CACHE.put(key, resolved);
        }
        return resolved;
    }

    private static Method findMethod(Class<?> owner, String methodName, Class<?>[] parameterTypes) {
        for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                return cursor.getDeclaredMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException absent) {
                continue;
            }
        }
        for (Class<?> cursor = owner; cursor != null; cursor = cursor.getSuperclass()) {
            for (Class<?> implemented : cursor.getInterfaces()) {
                Method resolved = findMethod(implemented, methodName, parameterTypes);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    private static Class<?> forName(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    private static Class<?> primitiveOrNull(String typeName) {
        return switch (typeName) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "void" -> void.class;
            default -> null;
        };
    }

    private static String descriptor(String... typeNames) {
        StringBuilder rendered = new StringBuilder("(");
        for (String typeName : typeNames) {
            rendered.append(typeName).append(", ");
        }
        if (typeNames.length > 0) {
            rendered.setLength(rendered.length() - 2);
        }
        return rendered.append(")").toString();
    }

    private static String descriptor(Class<?>... parameterTypes) {
        String[] typeNames = new String[parameterTypes.length];
        for (int index = 0; index < parameterTypes.length; index++) {
            typeNames[index] = parameterTypes[index].getName();
        }
        return descriptor(typeNames);
    }

    private static RuntimeException rethrow(Throwable cause) {
        if (cause instanceof RuntimeException runtimeFailure) {
            return runtimeFailure;
        }
        if (cause instanceof Error errorFailure) {
            throw errorFailure;
        }
        return new IllegalStateException("Runtime member invocation failed: " + cause, cause);
    }
}
