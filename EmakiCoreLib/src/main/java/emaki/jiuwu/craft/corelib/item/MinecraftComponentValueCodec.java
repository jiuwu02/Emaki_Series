package emaki.jiuwu.craft.corelib.item;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import net.kyori.adventure.text.Component;


public final class MinecraftComponentValueCodec {

    private static final Logger LOGGER = Logger.getLogger(MinecraftComponentValueCodec.class.getName());

    private final AtomicBoolean gsonWarningLogged = new AtomicBoolean();

    private volatile Object gsonSerializer;
    private volatile Method gsonSerializeMethod;

    public String encode(String componentId, Object value, boolean nonValued) {
        if (nonValued) {
            if (value == null || Boolean.TRUE.equals(value) || value instanceof Map<?, ?> map && map.isEmpty()) {
                return "{}";
            }
            throw new IllegalArgumentException("Non-valued component " + componentId + " accepts only true, null, or an empty map.");
        }
        if ("minecraft:custom_name".equals(componentId) || "minecraft:item_name".equals(componentId)) {
            return encodeTextComponent(value);
        }
        if ("minecraft:lore".equals(componentId)) {
            return encodeLore(value);
        }
        return encodeGeneric(value);
    }

    private String encodeTextComponent(Object value) {
        if (value instanceof String text) {
            return serializeComponent(MiniMessages.parse(text));
        }
        return encodeGeneric(value);
    }

    private String encodeLore(Object value) {
        if (value instanceof String text) {
            return "[" + serializeComponent(MiniMessages.parse(text)) + "]";
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder builder = new StringBuilder("[");
            Iterator<?> iterator = collection.iterator();
            while (iterator.hasNext()) {
                Object entry = iterator.next();
                builder.append(entry instanceof String text
                        ? serializeComponent(MiniMessages.parse(text))
                        : encodeGeneric(entry));
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            return builder.append(']').toString();
        }
        return encodeGeneric(value);
    }

    private String encodeGeneric(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Boolean bool) {
            return Boolean.toString(bool);
        }
        if (value instanceof Number number) {
            if (number instanceof Double doubleValue && !Double.isFinite(doubleValue)
                    || number instanceof Float floatValue && !Float.isFinite(floatValue)) {
                throw new IllegalArgumentException("Component numbers must be finite.");
            }
            return number.toString();
        }
        if (value instanceof Map<?, ?> map) {
            if (map.size() == 1 && map.containsKey("$snbt")) {
                Object raw = map.get("$snbt");
                if (!(raw instanceof String text) || text.isBlank()) {
                    throw new IllegalArgumentException("$snbt must contain a non-empty string.");
                }
                return text;
            }
            StringBuilder builder = new StringBuilder("{");
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                if (entry.getKey() == null) {
                    throw new IllegalArgumentException("Component maps cannot contain null keys.");
                }
                builder.append(quote(String.valueOf(entry.getKey())))
                        .append(':')
                        .append(encodeGeneric(entry.getValue()));
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            return builder.append('}').toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder builder = new StringBuilder("[");
            Iterator<?> iterator = collection.iterator();
            while (iterator.hasNext()) {
                builder.append(encodeGeneric(iterator.next()));
                if (iterator.hasNext()) {
                    builder.append(',');
                }
            }
            return builder.append(']').toString();
        }
        throw new IllegalArgumentException("Unsupported component value type: " + value.getClass().getName());
    }

    private String serializeComponent(Component component) {
        try {
            Object serializer = gsonSerializer;
            Method method = gsonSerializeMethod;
            if (serializer == null || method == null) {
                Class<?> serializerType = Class.forName("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
                serializer = serializerType.getMethod("gson").invoke(null);
                method = serializerType.getMethod("serialize", Component.class);
                gsonSerializer = serializer;
                gsonSerializeMethod = method;
            }
            Object serialized = method.invoke(serializer, component);
            if (serialized instanceof String text && !text.isBlank()) {
                return text;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            if (gsonWarningLogged.compareAndSet(false, true)) {
                LOGGER.warning("Adventure GsonComponentSerializer is unavailable, falling back to plain-text"
                        + " component encoding: " + describe(exception));
            }
        }
        return "{\"text\":" + quote(MiniMessages.plain(component)) + "}";
    }

    private String quote(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    private String describe(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
