package emaki.jiuwu.craft.corelib.assembly;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.pdc.SnapshotCodec;
import emaki.jiuwu.craft.corelib.text.Texts;

public record EmakiItemLayerSnapshot(String namespaceId,
                                     int schemaVersion,
                                     Map<String, Object> audit,
                                     List<EmakiStatContribution> stats,
                                     EmakiStructuredPresentation structuredPresentation) {

    public static final SnapshotCodec<EmakiItemLayerSnapshot> CODEC = SnapshotCodec.yaml(
        EmakiItemLayerSnapshot::toMap,
        EmakiItemLayerSnapshot::fromMap
    );

    public EmakiItemLayerSnapshot {
        namespaceId = normalizeId(namespaceId);
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        audit = audit == null || audit.isEmpty() ? Map.of() : Map.copyOf(audit);
        stats = stats == null || stats.isEmpty() ? List.of() : List.copyOf(stats);
        structuredPresentation = structuredPresentation == null || structuredPresentation.isEmpty() ? null : structuredPresentation;
    }

    public boolean hasStructuredPresentation() {
        return structuredPresentation != null && !structuredPresentation.isEmpty();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("namespace_id", namespaceId);
        map.put("schema_version", schemaVersion);
        map.put("audit", ConfigNodes.toPlainData(audit));
        List<Map<String, Object>> statMaps = new ArrayList<>();
        for (EmakiStatContribution contribution : stats) {
            if (contribution != null) {
                statMaps.add(contribution.toMap());
            }
        }
        map.put("stats", statMaps);
        if (structuredPresentation != null) {
            map.put("structured_presentation", structuredPresentation.toMap());
        }
        return map;
    }

    public static EmakiItemLayerSnapshot fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        String namespaceId = Texts.toStringSafe(map.get("namespace_id"));
        if (Texts.isBlank(namespaceId)) {
            return null;
        }
        List<EmakiStatContribution> stats = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(map.get("stats"))) {
            Object plain = ConfigNodes.toPlainData(raw);
            if (!(plain instanceof Map<?, ?> statMap)) {
                continue;
            }
            EmakiStatContribution contribution = EmakiStatContribution.fromMap(normalizeMap(statMap));
            if (contribution != null) {
                stats.add(contribution);
            }
        }
        Object auditRaw = ConfigNodes.toPlainData(map.get("audit"));
        Map<String, Object> audit = auditRaw instanceof Map<?, ?> auditMap ? normalizeMap(auditMap) : Map.of();
        Object structuredRaw = ConfigNodes.toPlainData(map.get("structured_presentation"));
        EmakiStructuredPresentation structuredPresentation = structuredRaw instanceof Map<?, ?> structuredMap
                ? EmakiStructuredPresentation.fromMap(normalizeMap(structuredMap))
                : null;
        return new EmakiItemLayerSnapshot(
            namespaceId,
            Numbers.tryParseInt(map.get("schema_version"), 1),
            audit,
            stats,
            structuredPresentation
        );
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(String.valueOf(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
        }
        return result;
    }

    private static String normalizeId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.isBlank() ? "unknown" : normalized;
    }
}
