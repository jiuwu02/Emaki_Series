package emaki.jiuwu.craft.dungeon.command;

import java.util.Collection;
import java.util.List;

import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.dungeon.EmakiDungeonPlugin;

public final class DungeonCommandRouter {

    private static final List<String> ROOT_SUBS = List.of(
            "help", "list", "enter", "leave", "cancel", "ready",
            "party", "stats", "rank", "globalchat", "editor", "admin"
    );

    private final EmakiDungeonPlugin plugin;

    public DungeonCommandRouter(EmakiDungeonPlugin plugin) {
        this.plugin = plugin;
    }

    public void route(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            plugin.messageService().send(sender, "command.help");
            return;
        }
        plugin.messageService().send(sender, "command.unknown", java.util.Map.of("command", args[0]));
    }

    public @NotNull Collection<String> suggest(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length <= 1) {
            return ROOT_SUBS;
        }
        return List.of();
    }
}
