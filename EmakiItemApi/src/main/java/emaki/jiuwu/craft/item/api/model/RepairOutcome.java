package emaki.jiuwu.craft.item.api.model;

import org.jetbrains.annotations.NotNull;

/** Result committed by the runtime repair service. */
public record RepairOutcome(@NotNull String itemId,
                            int restoredAmount,
                            int currentDamage,
                            int maxDamage,
                            boolean disabled) {

    public RepairOutcome {
        itemId = itemId == null ? "" : itemId;
        restoredAmount = Math.max(0, restoredAmount);
        currentDamage = Math.max(0, currentDamage);
        maxDamage = Math.max(0, maxDamage);
    }
}
