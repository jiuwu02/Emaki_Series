package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.text.Texts;

abstract class AbstractReflectiveAccessor {

    private boolean initialized;
    private boolean available;
    private String failureReason = "";

    public final synchronized boolean ensureAvailable() {
        if (!preEnsureAvailable()) {
            return false;
        }
        if (initialized) {
            return available;
        }
        initialized = true;
        try {
            initializeBindings();
            available = true;
            failureReason = "";
        } catch (Exception | LinkageError exception) {
            available = false;
            failureReason = exception.getClass().getSimpleName() + ": " + Texts.toStringSafe(exception.getMessage());
            resetBindings();
        }
        return available;
    }

    protected boolean preEnsureAvailable() {
        return true;
    }

    public final String failureReason() {
        return failureReason;
    }

    public final synchronized void reset() {
        initialized = false;
        available = false;
        failureReason = "";
        resetBindings();
    }

    protected abstract void initializeBindings() throws Exception;

    protected abstract void resetBindings();

    protected final Method getOptionalMethod(Class<?> type, String methodName, Class<?>... parameterTypes) {
        if (type == null) {
            return null;
        }
        try {
            return type.getMethod(methodName, parameterTypes);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
    }

    protected final Field getOptionalField(Class<?> type, String fieldName) {
        if (type == null) {
            return null;
        }
        try {
            return type.getField(fieldName);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
    }

    protected final Object invoke(Method method, Object target, Object... arguments) {
        if (method == null || target == null && !Modifier.isStatic(method.getModifiers())) {
            return null;
        }
        try {
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
    }

    protected final Object readField(Field field, Object target) {
        if (field == null || target == null && !Modifier.isStatic(field.getModifiers())) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            return null;
        }
    }

    protected final Object readStaticField(Field field) {
        return readField(field, null);
    }

    protected final ItemStack asItemStack(Object value) {
        return value instanceof ItemStack itemStack ? itemStack : null;
    }
}
