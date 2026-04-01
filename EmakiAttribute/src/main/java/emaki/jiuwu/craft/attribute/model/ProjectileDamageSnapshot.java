package emaki.jiuwu.craft.attribute.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.math.Numbers;

public record ProjectileDamageSnapshot(int schemaVersion,
        String damageTypeId,
        UUID shooterUuid,
        String sourceSignature,
        long launchedAt,
        long expiresAt,
        AttributeSnapshot attackSnapshot) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ProjectileDamageSnapshot       {
        sourceSignature = sourceSignature == null ? "" : sourceSignature;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schema_version", schemaVersion);
        map.put("damage_type_id", damageTypeId);
        map.put("shooter_uuid", shooterUuid == null ? null : shooterUuid.toString());
        map.put("source_signature", sourceSignature);
        map.put("launched_at", launchedAt);
        map.put("expires_at", expiresAt);
        map.put("attack_snapshot", attackSnapshot == null ? null : attackSnapshot.toMap());
        return map;
    }

    public static ProjectileDamageSnapshot fromMap(Map<String, Object> map) {
        if (map == null) {
            return null;
        }
        Object attackRaw = map.get("attack_snapshot");
        AttributeSnapshot attackSnapshot = null;
        if (attackRaw instanceof Map<?, ?> attackMap) {
            Map<String, Object> plain = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : attackMap.entrySet()) {
                if (entry.getKey() != null) {
                    plain.put(String.valueOf(entry.getKey()), ConfigNodes.toPlainData(entry.getValue()));
                }
            }
            attackSnapshot = AttributeSnapshot.fromMap(plain);
        }
        return new ProjectileDamageSnapshot(
                Numbers.tryParseInt(map.get("schema_version"), CURRENT_SCHEMA_VERSION),
                ConfigNodes.string(map, "damage_type_id", ""),
                map.get("shooter_uuid") == null ? null : UUID.fromString(String.valueOf(map.get("shooter_uuid"))),
                ConfigNodes.string(map, "source_signature", ""),
                Numbers.tryParseLong(map.get("launched_at"), 0L),
                Numbers.tryParseLong(map.get("expires_at"), 0L),
                attackSnapshot
        );
    }
}
