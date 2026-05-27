package emaki.jiuwu.craft.strengthen.model;

import emaki.jiuwu.craft.corelib.text.Texts;

public record AttemptMaterial(String item,
        int requiredAmount,
        int availableAmount,
        boolean optional,
        boolean protection,
        int temperBoost,
        int consumedAmount) {

    public AttemptMaterial {
        item = Texts.toStringSafe(item);
        requiredAmount = requiredAmount == 0 ? 1 : requiredAmount;
        availableAmount = Math.max(0, availableAmount);
        temperBoost = Math.max(0, temperBoost);
        consumedAmount = Math.max(0, consumedAmount);
    }

    public boolean satisfied() {
        return optional || requiredAmount < 0 || availableAmount >= requiredAmount;
    }
}
