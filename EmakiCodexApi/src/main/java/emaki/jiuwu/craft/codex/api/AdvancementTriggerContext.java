package emaki.jiuwu.craft.codex.api;

import java.util.Map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Snapshot supplied to registered advancement-trigger providers. */
public record AdvancementTriggerContext(@NotNull Player player,
                                        @NotNull String triggerId,
                                        @NotNull Map<String, Object> variables) {
    public AdvancementTriggerContext {
        if (player == null) throw new NullPointerException("player");
        triggerId = triggerId == null ? "" : triggerId;
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
