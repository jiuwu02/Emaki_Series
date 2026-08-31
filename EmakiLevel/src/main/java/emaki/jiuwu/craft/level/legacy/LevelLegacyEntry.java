package emaki.jiuwu.craft.level.legacy;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.legacy.LegacyConvertCommand;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;

public final class LevelLegacyEntry {

    private LevelLegacyEntry() {
    }

    public static boolean handle(@NotNull EmakiLevelPlugin plugin,
            @NotNull CommandSender sender,
            String[] args,
            @NotNull String adminPermission) {
        if (!sender.hasPermission(adminPermission)) {
            plugin.messages().send(sender, "command.no_permission");
            return true;
        }
        LegacyConvertCommand.run(plugin.messages(),
                sender,
                plugin.getDataFolder().toPath(),
                LevelLegacyTargets.specs(),
                plugin.getLogger(),
                LegacyConvertCommand.applyRequested(args, 0));
        return true;
    }
}
