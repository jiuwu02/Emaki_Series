package emaki.jiuwu.craft.strengthen.api.model;


/**
 * Describes one material requirement of a strengthen attempt and how it is
 * satisfied by the player's supplied inputs.
 *
 * @param item            the material item id
 * @param requiredAmount  the amount required (defaults to 1 when 0; a negative
 *                        value means "no hard requirement")
 * @param availableAmount the amount the player actually supplied; clamped to
 *                        {@code >= 0}
 * @param optional        whether the material is optional
 * @param protection      whether the material acts as failure protection
 * @param temperBoost     temper bonus granted by this material; clamped to
 *                        {@code >= 0}
 * @param consumedAmount  the amount that will be consumed; clamped to {@code >= 0}
 * @param materialId      the canonical material selection identity
 * @param countKey        the quantity aggregation and consumption identity
 * @param inputIndex      the temporary coordinate used to debit or return the original input,
 *                        or {@code -1}; it is not a material identity
 * @param sourceToken     the matched source token used only for display and audit; it is not
 *                        part of material identity, aggregation, or signatures
 */
public record AttemptMaterial(String item,
        int requiredAmount,
        int availableAmount,
        boolean optional,
        boolean protection,
        int temperBoost,
        int consumedAmount,
        String materialId,
        String countKey,
        int inputIndex,
        String sourceToken) {

    /** Canonical constructor; normalizes identities and clamps counts. */
    public AttemptMaterial {
        item = StrengthenApiValues.toStringSafe(item);
        requiredAmount = requiredAmount == 0 ? 1 : requiredAmount;
        availableAmount = Math.max(0, availableAmount);
        temperBoost = Math.max(0, temperBoost);
        consumedAmount = Math.max(0, consumedAmount);
        materialId = StrengthenApiValues.isBlank(materialId) ? item : StrengthenApiValues.toStringSafe(materialId);
        countKey = StrengthenApiValues.isBlank(countKey) ? materialId : StrengthenApiValues.toStringSafe(countKey);
        inputIndex = Math.max(-1, inputIndex);
        sourceToken = StrengthenApiValues.toStringSafe(sourceToken);
    }

    /** Creates the legacy item-keyed view. */
    public AttemptMaterial(String item,
            int requiredAmount,
            int availableAmount,
            boolean optional,
            boolean protection,
            int temperBoost,
            int consumedAmount) {
        this(item, requiredAmount, availableAmount, optional, protection, temperBoost, consumedAmount,
                item, item, -1, item);
    }

    /** {@return whether this material requirement is met} */
    public boolean satisfied() {
        return optional || requiredAmount < 0 || availableAmount >= requiredAmount;
    }
}
