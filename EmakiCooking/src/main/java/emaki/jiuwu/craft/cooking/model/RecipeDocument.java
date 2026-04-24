package emaki.jiuwu.craft.cooking.model;

import java.util.Objects;

import emaki.jiuwu.craft.corelib.yaml.YamlSection;

public record RecipeDocument(String id,
        String displayName,
        StationType stationType,
        YamlSection configuration) {

    public RecipeDocument {
        id = id == null ? "" : id.trim();
        displayName = displayName == null || displayName.isBlank() ? id : displayName.trim();
        stationType = Objects.requireNonNull(stationType, "stationType");
        configuration = Objects.requireNonNull(configuration, "configuration");
    }
}
