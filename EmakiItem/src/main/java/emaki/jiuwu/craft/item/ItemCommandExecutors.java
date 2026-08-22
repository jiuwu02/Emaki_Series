package emaki.jiuwu.craft.item;

import java.util.Map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.scheduling.EmakiScheduling;
import emaki.jiuwu.craft.corelib.api.text.Texts;

final class ItemCommandExecutors {

    private final EmakiItemPlugin plugin;
    private final EmakiScheduling scheduling;

    ItemCommandExecutors(EmakiItemPlugin plugin, EmakiScheduling scheduling) {
        this.plugin = plugin;
        this.scheduling = scheduling;
    }

    boolean runForPlayer(Player player, String operation, Runnable task) {
        if (player == null || task == null) {
            return false;
        }
        boolean owner = scheduling.ownsEntity(player);
        debugCommandDomain(player, operation, owner ? "direct" : "scheduled", owner);
        if (owner) {
            task.run();
            return true;
        }
        scheduling.runForEntity(plugin, player, task, null);
        debugCommandDomain(player, operation, "accepted", false);
        return true;
    }

    void runForSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            if (scheduling.ownsEntity(player)) {
                task.run();
            } else {
                scheduling.runForEntity(plugin, player, task, null);
            }
            return;
        }
        scheduling.runGlobal(plugin, task);
    }

    private void debugCommandDomain(Player player, String operation, String stage, boolean owner) {
        var debugLogger = plugin.debugLogger();
        if (debugLogger == null || !debugLogger.shouldLog("set", player)) {
            return;
        }
        debugLogger.log("set", player, "set.command", Map.of(
                "operation", Texts.toStringSafe(operation),
                "stage", Texts.toStringSafe(stage),
                "global_owner", scheduling.ownsGlobal(),
                "owner", owner,
                "thread", Thread.currentThread().getName()
        ));
    }
}
