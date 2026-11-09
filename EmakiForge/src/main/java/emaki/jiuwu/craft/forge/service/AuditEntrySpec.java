package emaki.jiuwu.craft.forge.service;

public record AuditEntrySpec(
        String materialId,
        String countKey,
        String auditId,
        String materialItem,
        String category,
        int amount,
        int amountConsumed,
        int slot,
        int sequence,
        String matchedSource,
        String matcherDigest) {
}
