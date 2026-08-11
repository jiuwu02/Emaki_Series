package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.attribute.api.model.DamageContext;
import emaki.jiuwu.craft.attribute.api.model.DamageResult;
import emaki.jiuwu.craft.attribute.model.DamageStageDefinition;
import emaki.jiuwu.craft.attribute.model.DamageTraceRecord;
import emaki.jiuwu.craft.attribute.model.DamageTraceStageRecord;
import emaki.jiuwu.craft.attribute.model.DamageTypeDefinition;
import emaki.jiuwu.craft.attribute.model.ResolvedDamage;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class DamageTraceService {

    private static final int MAX_RECORDS_PER_PLAYER = 20;
    private static final int MAX_TOTAL_RECORDS = 500;

    private final AtomicLong sequence = new AtomicLong();
    private final Map<UUID, Deque<DamageTraceRecord>> recordsByPlayer = new ConcurrentHashMap<>();

    public void record(ResolvedDamage resolvedDamage, String applyMode, boolean vanillaEventCancelled, boolean vanillaDamageRewritten, boolean applied, List<String> events) {
        if (resolvedDamage == null || resolvedDamage.damageContext() == null || resolvedDamage.damageResult() == null) {
            return;
        }
        DamageContext context = resolvedDamage.damageContext();
        if (!hasPlayerParticipant(context)) {
            return;
        }
        DamageTraceRecord record = toRecord(resolvedDamage, applyMode, vanillaEventCancelled, vanillaDamageRewritten, applied, events);
        addFor(context.attacker(), record);
        addFor(context.target(), record);
        trimTotal();
    }

    public List<DamageTraceRecord> list(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        Deque<DamageTraceRecord> records = recordsByPlayer.get(playerId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return List.copyOf(records);
    }

    public DamageTraceRecord last(UUID playerId) {
        List<DamageTraceRecord> records = list(playerId);
        return records.isEmpty() ? null : records.get(0);
    }

    public boolean clear(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        return recordsByPlayer.remove(playerId) != null;
    }

    private DamageTraceRecord toRecord(ResolvedDamage resolvedDamage, String applyMode, boolean vanillaEventCancelled, boolean vanillaDamageRewritten, boolean applied, List<String> events) {
        DamageContext context = resolvedDamage.damageContext();
        DamageResult result = resolvedDamage.damageResult();
        return new DamageTraceRecord(
                sequence.incrementAndGet(),
                System.currentTimeMillis(),
                id(context.attacker()),
                label(context.attacker()),
                id(context.target()),
                label(context.target()),
                id(context.projectile()),
                label(context.projectile()),
                result.damageTypeId(),
                context.cause() == null ? "" : context.cause().name(),
                context.sourceDamage(),
                context.baseDamage(),
                resolvedDamage.finalDamage(),
                result.critical(),
                resolvedDamage.finalDamage() <= 0D,
                vanillaEventCancelled,
                vanillaDamageRewritten,
                applied,
                Texts.isBlank(applyMode) ? "unknown" : applyMode,
                context.attackerSnapshot(),
                context.targetSnapshot(),
                context.variables().asMap(),
                stages(context.baseDamage(), result, resolvedDamage.damageType()),
                events == null ? List.of() : events
        );
    }

    private List<DamageTraceStageRecord> stages(double baseDamage, DamageResult result, DamageTypeDefinition damageType) {
        if (result == null || result.stageValues().isEmpty()) {
            return List.of();
        }
        List<DamageTraceStageRecord> stages = new ArrayList<>();
        double input = baseDamage;
        List<DamageStageDefinition> definitions = damageType == null ? List.of() : damageType.stages();
        for (DamageStageDefinition definition : definitions) {
            if (definition == null) {
                continue;
            }
            Double output = result.stageValues().get(definition.id());
            if (output == null) {
                continue;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("flatAttributes", definition.flatAttributes());
            details.put("percentAttributes", definition.percentAttributes());
            details.put("chanceAttributes", definition.chanceAttributes());
            details.put("multiplierAttributes", definition.multiplierAttributes());
            details.put("expression", Texts.toStringSafe(definition.expression()));
            details.put("variables", definition.variables());
            stages.add(new DamageTraceStageRecord(
                    definition.id(),
                    definition.kind() == null ? "" : definition.kind().name(),
                    definition.source() == null ? "" : definition.source().name(),
                    definition.mode() == null ? "" : definition.mode().name(),
                    input,
                    output,
                    details
            ));
            input = output;
        }
        if (stages.isEmpty()) {
            for (Map.Entry<String, Double> entry : result.stageValues().entrySet()) {
                double output = entry.getValue() == null ? input : entry.getValue();
                stages.add(new DamageTraceStageRecord(entry.getKey(), "", "", "", input, output, Map.of()));
                input = output;
            }
        }
        return stages;
    }

    private void addFor(Entity entity, DamageTraceRecord record) {
        if (!(entity instanceof Player player) || record == null) {
            return;
        }
        recordsByPlayer.compute(player.getUniqueId(), (_, current) -> {
            Deque<DamageTraceRecord> next = current == null ? new ArrayDeque<>() : current;
            if (next.peekFirst() != null && next.peekFirst().traceId() == record.traceId()) {
                return next;
            }
            next.addFirst(record);
            while (next.size() > MAX_RECORDS_PER_PLAYER) {
                next.removeLast();
            }
            return next;
        });
    }

    private void trimTotal() {
        int total = recordsByPlayer.values().stream().mapToInt(Deque::size).sum();
        if (total <= MAX_TOTAL_RECORDS) {
            return;
        }
        for (Deque<DamageTraceRecord> records : recordsByPlayer.values()) {
            while (total > MAX_TOTAL_RECORDS && records != null && !records.isEmpty()) {
                records.removeLast();
                total--;
            }
            if (total <= MAX_TOTAL_RECORDS) {
                return;
            }
        }
    }

    private boolean hasPlayerParticipant(DamageContext context) {
        return context != null && (context.attacker() instanceof Player || context.target() instanceof Player);
    }

    private UUID id(Entity entity) {
        return entity == null ? null : entity.getUniqueId();
    }

    private String label(Entity entity) {
        if (entity == null) {
            return "";
        }
        String name = Texts.toStringSafe(entity.getName()).trim();
        if (Texts.isBlank(name)) {
            name = entity.getType().name();
        }
        return name + "(" + entity.getType().name() + ")";
    }
}
