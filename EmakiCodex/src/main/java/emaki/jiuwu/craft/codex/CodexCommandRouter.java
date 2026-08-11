package emaki.jiuwu.craft.codex;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;





final class CodexCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakicodex";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";

    private static final List<String> SUBCOMMANDS =
            List.of("help", "reload", "grant", "revoke", "debug");

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
                "advancements", plugin.advancementRegistrar().size()
        )));
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
        // Carries the whole EmakiResult back rather than isSuccess(): the service distinguishes nine
        // failure causes (not registered, missing on server, already completed, disabled, wrong thread,
        // ...) and collapsing them into one message left the operator with no way to tell them apart.
        runOnTargetOwner(target, () -> grant
                ? plugin.advancementService().grant(target, advancementId)
                : plugin.advancementService().revoke(target, advancementId))
                .whenComplete((result, throwable) -> {
                    if (throwable != null || result == null) {
                        sendToSender(sender, () -> plugin.messageService().send(sender,
                                "command.advancement.failed", Map.of(
                                        "advancement", advancementId,
                                        "reason", throwable == null
                                                ? plugin.messageService().message("command.advancement.reason_unknown")
                                                : Texts.toStringSafe(throwable.getMessage()))));
                        return;
                    }
                    if (!result.isSuccess()) {
                        sendToSender(sender, () -> plugin.messageService().send(sender,
                                "command.advancement.failed", Map.of(
                                        "advancement", advancementId,
                                        "reason", describeFailure(result))));
                        return;
                    }
                    String key = grant ? "command.grant.done" : "command.revoke.done";
                    sendToSender(sender, () -> plugin.messageService().send(sender, key, Map.of(
                            "player", target.getName(),
                            "advancement", advancementId)));
                });
        return true;
    }

    /**
     * Turns a failed result into the sentence explaining why.
     *
     * <p>Resolves the result's own reason key through the language file, so a new failure cause in
     * {@code AdvancementService} only needs a language entry rather than a change here. Falls back to the
     * raw key so an untranslated cause is still reportable instead of silently blank.</p>
     */
    private String describeFailure(EmakiResult<Unit> result) {
        String reasonKey = result == null ? "" : Texts.toStringSafe(result.reasonKey());
        if (Texts.isBlank(reasonKey)) {
            return plugin.messageService().message("command.advancement.reason_unknown");
        }
        return plugin.messageService().messageOrFallback(reasonKey, reasonKey);
    }

    /**
     * Runs an advancement mutation on the thread that owns the target.
     *
     * <p>Carries the operation's own {@link EmakiResult} rather than a boolean so the caller can report why
     * a mutation was refused. When the target is gone or no thread could be obtained the failure is
     * expressed as a result too, keeping one reporting path for every outcome.</p>
     */
    private CompletableFuture<EmakiResult<Unit>> runOnTargetOwner(Player target,
            Supplier<EmakiResult<Unit>> operation) {
        CompletableFuture<EmakiResult<Unit>> future = new CompletableFuture<>();
        if (target == null || !target.isOnline()) {
            future.complete(EmakiResult.targetOffline());
            return future;
        }
        Runnable task = () -> {
            try {
                EmakiResult<Unit> result = operation.get();
                future.complete(result == null ? EmakiResult.unavailable() : result);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        };
        try {
            if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(target)) {
                task.run();
                return future;
            }
            var scheduled = plugin.executionDispatcher().runEntity(plugin, target, task,
                    () -> future.complete(EmakiResult.unavailable()));
            if (scheduled == null) {
                future.complete(EmakiResult.unavailable());
            }
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private void sendToSender(CommandSender sender, Runnable task) {
        try {
            if (sender instanceof Player player) {
                if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(player)) {
                    task.run();
                } else {
                    plugin.executionDispatcher().runEntity(plugin, player, task, () -> { });
                }
            } else {
                plugin.executionDispatcher().runGlobal(plugin, task);
            }
        } catch (Throwable throwable) {
            plugin.getLogger().log(Level.WARNING,
                    "Codex command response dispatch failed: sender=" + sender.getName()
                            + ", operation=send_to_sender, cause=" + throwable,
                    throwable);
        }
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!hasAdminOr(sender, PERMISSION_DEBUG)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private boolean hasAdminOr(CommandSender sender, String permission) {
        return sender.hasPermission(PERMISSION_ADMIN) || sender.hasPermission(permission);
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("reload", plugin.messageService().message("command.help.commands.reload"));
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
                case "grant", "revoke" -> CommandTabHelper.completeOnlinePlayers(args[1]);
                case "debug" -> plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
                default -> List.of();
            };
        }
        if (args.length >= 2 && "debug".equals(sub)) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 3 && ("grant".equals(sub) || "revoke".equals(sub))) {
            return CommandTabHelper.filterByPrefix(plugin.advancementRegistrar().registered().keySet(), args[2]);
        }
        return List.of();
    }
}
