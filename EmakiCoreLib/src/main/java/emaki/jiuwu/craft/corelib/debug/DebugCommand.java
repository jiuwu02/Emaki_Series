package emaki.jiuwu.craft.corelib.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.service.AbstractMessageService;

public final class DebugCommand {

    private static final Set<String> SHARED_MODULES = Set.of("action", "item_operation");

    private final DebugLogger debugLogger;
    private final Set<String> availableModules;

    public DebugCommand(DebugLogger debugLogger, Set<String> availableModules) {
        this.debugLogger = debugLogger;
        LinkedHashSet<String> modules = new LinkedHashSet<>();
        if (availableModules != null) {
            availableModules.stream()
                    .filter(module -> module != null && !module.isBlank())
                    .map(String::toLowerCase)
                    .forEach(modules::add);
        }
        modules.addAll(SHARED_MODULES);
        this.availableModules = Collections.unmodifiableSet(modules);
    }

    public boolean handle(CommandSender sender, String[] args, AbstractMessageService messageService) {
        if (args.length == 0) {
            sendStatus(sender, messageService);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status" -> sendStatus(sender, messageService);
            case "on" -> {
                debugLogger.enableAll();
                messageService.sendRaw(sender, messageService.message("debug.command.all_enabled"));
            }
            case "off" -> {
                debugLogger.disableAll();
                messageService.sendRaw(sender, messageService.message("debug.command.all_disabled"));
            }
            case "player" -> handlePlayer(sender, args, messageService);
            case "module" -> handleModule(sender, args, messageService);
            default -> sendStatus(sender, messageService);
        }
        return true;
    }

    public List<String> tabComplete(String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("status", "on", "off", "player", "module")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "player" -> {
                    String prefix = args[1].toLowerCase();
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .forEach(result::add);
                }
                case "module" -> {
                    String prefix = args[1].toLowerCase();
                    availableModules.stream()
                            .filter(module -> module.startsWith(prefix))
                            .forEach(result::add);
                }
                default -> {
                }
            }
        }
        return result;
    }

    private void handlePlayer(CommandSender sender, String[] args, AbstractMessageService messageService) {
        if (args.length < 2) {
            messageService.sendRaw(sender, messageService.message("debug.command.available_modules",
                    Map.of("modules", String.join(", ", availableModules))));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messageService.sendRaw(sender, messageService.message("debug.command.player_not_found",
                    Map.of("player", args[1])));
            return;
        }
        boolean nowTracked = debugLogger.togglePlayer(target.getUniqueId());
        if (nowTracked) {
            messageService.sendRaw(sender, messageService.message("debug.command.player_added",
                    Map.of("player", target.getName())));
        } else {
            messageService.sendRaw(sender, messageService.message("debug.command.player_removed",
                    Map.of("player", target.getName())));
        }
    }

    private void handleModule(CommandSender sender, String[] args, AbstractMessageService messageService) {
        if (args.length < 2) {
            messageService.sendRaw(sender, messageService.message("debug.command.available_modules",
                    Map.of("modules", String.join(", ", availableModules))));
            return;
        }
        String module = args[1].toLowerCase();
        boolean nowEnabled = debugLogger.toggleModule(module);
        if (nowEnabled) {
            messageService.sendRaw(sender, messageService.message("debug.command.module_enabled",
                    Map.of("module", module)));
        } else {
            messageService.sendRaw(sender, messageService.message("debug.command.module_disabled",
                    Map.of("module", module)));
        }
    }

    private void sendStatus(CommandSender sender, AbstractMessageService messageService) {
        messageService.sendRaw(sender, messageService.message("debug.command.status_header"));
        if (debugLogger.isGlobalEnabled()) {
            messageService.sendRaw(sender, messageService.message("debug.command.status_enabled"));
        } else {
            messageService.sendRaw(sender, messageService.message("debug.command.status_disabled"));
        }
        Set<UUID> players = debugLogger.trackedPlayers();
        if (players.isEmpty()) {
            messageService.sendRaw(sender, messageService.message("debug.command.tracked_players",
                    Map.of("players", "*")));
        } else {
            List<String> names = new ArrayList<>();
            for (UUID uuid : players) {
                Player player = Bukkit.getPlayer(uuid);
                names.add(player != null ? player.getName() : uuid.toString());
            }
            messageService.sendRaw(sender, messageService.message("debug.command.tracked_players",
                    Map.of("players", String.join(", ", names))));
        }
        Set<String> modules = debugLogger.enabledModules();
        messageService.sendRaw(sender, messageService.message("debug.command.tracked_modules",
                Map.of("modules", modules.isEmpty() ? "*" : String.join(", ", modules))));
        messageService.sendRaw(sender, messageService.message("debug.command.available_modules",
                Map.of("modules", String.join(", ", availableModules))));
    }
}
