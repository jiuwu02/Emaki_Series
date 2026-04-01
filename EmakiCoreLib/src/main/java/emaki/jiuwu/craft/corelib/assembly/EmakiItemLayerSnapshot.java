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
                                     List<EmakiPresentationEntry> presentation) {

    public static final SnapshotCodec<EmakiItemLayerSnapshot> CODEC = SnapshotCodec.yaml(
        EmakiItemLayerSnapshot::toMap,
        EmakiItemLayerSnapshot::fromMap
    );

    public EmakiItemLayerSnapshot {
        namespaceId = normalizeId(namespaceId);
        schemaVersion = schemaVersion <= 0 ? 1 : schemaVersion;
        audit = audit == null || audit.isEmpty() ? Map.of() : Map.copyOf(audit);
        stats = stats == null || stats.isEmpty() ? List.of() : List.copyOf(stats);
        presentation = presentation == null || presentation.isEmpty() ? List.of() : List.copyOf(presentation);
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
        List<Map<String, Object>> presentationMaps = new ArrayList<>();
        for (EmakiPresentationEntry entry : presentation) {
            if (entry != null) {
                presentationMaps.add(entry.toMap());
            }
        }
        map.put("presentation", presentationMaps);
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
        List<EmakiPresentationEntry> presentation = new ArrayList<>();
        for (Object raw : ConfigNodes.asObjectList(map.get("presentation"))) {
            Object plain = ConfigNodes.toPlainData(raw);
            if (!(plain instanceof Map<?, ?> presentationMap)) {
                continue;
            }
            EmakiPresentationEntry entry = EmakiPresentationEntry.fromMap(normalizeMap(presentationMap));
            if (entry != null) {
                presentation.add(entry);
            }
        }
        Object auditRaw = ConfigNodes.toPlainData(map.get("audit"));
        Map<String, Object> audit = auditRaw instanceof Map<?, ?> auditMap ? normalizeMap(auditMap) : Map.of();
        return new EmakiItemLayerSnapshot(
            namespaceId,
            Numbers.tryParseInt(map.get("schema_version"), 1),
            audit,
            stats,
            presentation
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
