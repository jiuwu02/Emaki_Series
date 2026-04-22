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

import emaki.jiuwu.craft.attribute.model.AttributeSnapshot;
import emaki.jiuwu.craft.attribute.model.DamageContext;
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
            return "<null>";
        }
        return "attacker=" + entityDebugLabel(damageContext.attacker())
                + ", target=" + entityDebugLabel(damageContext.target())
                + ", projectile=" + entityDebugLabel(damageContext.projectile())
                + ", cause=" + (damageContext.cause() == null ? "<none>" : damageContext.cause().name())
                + ", damageType=" + damageContext.damageTypeId()
                + ", sourceDamage=" + formatNumber(damageContext.sourceDamage())
                + ", baseDamage=" + formatNumber(damageContext.baseDamage());
    }

    String formatStageValues(Map<String, Double> stageValues) {
        if (stageValues == null || stageValues.isEmpty()) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> entry : stageValues.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(formatNumber(entry.getValue() == null ? 0D : entry.getValue()));
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    String formatSnapshot(AttributeSnapshot snapshot) {
        if (snapshot == null) {
            return "<null>";
        }
        if (snapshot.values().isEmpty()) {
            return "signature=" + snapshot.sourceSignature() + ", values={}";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("signature=").append(snapshot.sourceSignature()).append(", values={");
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
            builder.append('}');
            return builder.toString();
        }
        builder.append('}');
        return builder.toString();
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
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    String entityDebugLabel(Entity entity) {
        if (entity == null) {
            return "<none>";
        }
        String name = Texts.toStringSafe(entity.getName()).trim();
        if (Texts.isBlank(name)) {
            name = entity.getType().name();
        }
        return name + "(" + entity.getType().name() + "," + entity.getUniqueId() + ")";
    }

    String formatNumber(double value) {
        return Numbers.formatNumber(value, "0.##");
    }
}
