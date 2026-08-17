package emaki.jiuwu.craft.corelib.trigger;

import java.util.Collections;
import java.util.Set;

public record TriggerDefinition(
        String id,
        String displayName,
        String description,
        boolean enabled,
        Set<String> incompatibleWith,
        String material,
        TriggerCategory category
) {

    public TriggerDefinition(String id,
            String displayName,
            String description,
            boolean enabled,
            Set<String> incompatibleWith,
            String material) {
        this(id, displayName, description, enabled, incompatibleWith, material, TriggerCategory.ACTIVE);
    }

    public TriggerDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Trigger id must not be null or blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Trigger displayName must not be null or blank");
        }
        incompatibleWith = incompatibleWith == null
                ? Set.of()
                : Collections.unmodifiableSet(Set.copyOf(incompatibleWith));
        category = category == null ? TriggerCategory.ACTIVE : category;
    }
}
