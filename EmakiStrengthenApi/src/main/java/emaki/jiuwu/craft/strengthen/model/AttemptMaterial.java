package emaki.jiuwu.craft.strengthen.model;

import emaki.jiuwu.craft.corelib.text.Texts;

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
 */
public record AttemptMaterial(String item,
        int requiredAmount,
        int availableAmount,
        boolean optional,
        boolean protection,
        int temperBoost,
        int consumedAmount) {

    /** Canonical constructor; normalizes the item id and clamps counts. */
    public AttemptMaterial {
        item = Texts.toStringSafe(item);
        requiredAmount = requiredAmount == 0 ? 1 : requiredAmount;
        availableAmount = Math.max(0, availableAmount);
        temperBoost = Math.max(0, temperBoost);
        consumedAmount = Math.max(0, consumedAmount);
    }

    /** {@return whether this material requirement is met} */
    public boolean satisfied() {
        return optional || requiredAmount < 0 || availableAmount >= requiredAmount;
    }
}
