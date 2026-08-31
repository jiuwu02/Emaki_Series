package emaki.jiuwu.craft.cooking.legacy;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.corelib.legacy.LegacyConvertCommand;

public final class CookingLegacyEntry {

    private CookingLegacyEntry() {
    }

    public static boolean handle(@NotNull EmakiCookingPlugin plugin,
            @NotNull CommandSender sender,
            String[] args,
            @NotNull String adminPermission) {
        if (!sender.hasPermission(adminPermission)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        LegacyConvertCommand.run(plugin.messageService(),
                sender,
                plugin.getDataFolder().toPath(),
                CookingLegacyTargets.specs(),
                plugin.getLogger(),
                LegacyConvertCommand.applyRequested(args, 1));
        return true;
    }
}
