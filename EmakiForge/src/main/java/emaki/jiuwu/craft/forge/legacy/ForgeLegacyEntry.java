package emaki.jiuwu.craft.forge.legacy;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyConvertCommand;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class ForgeLegacyEntry {

    private ForgeLegacyEntry() {
    }

    public static boolean handle(@NotNull EmakiForgePlugin plugin,
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
                ForgeLegacyTargets.specs(),
                plugin.getLogger(),
                LegacyConvertCommand.applyRequested(args, 1));
        return true;
    }
}
