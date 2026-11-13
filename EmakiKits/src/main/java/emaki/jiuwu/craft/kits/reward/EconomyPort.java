package emaki.jiuwu.craft.kits.reward;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.kits.config.AppConfig;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public final class EconomyPort {

    private final EmakiCoreLibPlugin coreLib;

    public EconomyPort(EmakiCoreLibPlugin coreLib) {
        this.coreLib = coreLib;
    }

    public EconomyManager economy() {
        return coreLib.economyManager();
    }

    public boolean available() {
        return economy() != null;
    }

    public double balance(Player player, AppConfig.EconomySettings settings) {
        EconomyManager manager = economy();
        return manager == null ? 0D
                : manager.getBalance(player, settings.provider(), settings.currencyId());
    }

    public boolean debit(Player player, AppConfig.EconomySettings settings, double amount) {
        EconomyManager manager = economy();
        if (manager == null) {
            return false;
        }
        try {
            ActionResult result = manager.remove(player, settings.provider(),
                    settings.currencyId(), amount);
            return result != null && result.success();
        } catch (RuntimeException | LinkageError exception) {
            coreLib.getLogger().log(Level.WARNING,
                    "[EmakiKits] 扣款失败: operation=debit, player=" + player.getUniqueId()
                            + ", amount=" + amount + ", cause=" + exception, exception);
            return false;
        }
    }

    public void refund(Player player, AppConfig.EconomySettings settings, double amount) {
        EconomyManager manager = economy();
        if (manager == null) {
            return;
        }
        try {
            manager.add(player, settings.provider(), settings.currencyId(), amount);
        } catch (RuntimeException | LinkageError exception) {
            coreLib.getLogger().log(Level.SEVERE,
                    "[EmakiKits] 扣款补偿失败: operation=refund, player=" + player.getUniqueId()
                            + ", amount=" + amount + ", cause=" + exception, exception);
        }
    }
}
