package emaki.jiuwu.craft.level.api;

import java.util.List;
import java.util.Map;

public record LevelTypeView(String id,
        String displayName,
        List<String> description,
        boolean primary,
        boolean enabled,
        int startLevel,
        int maxLevel,
        boolean autoUpgrade,
        boolean manualUpgrade,
        Map<String, String> attributes) {

    public LevelTypeView {
        id = id == null ? "" : id;
        displayName = displayName == null ? id : displayName;
        description = description == null ? List.of() : List.copyOf(description);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
