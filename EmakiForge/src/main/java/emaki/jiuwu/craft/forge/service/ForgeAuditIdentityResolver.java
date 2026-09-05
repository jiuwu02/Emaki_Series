package emaki.jiuwu.craft.forge.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import emaki.jiuwu.craft.corelib.api.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class ForgeAuditIdentityResolver {
    private ForgeAuditIdentityResolver() {
    }

    static Map<String, Object> canonicalEntry(String materialId, String countKey, String auditId,
            String legacyMaterial, String category, int amount, int amountConsumed, int slot, int sequence,
            String matchedSource, String matcherDigest) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material_id", Texts.toStringSafe(materialId));
        map.put("count_key", Texts.toStringSafe(countKey));
        map.put("audit_id", Texts.toStringSafe(auditId));
        map.put("material_item", Texts.toStringSafe(legacyMaterial));
        map.put("category", Texts.toStringSafe(category));
        map.put("amount", Math.max(0, amount));
        map.put("amount_consumed", Math.max(0, amountConsumed));
        map.put("slot", slot);
        map.put("sequence", Math.max(0, sequence));
        map.put("matched_source", Texts.toStringSafe(matchedSource));
        map.put("matcher", Texts.toStringSafe(matcherDigest));
        map.put("allocation", Map.of("slot", slot, "amount", Math.max(0, amount)));
        return map;
    }

    static <T> Resolution<T> resolve(Object raw,
            Function<String, T> byAuditId,
            Function<String, T> byMaterialId,
            Function<String, T> byCountKey,
            Function<String, T> byLegacy) {
        Map<String, Object> entry = ConfigNodes.entries(raw);
        Fields fields = fields(entry);
        T value = apply(fields.auditId(), byAuditId);
        String lookup = fields.auditId();
        String mode = "audit_id";
        if (value == null) {
            value = apply(fields.materialId(), byMaterialId);
            lookup = fields.materialId();
            mode = "material_id";
        }
        if (value == null) {
            value = apply(fields.countKey(), byCountKey);
            lookup = fields.countKey();
            mode = "count_key";
        }
        if (value == null) {
            value = apply(fields.legacyMaterial(), byLegacy);
            lookup = fields.legacyMaterial();
            mode = "legacy";
        }
        return new Resolution<>(value, fields, lookup, mode,
                value != null && (!fields.canonical() || !"audit_id".equals(mode)));
    }

    private static Fields fields(Map<String, Object> entry) {
        String auditId = ConfigNodes.string(entry, "audit_id", "");
        String materialId = ConfigNodes.string(entry, "material_id", "");
        String countKey = ConfigNodes.string(entry, "count_key", "");
        String legacyMaterial = ConfigNodes.string(entry, "material_item",
                ConfigNodes.string(entry, "source", ""));
        int contributionAmount = Numbers.tryParseInt(entry.get("amount"), 0);
        int amountConsumed = Numbers.tryParseInt(entry.get("amount_consumed"), contributionAmount);
        return new Fields(auditId, materialId, countKey, legacyMaterial, contributionAmount, amountConsumed,
                ConfigNodes.string(entry, "matched_source", ConfigNodes.string(entry, "source", "")));
    }

    private static <T> T apply(String identity, Function<String, T> resolver) {
        return Texts.isBlank(identity) || resolver == null ? null : resolver.apply(identity);
    }

    record Fields(String auditId, String materialId, String countKey, String legacyMaterial,
            int contributionAmount, int amountConsumed, String matchedSource) {
        boolean canonical() {
            return Texts.isNotBlank(auditId) && Texts.isNotBlank(materialId) && Texts.isNotBlank(countKey);
        }
    }

    record Resolution<T>(T value, Fields fields, String lookupIdentity, String mode,
            boolean requiresMigration) {
    }
}
