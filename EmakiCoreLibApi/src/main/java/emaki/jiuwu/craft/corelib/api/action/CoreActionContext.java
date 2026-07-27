package emaki.jiuwu.craft.corelib.api.action;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stable execution context passed to third-party CoreLib actions.
 */
public final class CoreActionContext {

    private final Plugin sourcePlugin;
    private final Player player;
    private final String phase;
    private final boolean silent;
    private final Map<String, String> placeholders;
    private final Map<String, Object> attributes;
    private final Map<String, Object> sharedState;

    public CoreActionContext(@Nullable Plugin sourcePlugin,
            @Nullable Player player,
            @Nullable String phase,
            boolean silent,
            @Nullable Map<String, String> placeholders,
            @Nullable Map<String, Object> attributes) {
        this(sourcePlugin, player, phase, silent, placeholders, attributes, new ConcurrentHashMap<>());
    }

    public CoreActionContext(@Nullable Plugin sourcePlugin,
            @Nullable Player player,
            @Nullable String phase,
            boolean silent,
            @Nullable Map<String, String> placeholders,
            @Nullable Map<String, Object> attributes,
            @Nullable Map<String, Object> sharedState) {
        this.sourcePlugin = sourcePlugin;
        this.player = player;
        this.phase = isBlank(phase) ? "default" : phase.trim();
        this.silent = silent;
        this.placeholders = placeholders == null ? Map.of() : Map.copyOf(placeholders);
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.sharedState = sharedState == null ? new ConcurrentHashMap<>() : sharedState;
    }

    public static @NotNull CoreActionContext create(@Nullable Plugin sourcePlugin,
            @Nullable Player player,
            @Nullable String phase,
            boolean silent) {
        return new CoreActionContext(sourcePlugin, player, phase, silent, Map.of(), Map.of());
    }

    public @Nullable Plugin sourcePlugin() {
        return sourcePlugin;
    }

    public @Nullable Player player() {
        return player;
    }

    public @NotNull String phase() {
        return phase;
    }

    public boolean silent() {
        return silent;
    }

    public @NotNull Map<String, String> placeholders() {
        return placeholders;
    }

    public @NotNull Map<String, Object> attributes() {
        return attributes;
    }

    public @NotNull Map<String, Object> sharedState() {
        return sharedState;
    }

    public @Nullable String placeholder(@Nullable String key) {
        return placeholders.get(lower(key));
    }

    public @Nullable Object attribute(@Nullable String key) {
        return attributes.get(key);
    }

    public @Nullable Object sharedValue(@Nullable String key) {
        return sharedState.get(key);
    }

    public @NotNull CoreActionContext withPhase(@Nullable String value) {
        return new CoreActionContext(sourcePlugin, player, value, silent, placeholders, attributes, sharedState);
    }

    public @NotNull CoreActionContext withPlaceholder(@Nullable String key, @Nullable Object value) {
        Map<String, String> copy = new LinkedHashMap<>(placeholders);
        copy.put(lower(key), value == null ? "" : String.valueOf(value));
        return new CoreActionContext(sourcePlugin, player, phase, silent, copy, attributes, sharedState);
    }

    public @NotNull CoreActionContext withPlaceholders(@Nullable Map<String, ?> values) {
        Map<String, String> copy = new LinkedHashMap<>(placeholders);
        if (values != null) {
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                copy.put(lower(entry.getKey()), entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }
        return new CoreActionContext(sourcePlugin, player, phase, silent, copy, attributes, sharedState);
    }

    public @NotNull CoreActionContext withAttribute(@Nullable String key, @Nullable Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(attributes);
        copy.put(key, value);
        return new CoreActionContext(sourcePlugin, player, phase, silent, placeholders, copy, sharedState);
    }

    public @NotNull CoreActionContext withAttributes(@Nullable Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>(attributes);
        if (values != null) {
            copy.putAll(values);
        }
        return new CoreActionContext(sourcePlugin, player, phase, silent, placeholders, copy, sharedState);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
