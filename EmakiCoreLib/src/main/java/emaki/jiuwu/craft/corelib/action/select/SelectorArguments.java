package emaki.jiuwu.craft.corelib.action.select;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class SelectorArguments {

    public static final String SELECTOR_NAME_KEY = "name";

    public sealed interface Result {

        record Merged(@NotNull Map<String, String> values) implements Result {
        }

        record UnknownArgument(@NotNull String key) implements Result {
        }
    }

    private SelectorArguments() {
    }

    public static @NotNull Result merge(@Nullable Map<String, String> configured,
            @Nullable Map<String, String> inline,
            @Nullable Collection<String> declared) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (configured != null) {
            merged.putAll(configured);
        }
        if (inline == null || inline.isEmpty()) {
            return new Result.Merged(Map.copyOf(merged));
        }
        Collection<String> names = declared == null ? List.of() : declared;
        for (Map.Entry<String, String> entry : inline.entrySet()) {
            String key = Texts.lower(entry.getKey());
            if (SELECTOR_NAME_KEY.equals(key) || Texts.isBlank(key)) {
                continue;
            }
            if (!names.contains(key)) {
                return new Result.UnknownArgument(key);
            }
            merged.put(key, Texts.toStringSafe(entry.getValue()));
        }
        return new Result.Merged(Map.copyOf(merged));
    }
}