package emaki.jiuwu.craft.attribute.service;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

import emaki.jiuwu.craft.attribute.api.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

final class CombatDebugService {

    private final AttributeService service;
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();

    CombatDebugService(AttributeService service) {
        this.service = service;
    }

    public boolean toggle(Player player) {
        if (player == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (trackedPlayers.remove(playerId)) {
            return false;
        }
        trackedPlayers.add(playerId);
        return true;
    }

    public boolean setEnabled(Player player, boolean enabled) {
        if (player == null) {
            return false;
        }
        if (enabled) {
            trackedPlayers.add(player.getUniqueId());
            return true;
        }
        trackedPlayers.remove(player.getUniqueId());
        return false;
    }

    public boolean isEnabled(Player player) {
        return player != null && trackedPlayers.contains(player.getUniqueId());
    }

    public boolean isEnabled(Entity entity) {
        return entity instanceof Player player && isEnabled(player);
    }

    public boolean shouldTrace(LivingEntity attacker, LivingEntity target) {
        return isEnabled(attacker) || isEnabled(target);
    }

    public boolean shouldTrace(Projectile projectile, LivingEntity target) {
        Entity shooter = projectile == null ? null : projectile.getShooter() instanceof Entity entity ? entity : null;
        return shouldTrace(shooter instanceof LivingEntity livingEntity ? livingEntity : null, target);
    }

    public void log(String phase, String message) {
        if (service == null || service.plugin() == null) {
            return;
        }
        String safePhase = phase == null || phase.isBlank() ? "TRACE" : phase;
        String safeMessage = message == null ? "" : message;
        if (service.plugin().messageService() != null) {
            service.plugin().messageService().info("console.combat_debug_line", Map.of(
                    "phase", safePhase,
                    "message", safeMessage
            ));
            return;
        }
        service.plugin().getLogger().info("[CombatDebug][" + safePhase + "] " + safeMessage);
    }

    public void logMessage(String phase, String messageKey, Map<String, ?> replacements) {
        if (service == null || service.plugin() == null || service.plugin().messageService() == null) {
            log(phase, messageKey);
            return;
        }
        log(phase, service.plugin().messageService().message(messageKey, replacements == null ? Map.of() : replacements));
    }

    String describeDamageContext(DamageContext damageContext) {
        if (damageContext == null) {
            return fieldText("null_value", "<null>");
        }
        return fieldMessage("context", Map.of(
                "attacker", entityDebugLabel(damageContext.attacker()),
                "target", entityDebugLabel(damageContext.target()),
                "projectile", entityDebugLabel(damageContext.projectile()),
                "cause", damageContext.cause() == null
                        ? fieldText("none", "<none>")
                        : damageContext.cause().name(),
                "damage_type", Texts.toStringSafe(damageContext.damageTypeId()),
                "source_damage", formatNumber(damageContext.sourceDamage()),
                "base_damage", formatNumber(damageContext.baseDamage())
        ));
    }

    String formatStageValues(Map<String, Double> stageValues) {
        if (stageValues == null || stageValues.isEmpty()) {
            return fieldMessage("stage_values", Map.of("values", ""));
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Double> entry : stageValues.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(formatNumber(entry.getValue() == null ? 0D : entry.getValue()));
            first = false;
        }
        return fieldMessage("stage_values", Map.of("values", builder.toString()));
    }

    String formatSnapshot(AttributeSnapshot snapshot) {
        if (snapshot == null) {
            return fieldText("null_value", "<null>");
        }
        if (snapshot.values().isEmpty()) {
            return snapshotMessage(snapshot, "");
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Double> entry : orderedSnapshotEntries(snapshot).entrySet()) {
            Double value = entry.getValue();
            if (value == null || Double.compare(value, 0D) == 0) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(formatNumber(value));
            first = false;
        }
        if (first) {
            return snapshotMessage(snapshot, "");
        }
        return snapshotMessage(snapshot, builder.toString());
    }

    Map<String, Double> orderedSnapshotEntries(AttributeSnapshot snapshot) {
        if (snapshot == null || snapshot.values().isEmpty()) {
            return Map.of();
        }
        Map<String, Double> ordered = new LinkedHashMap<>();
        for (var definition : service.attributeRegistry().all().values()) {
            if (definition == null) {
                continue;
            }
            Double value = snapshot.values().get(definition.id());
            if (value != null) {
                ordered.put(definition.id(), value);
            }
        }
        for (Map.Entry<String, Double> entry : snapshot.values().entrySet()) {
            if (AttributeSnapshot.isRangeSpreadKey(entry.getKey())) {
                continue;
            }
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    String entityDebugLabel(Entity entity) {
        if (entity == null) {
            return fieldText("none", "<none>");
        }
        String name = Texts.toStringSafe(entity.getName()).trim();
        if (Texts.isBlank(name)) {
            name = entity.getType().name();
        }
        return fieldMessage("entity", Map.of(
                "name", name,
                "type", entity.getType().name(),
                "uuid", entity.getUniqueId().toString()
        ));
    }

    String formatNumber(double value) {
        return Numbers.formatNumber(value, "0.##");
    }

    private String snapshotMessage(AttributeSnapshot snapshot, String values) {
        return fieldMessage("snapshot", Map.of(
                "signature", Texts.toStringSafe(snapshot.sourceSignature()),
                "values", values
        ));
    }

    private String fieldMessage(String field, Map<String, ?> replacements) {
        MessageService messageService = service == null || service.plugin() == null
                ? null
                : service.plugin().messageService();
        String key = "combat_debug.field." + field;
        if (messageService == null) {
            return key;
        }
        return messageService.message(key, replacements);
    }

    private String fieldText(String field, String fallback) {
        MessageService messageService = service == null || service.plugin() == null
                ? null
                : service.plugin().messageService();
        if (messageService == null) {
            return fallback;
        }
        return messageService.messageOrFallback("combat_debug.field." + field, fallback);
    }
}
