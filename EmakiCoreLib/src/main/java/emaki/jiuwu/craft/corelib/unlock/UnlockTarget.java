package emaki.jiuwu.craft.corelib.unlock;

import javax.annotation.Nullable;

import org.bukkit.entity.Player;

public interface UnlockTarget {

    @Nullable
    String validate(Player player, int slots);

    int currentCount();

    @Nullable
    UnlockSlotCost costAt(int ordinal);

    boolean notifyUnlock(Player player, int slots, double currencyTotal);

    void commit(int slots, double currencyTotal, String currencyProviderId);
}
