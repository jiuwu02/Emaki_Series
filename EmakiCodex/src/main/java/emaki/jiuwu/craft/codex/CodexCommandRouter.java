package emaki.jiuwu.craft.codex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.codex.recipe.model.CodexRecipe;
import emaki.jiuwu.craft.corelib.command.CommandTabHelper;

/**
 * Root command handler for {@code /codex}. Admin actions gate on {@code emakicodex.admin};
 * reload also accepts {@code emakicodex.reload}; debug accepts {@code emakicodex.debug}.
 */
final class CodexCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakicodex";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";

    private static final List<String> SUBCOMMANDS =
            List.of("help", "reload", "sync", "recipe", "unlock", "lock", "grant", "revoke", "debug");

    private final EmakiCodexPlugin plugin;

    CodexCommandRouter(EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "reload" -> handleReload(sender);
            case "sync" -> handleSync(sender, args);
            case "recipe" -> handleRecipe(sender, args);
            case "unlock" -> handleUnlock(sender, args, true);
            case "lock" -> handleUnlock(sender, args, false);
            case "grant" -> handleAdvancement(sender, args, true);
            case "revoke" -> handleAdvancement(sender, args, false);
            case "debug" -> handleDebug(sender, args);
            default -> {
                plugin.messageService().send(sender, "general.unknown_command");
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender) {
        if (!hasAdminOr(sender, PERMISSION_RELOAD)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        plugin.reloadPluginState();
        plugin.messageService().send(sender, "general.reload_success");
        plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                "recipes", plugin.recipeIndex().size(),
                "advancements", plugin.advancementRegistrar().size()
        )));
        return true;
    }

    private boolean handleSync(CommandSender sender, String[] args) {
        if (!hasAdminOr(sender, PERMISSION_RELOAD)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messageService().send(sender, "general.player_not_found", Map.of("player", args[1]));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        plugin.recipeSyncGateway().sync(target);
        plugin.messageService().send(sender, "command.sync.done", Map.of("player", target.getName()));
        return true;
    }

    private boolean handleRecipe(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2 || !"list".equalsIgnoreCase(args[1])) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        String sourceFilter = args.length >= 3 ? args[2].toLowerCase(Locale.ROOT) : null;
        List<CodexRecipe> matches = new ArrayList<>();
        for (CodexRecipe recipe : plugin.recipeIndex().all()) {
            if (sourceFilter == null || recipe.namespace().equals(sourceFilter)) {
                matches.add(recipe);
            }
        }
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.recipe.header",
                Map.of("count", matches.size())));
        int shown = 0;
        for (CodexRecipe recipe : matches) {
            if (shown++ >= 50) {
                plugin.messageService().sendRaw(sender, plugin.messageService().message("command.recipe.truncated",
                        Map.of("remaining", matches.size() - 50)));
                break;
            }
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.recipe.line",
                    Map.of("id", recipe.recipeId(), "type", recipe.type().token())));
        }
        return true;
    }

    private boolean handleUnlock(CommandSender sender, String[] args, boolean unlock) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        OfflinePlayer target = resolveTarget(args[1]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found", Map.of("player", args[1]));
            return true;
        }
        String recipeId = args[2];
        boolean changed = unlock
                ? plugin.unlockStore().unlock(target.getUniqueId(), recipeId)
                : plugin.unlockStore().lock(target.getUniqueId(), recipeId);
        if (target.isOnline() && target.getPlayer() != null) {
            plugin.recipeSyncGateway().sync(target.getPlayer());
        }
        String key = unlock ? "command.unlock.done" : "command.lock.done";
        plugin.messageService().send(sender, key, Map.of(
                "player", String.valueOf(target.getName()),
                "recipe", recipeId,
                "changed", String.valueOf(changed)));
        return true;
    }

    private boolean handleAdvancement(CommandSender sender, String[] args, boolean grant) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found", Map.of("player", args[1]));
            return true;
        }
        String advancementId = args[2];
        boolean ok = grant
                ? plugin.advancementService().grant(target, advancementId)
                : plugin.advancementService().revoke(target, advancementId);
        String key = grant ? "command.grant.done" : "command.revoke.done";
        if (!ok) {
            plugin.messageService().send(sender, "command.advancement.failed", Map.of("advancement", advancementId));
            return true;
        }
        plugin.messageService().send(sender, key, Map.of(
                "player", target.getName(),
                "advancement", advancementId));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!hasAdminOr(sender, PERMISSION_DEBUG)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    @SuppressWarnings("deprecation") // getOfflinePlayer(String) is the only lookup for offline targets by name
    private OfflinePlayer resolveTarget(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() ? offline : null;
    }

    private boolean hasAdminOr(CommandSender sender, String permission) {
        return sender.hasPermission(PERMISSION_ADMIN) || sender.hasPermission(permission);
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("reload", plugin.messageService().message("command.help.commands.reload"));
        lines.put("sync [player]", plugin.messageService().message("command.help.commands.sync"));
        lines.put("recipe list [source]", plugin.messageService().message("command.help.commands.recipe"));
        lines.put("unlock <player> <recipeId>", plugin.messageService().message("command.help.commands.unlock"));
        lines.put("lock <player> <recipeId>", plugin.messageService().message("command.help.commands.lock"));
        lines.put("grant <player> <advId>", plugin.messageService().message("command.help.commands.grant"));
        lines.put("revoke <player> <advId>", plugin.messageService().message("command.help.commands.revoke"));
        lines.put("debug", plugin.messageService().message("command.help.commands.debug"));
        lines.forEach((name, desc) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", desc))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandTabHelper.completeSubcommands(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "sync", "unlock", "lock", "grant", "revoke" -> CommandTabHelper.completeOnlinePlayers(args[1]);
                case "recipe" -> CommandTabHelper.completeLiterals(args[1], "list");
                case "debug" -> plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
                default -> List.of();
            };
        }
        if (args.length >= 2 && "debug".equals(sub)) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 3 && ("unlock".equals(sub) || "lock".equals(sub))) {
            List<String> ids = new ArrayList<>(plugin.recipeIndex().asMap().keySet());
            return CommandTabHelper.filterByPrefix(ids, args[2]);
        }
        if (args.length == 3 && ("grant".equals(sub) || "revoke".equals(sub))) {
            return CommandTabHelper.filterByPrefix(plugin.advancementRegistrar().registered().keySet(), args[2]);
        }
        return List.of();
    }
}
