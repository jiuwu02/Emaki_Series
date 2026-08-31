package emaki.jiuwu.craft.codex.advancement.model;

import java.util.List;
import java.util.Map;

public record AdvancementPage(String pageId,
        String title,
        String background,
        String rootId,
        Map<String, AdvancementDefinition> advancements) {

    public AdvancementPage {
        advancements = advancements == null ? Map.of() : Map.copyOf(advancements);
    }

    public AdvancementDefinition root() {
        return rootId == null ? null : advancements.get(rootId);
    }

    public List<AdvancementDefinition> definitions() {
        return List.copyOf(advancements.values());
    }
}
