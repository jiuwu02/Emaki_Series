package emaki.jiuwu.craft.storage.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.command.CommandTabHelper;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.session.StorageSessionManager;

/**
 * Root command dispatch for {@code /emakistorage}.
 *
 * <p>Implements Bukkit's {@link TabExecutor} and is adapted to Paper's {@code BasicCommand} by the
 * plugin class, matching how the other Emaki modules register their root command.
 */
public final class StorageCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakistorage";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";

    private static final List<String> SUBCOMMANDS =
            List.of("help", "open", "slot", "stacklimit", "info", "export", "reload", "debug");

    private final EmakiStoragePlugin plugin;

    public StorageCommandRouter(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return handleOpenSelf(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "open" -> handleOpen(sender, args);
            case "slot" -> handleSlot(sender, args);
            case "stacklimit" -> handleStackLimit(sender, args);
            case "info" -> handleInfo(sender, args);
            case "export" -> handleExport(sender, args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender, args);
            default -> {
                plugin.messageService().send(sender, "general.unknown_command");
                yield true;
            }
        };
    }

    private boolean handleOpenSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!player.hasPermission(PERMISSION_USE) && !hasAdmin(player)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (!plugin.sessionManager().openOwn(player)) {
            plugin.messageService().send(sender, "general.open_failed");
        }
        return true;
    }

    private boolean handleOpen(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (!(sender instanceof Player admin)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        ResolvedTarget target = resolveTarget(sender, args[1]);
        if (target == null) {
            return true;
        }
        plugin.sessionManager().openForAdmin(admin, target.uuid(), target.name())
                .thenAccept(opened -> {
                    if (!Boolean.TRUE.equals(opened)) {
                        plugin.messageService().send(sender, "general.open_failed");
                    }
                });
        return true;
    }

    /** Handles {@code slot grant <player> <amount>}. */
    private boolean handleSlot(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 4 || !args[1].equalsIgnoreCase("grant")) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        ResolvedTarget target = resolveTarget(sender, args[2]);
        if (target == null) {
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[3].trim());
        } catch (NumberFormatException ignored) {
            plugin.messageService().send(sender, "command.slot.invalid_amount", Map.of("input", args[3]));
            return true;
        }
        withStorage(sender, target, storage -> {
            storage.grantedSlots(storage.grantedSlots() + amount);
            storage.markDirty();
            plugin.operationLog().record(StorageLogEntry.raw(storage.playerId(),
                    StorageOperationType.ADMIN_GIVE, null,
                    (amount >= 0 ? "+" : "") + amount + "slots", storage.grantedSlots(),
                    StorageOperationSource.COMMAND, "by=" + sender.getName()));
            plugin.messageService().send(sender, "command.slot.granted", Map.of(
                    "player", target.name(),
                    "amount", amount,
                    "granted", storage.grantedSlots()));
        });
        return true;
    }

    /** Handles {@code stacklimit player|slot ...}. */
    private boolean handleStackLimit(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 4) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        String mode = args[1].toLowerCase(Locale.ROOT);
        if (mode.equals("player")) {
            ResolvedTarget target = resolveTarget(sender, args[2]);
            if (target == null) {
                return true;
            }
            Long limit = parseLimit(sender, args[3]);
            if (limit == null) {
                return true;
            }
            withStorage(sender, target, storage -> {
                storage.defaultStackLimit(limit);
                storage.markDirty();
                plugin.operationLog().record(StorageLogEntry.raw(storage.playerId(),
                        StorageOperationType.ADMIN_SET, null, "=" + limit, limit,
                        StorageOperationSource.COMMAND,
                        "by=" + sender.getName() + " field=default_stack_limit"));
                plugin.messageService().send(sender, "command.stacklimit.player_set", Map.of(
                        "player", target.name(), "limit", limit));
            });
            return true;
        }
        if (mode.equals("slot")) {
            if (args.length < 5) {
                plugin.messageService().send(sender, "general.invalid_args");
                return true;
            }
            ResolvedTarget target = resolveTarget(sender, args[2]);
            if (target == null) {
                return true;
            }
            int slotIndex;
            try {
                slotIndex = Integer.parseInt(args[3].trim());
            } catch (NumberFormatException ignored) {
                plugin.messageService().send(sender, "general.invalid_args");
                return true;
            }
            Long limit = parseLimit(sender, args[4]);
            if (limit == null) {
                return true;
            }
            withStorage(sender, target, storage -> {
                StorageEntry entry = storage.entryAt(slotIndex);
                if (entry == null) {
                    plugin.messageService().send(sender, "command.stacklimit.slot_empty",
                            Map.of("slot", slotIndex));
                    return;
                }
                entry.stackLimit(limit);
                storage.markDirty();
                plugin.operationLog().record(StorageLogEntry.raw(storage.playerId(),
                        StorageOperationType.ADMIN_SET,
                        plugin.textIndexer().identifierOf(entry.key()), "=" + limit, limit,
                        StorageOperationSource.COMMAND,
                        "by=" + sender.getName() + " field=slot_stack_limit slot=" + slotIndex));
                plugin.messageService().send(sender, "command.stacklimit.slot_set", Map.of(
                        "player", target.name(), "slot", slotIndex, "limit", limit));
            });
            return true;
        }
        plugin.messageService().send(sender, "general.invalid_args");
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        ResolvedTarget target = resolveTarget(sender, args[1]);
        if (target == null) {
            return true;
        }
        plugin.getApiBridgeSnapshot(target.uuid()).thenAccept(snapshot -> {
            StorageCapacity capacity = snapshot.capacity();
            Map<String, Object> replacements = new LinkedHashMap<>();
            replacements.put("player", target.name());
            replacements.put("used_slots", capacity.usedSlots());
            replacements.put("total_slots", capacity.effectiveSlots());
            replacements.put("base_slots", capacity.baseSlots());
            replacements.put("permission_slots", capacity.permissionSlots());
            replacements.put("granted_slots", capacity.grantedSlots());
            replacements.put("purchased_slots", capacity.purchasedSlots());
            replacements.put("max_slots", capacity.maxSlots());
            replacements.put("pages", capacity.totalPages());
            replacements.put("stack_limit", snapshot.defaultStackLimit());
            replacements.put("sort_mode", snapshot.sortMode());
            replacements.put("total_amount", snapshot.totalAmount());
            var messages = plugin.messageService();
            messages.sendRaw(sender, messages.message("command.info.header", replacements));
            messages.sendRaw(sender, messages.message("command.info.capacity", replacements));
            messages.sendRaw(sender, messages.message("command.info.sources", replacements));
            messages.sendRaw(sender, messages.message("command.info.limits", replacements));
        });
        return true;
    }

    /**
     * Handles {@code export <player>}.
     *
     * <p>Entry payloads are binary and cannot be hand-edited, so an admin diagnosing a problem needs
     * a readable dump. The values that genuinely need hand-editing live in {@code meta.yml} already.
     */
    private boolean handleExport(CommandSender sender, String[] args) {
        if (!hasAdmin(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        ResolvedTarget target = resolveTarget(sender, args[1]);
        if (target == null) {
            return true;
        }
        plugin.exportStorage(target.uuid(), target.name()).thenAccept(path -> {
            if (path == null) {
                plugin.messageService().send(sender, "command.export.failed",
                        Map.of("player", target.name()));
                return;
            }
            plugin.messageService().send(sender, "command.export.done", Map.of(
                    "player", target.name(), "file", path));
        });
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !hasAdmin(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        int templates = plugin.reloadPluginState();
        plugin.messageService().send(sender, "general.reload_success");
        plugin.messageService().sendRaw(sender,
                plugin.messageService().message("general.reload_summary",
                        Map.of("templates", templates)));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !hasAdmin(sender)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.debugCommand().handle(sender,
                Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    /**
     * Parses a stack limit, accepting compact units and {@code max}.
     *
     * <p>Out-of-range input is rejected outright rather than truncated, so a mistyped figure never
     * silently becomes a different ceiling.
     *
     * @return the parsed limit, or {@code null} when already reported as invalid
     */
    private Long parseLimit(CommandSender sender, String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.equalsIgnoreCase("max")) {
            return Long.MAX_VALUE;
        }
        // 0 is a meaningful value here: at entry and player level it restores inheritance from the
        // next level up, so it must not be rejected alongside genuinely invalid input.
        if (text.equals("0")) {
            return 0L;
        }
        long parsed = StorageSessionManager.parseCompactAmount(text);
        if (parsed <= 0L) {
            plugin.messageService().send(sender, "command.stacklimit.invalid",
                    Map.of("input", text));
            return null;
        }
        return parsed;
    }

    /** A resolved command target. */
    private record ResolvedTarget(UUID uuid, String name, Player online) {
    }

    private ResolvedTarget resolveTarget(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new ResolvedTarget(online.getUniqueId(), online.getName(), online);
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline == null) {
            plugin.messageService().send(sender, "general.player_not_found", Map.of("player", name));
            return null;
        }
        String resolvedName = offline.getName() == null ? name : offline.getName();
        return new ResolvedTarget(offline.getUniqueId(), resolvedName, null);
    }

    /**
     * Runs a mutation against a target's storage on the correct owner thread.
     *
     * <p>Online targets are dispatched to their own region thread, not the caller's. Offline targets
     * are refused: mutating an unloaded storage would race the player's next login.
     */
    private void withStorage(CommandSender sender, ResolvedTarget target,
            java.util.function.Consumer<PlayerStorage> mutation) {
        if (target.online() == null) {
            plugin.messageService().send(sender, "command.target_offline",
                    Map.of("player", target.name()));
            return;
        }
        plugin.runOwnerWriteAsync(target.online(), () -> {
            PlayerStorage storage = plugin.dataStore().cached(target.uuid());
            if (storage == null) {
                plugin.messageService().send(sender, "general.data_loading");
                return Boolean.FALSE;
            }
            mutation.accept(storage);
            return Boolean.TRUE;
        }, () -> Boolean.FALSE);
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission(PERMISSION_ADMIN)
                || (plugin.appConfig().opBypass() && sender.isOp());
    }

    private void sendHelp(CommandSender sender) {
        var messages = plugin.messageService();
        messages.sendRaw(sender, messages.message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("", messages.message("command.help.commands.open_self"));
        lines.put("open <player>", messages.message("command.help.commands.open"));
        lines.put("slot grant <player> <amount>", messages.message("command.help.commands.slot"));
        lines.put("stacklimit player <player> <limit>",
                messages.message("command.help.commands.stacklimit_player"));
        lines.put("stacklimit slot <player> <slot> <limit>",
                messages.message("command.help.commands.stacklimit_slot"));
        lines.put("info <player>", messages.message("command.help.commands.info"));
        lines.put("export <player>", messages.message("command.help.commands.export"));
        lines.put("reload", messages.message("command.help.commands.reload"));
        lines.put("debug", messages.message("command.help.commands.debug"));
        lines.forEach((usage, description) -> messages.sendRaw(sender,
                messages.message("command.help.line", Map.of("cmd", usage, "desc", description))));
        messages.sendRaw(sender, messages.message("command.help.footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return CommandTabHelper.completeSubcommands(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "open", "info", "export" -> args.length == 2
                    ? CommandTabHelper.completeOnlinePlayers(args[1])
                    : List.of();
            case "slot" -> switch (args.length) {
                case 2 -> CommandTabHelper.completeLiterals(args[1], "grant");
                case 3 -> CommandTabHelper.completeOnlinePlayers(args[2]);
                default -> List.of();
            };
            case "stacklimit" -> switch (args.length) {
                case 2 -> CommandTabHelper.completeLiterals(args[1], "player", "slot");
                case 3 -> CommandTabHelper.completeOnlinePlayers(args[2]);
                case 4 -> args[1].equalsIgnoreCase("player")
                        ? CommandTabHelper.completeLiterals(args[3], "0", "100", "max")
                        : List.of();
                case 5 -> args[1].equalsIgnoreCase("slot")
                        ? CommandTabHelper.completeLiterals(args[4], "0", "100", "max")
                        : List.of();
                default -> List.of();
            };
            case "debug" -> plugin.debugCommand()
                    .tabComplete(Arrays.copyOfRange(args, 1, args.length));
            default -> List.of();
        };
    }

    /** {@return the storage keys of a player, used by the export dump} */
    static List<StorageKey> orderedKeys(PlayerStorage storage) {
        return new ArrayList<>(storage.entryOrder());
    }
}
