package emaki.jiuwu.craft.level.api;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Immutable gameplay-trigger context supplied to an {@link ExpSourceProvider}. */
public record ExpSourceContext(@NotNull Player player,
        @NotNull String trigger,
        @NotNull Map<String, Object> variables) {

    public ExpSourceContext {
        if (player == null) {
            throw new NullPointerException("player");
        }
        if (trigger == null) {
            throw new NullPointerException("trigger");
        }
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
