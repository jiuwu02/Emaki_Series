package emaki.jiuwu.craft.corelib.script.bridge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record PayloadResult<T>(@Nullable T value, @Nullable String error) {

    static <T> @NotNull PayloadResult<T> ok(@NotNull T value) {
        return new PayloadResult<>(value, null);
    }

    static <T> @NotNull PayloadResult<T> fail(@NotNull String error) {
        return new PayloadResult<>(null, error);
    }

    boolean failed() {
        return error != null;
    }
}
