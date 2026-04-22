package emaki.jiuwu.craft.skills.trigger;

import java.util.Collections;
import java.util.Set;

/**
 * Defines a single skill trigger with its metadata and conflict rules.
 *
 * @param id               unique trigger identifier
 * @param displayName      human-readable name shown in GUI
 * @param description      optional description (nullable)
 * @param enabled          whether this trigger is active
 * @param incompatibleWith immutable set of trigger ids that conflict with this one
 * @param material         optional Bukkit Material name for GUI icon (nullable)
 */
public record SkillTriggerDefinition(
        String id,
        String displayName,
        String description,
        boolean enabled,
        Set<String> incompatibleWith,
        String material
) {

    public SkillTriggerDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Trigger id must not be null or blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Trigger displayName must not be null or blank");
        }
        incompatibleWith = incompatibleWith == null
                ? Set.of()
                : Collections.unmodifiableSet(Set.copyOf(incompatibleWith));
    }
}
