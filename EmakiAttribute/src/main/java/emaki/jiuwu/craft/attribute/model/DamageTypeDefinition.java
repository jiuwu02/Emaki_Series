package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.text.Texts;

public record DamageTypeDefinition(String id,
        String displayName,
        List<String> aliases,
        Set<String> allowedEvents,
        boolean hardLock,
        List<DamageStageDefinition> stages,
        RecoveryDefinition recovery,
        String description,
        String attackerMessage,
        String targetMessage) {

    public DamageTypeDefinition          {
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
        recovery = recovery == null ? null : recovery;
        description = Texts.toStringSafe(description).trim();
        attackerMessage = Texts.toStringSafe(attackerMessage).trim();
        targetMessage = Texts.toStringSafe(targetMessage).trim();
    }

    public DamageTypeDefinition(String id,
            String displayName,
            List<String> aliases,
            Set<String> allowedEvents,
            boolean hardLock,
            List<DamageStageDefinition> stages,
            RecoveryDefinition recovery,
            String description) {
        this(id, displayName, aliases, allowedEvents, hardLock, stages, recovery, description, null, null);
    }

    public DamageTypeDefinition(String id,
            String displayName,
            List<String> aliases,
            Set<String> allowedEvents,
            boolean hardLock,
            List<DamageStageDefinition> stages,
            String description) {
        this(id, displayName, aliases, allowedEvents, hardLock, stages, null, description, null, null);
    }

    public static DamageTypeDefinition fromMap(Object raw) {
        return fromMap(raw, null);
    }

    public static DamageTypeDefinition fromMap(Object raw, Function<String, String> attributeNormalizer) {
        if (raw == null) {
            return null;
        }
        List<DamageStageDefinition> stages = new java.util.ArrayList<>();
        for (Object entry : ConfigNodes.asObjectList(ConfigNodes.get(raw, "stages"))) {
            DamageStageDefinition stage = DamageStageDefinition.fromMap(entry, attributeNormalizer);
            if (stage != null) {
                stages.add(stage);
            }
        }
        RecoveryDefinition recovery = RecoveryDefinition.fromMap(ConfigNodes.get(raw, "recovery"), attributeNormalizer);
        Set<String> allowedEvents = new LinkedHashSet<>();
        for (Object event : ConfigNodes.asObjectList(ConfigNodes.get(raw, "allowed_events"))) {
            String normalized = normalizeId(Texts.toStringSafe(event));
            if (!normalized.isBlank()) {
                allowedEvents.add(normalized);
            }
        }
        String sharedMessage = ConfigNodes.string(raw, "message", null);
        String attackerMessage = ConfigNodes.string(raw, "attacker_message", sharedMessage);
        String targetMessage = ConfigNodes.string(raw, "target_message", sharedMessage);
        return new DamageTypeDefinition(
                ConfigNodes.string(raw, "id", null),
                ConfigNodes.string(raw, "display_name", null),
                Texts.asStringList(ConfigNodes.get(raw, "aliases")),
                allowedEvents,
                ConfigNodes.bool(raw, "hard_lock", false),
                stages,
                recovery,
                ConfigNodes.string(raw, "description", null),
                attackerMessage,
                targetMessage
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

    public boolean hasAttackerMessage() {
        return !Texts.isBlank(attackerMessage);
    }

    public boolean hasTargetMessage() {
        return !Texts.isBlank(targetMessage);
    }

    public boolean hasRecovery() {
        return recovery != null;
    }

    private static String normalizeId(String value) {
        return Texts.toStringSafe(value).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
