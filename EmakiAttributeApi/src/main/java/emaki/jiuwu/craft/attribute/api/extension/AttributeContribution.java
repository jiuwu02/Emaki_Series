package emaki.jiuwu.craft.attribute.api.extension;

/**
 * A single attribute value contributed to an entity by some source.
 *
 * <p>Returned in batches by an {@link AttributeContributionProvider} so that
 * EmakiAttribute can aggregate attribute values originating from external
 * plugins (equipment stats, buffs, etc.) into an entity's combat snapshot.
 *
 * @param attributeId the EmakiAttribute attribute id this value applies to
 * @param value       the contributed numeric value (may be negative)
 * @param sourceId    an identifier describing where the value came from; never
 *                    {@code null} (normalized to an empty string when absent)
 */
public record AttributeContribution(String attributeId, double value, String sourceId) {

    /**
     * Canonical constructor; normalizes a {@code null} {@code sourceId} to an
     * empty string so callers never receive {@code null}.
     */
    public AttributeContribution   {
        sourceId = sourceId == null ? "" : sourceId;
    }
}
