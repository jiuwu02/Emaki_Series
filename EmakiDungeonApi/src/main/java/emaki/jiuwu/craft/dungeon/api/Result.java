package emaki.jiuwu.craft.dungeon.api;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

public record Result<T>(String code, String messageKey, Map<String, Object> args, T value) {

    public static <T> Result<T> ok(T value) {
        return new Result<>(null, "", Map.of(), value);
    }

    public static <T> Result<T> err(String code, String messageKey) {
        return new Result<>(code, messageKey, Map.of(), null);
    }

    public static <T> Result<T> err(String code, String messageKey, Map<String, Object> args) {
        return new Result<>(code, messageKey, args == null ? Map.of() : Map.copyOf(args), null);
    }

    public boolean isSuccess() {
        return code == null || code.isEmpty();
    }

    public @Nullable T value() {
        return value;
    }
}
