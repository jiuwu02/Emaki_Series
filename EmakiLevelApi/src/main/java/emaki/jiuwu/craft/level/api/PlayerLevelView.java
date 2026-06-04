package emaki.jiuwu.craft.level.api;

import java.util.Map;
import java.util.UUID;

public record PlayerLevelView(UUID uuid,
        String name,
        Map<String, PlayerLevelEntryView> levels) {

    public PlayerLevelView {
        name = name == null ? "" : name;
        levels = levels == null ? Map.of() : Map.copyOf(levels);
    }
}
