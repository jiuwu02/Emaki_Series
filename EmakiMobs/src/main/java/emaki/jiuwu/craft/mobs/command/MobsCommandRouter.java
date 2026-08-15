package emaki.jiuwu.craft.mobs.command;

import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public final class MobsCommandRouter {

    private final EmakiMobsPlugin plugin;

    public MobsCommandRouter(EmakiMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public void route(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.messageService().send(sender, "command.usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> handleSpawn(sender, args);
            case "reload" -> handleReload(sender);
            default -> plugin.messageService().send(sender, "command.usage");
        }
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("emakimobs.spawn")) {
            plugin.messageService().send(sender, "command.no_permission");
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.usage");
            return;
        }
        String mobId = args[1];
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messageService().send(sender, "command.spawn_no_target");
            return;
        }
        if (target == null || !target.isOnline()) {
            plugin.messageService().send(sender, "command.spawn_no_target");
            return;
        }
        var result = plugin.mobFactory().spawn(target.getLocation(), mobId);
        if (result.isPresent()) {
            plugin.messageService().send(sender, "command.spawn_success",
                    Map.of("mob_id", mobId));
        } else {
            plugin.messageService().send(sender, "command.spawn_unknown",
                    Map.of("mob_id", mobId));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("emakimobs.reload")) {
            plugin.messageService().send(sender, "command.no_permission");
            return;
        }
        int count = plugin.reloadContent();
        plugin.messageService().send(sender, "command.reload_success",
                Map.of("count", String.valueOf(count)));
    }
}
