package emaki.jiuwu.craft.attribute.model;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record DamageTypeDefinition(String id,
                                   String displayName,
                                   List<String> aliases,
                                   Set<String> allowedEvents,
                                   boolean hardLock,
                                   List<DamageStageDefinition> stages,
                                   String description) {

    public DamageTypeDefinition {
        id = normalizeId(id);
        displayName = Texts.isBlank(displayName) ? id : Texts.toStringSafe(displayName).trim();
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        Set<String> normalizedEvents = new LinkedHashSet<>();
        if (allowedEvents != null) {
            for (String event : allowedEvents) {
                String normalized = normalizeId(event);
                if (!normalized.isBlank()) {
                    normalizedEvents.add(normalized);
                }
            }
        }
        allowedEvents = Set.copyOf(normalizedEvents);
        stages = stages == null ? List.of() : List.copyOf(stages);
        description = Texts.toStringSafe(description).trim();
    }

    public static DamageTypeDefinition fromMap(Object raw) {
        if (raw == null) {
            return null;
        }
        List<DamageStageDefinition> stages = new java.util.ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(ConfigNodes.get(raw, "stages"))) {
            DamageStageDefinition stage = DamageStageDefinition.fromMap(entry);
            if (stage != null) {
                stages.add(stage);
            }
        }
        Set<String> allowedEvents = new LinkedHashSet<>();
        for (Object event : ConfigNodes.asObjectList(ConfigNodes.get(raw, "allowed_events"))) {
            String normalized = normalizeId(Texts.toStringSafe(event));
            if (!normalized.isBlank()) {
                allowedEvents.add(normalized);
            }
        }
        return new DamageTypeDefinition(
            ConfigNodes.string(raw, "id", null),
            ConfigNodes.string(raw, "display_name", null),
            Texts.asStringList(ConfigNodes.get(raw, "aliases")),
            allowedEvents,
            ConfigNodes.bool(raw, "hard_lock", false),
            stages,
            ConfigNodes.string(raw, "description", null)
        );
    }

    public boolean matches(String candidate) {
        if (Texts.isBlank(candidate)) {
            return false;
        }
        String normalized = normalizeId(candidate);
        if (id.equals(normalized)) {
            return true;
        }
        for (String alias : aliases) {
            if (normalizeId(alias).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    public boolean allowsCause(String cause) {
        return allowedEvents.contains(normalizeId(cause));
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
