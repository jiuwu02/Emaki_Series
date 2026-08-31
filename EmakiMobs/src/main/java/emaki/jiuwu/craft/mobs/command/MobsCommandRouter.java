package emaki.jiuwu.craft.mobs.command;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class MobsCommandRouter {

    private static final String PERMISSION_SPAWN = "emakimobs.spawn";
    private static final String PERMISSION_RELOAD = "emakimobs.reload";
    private static final String PERMISSION_LIST = "emakimobs.list";
    private static final String PERMISSION_INFO = "emakimobs.info";
    private static final String PERMISSION_COUNT = "emakimobs.count";
    private static final String PERMISSION_DEBUG = "emakimobs.debug";
    private static final String PERMISSION_ADMIN = "emakimobs.admin";
    private static final int PAGE_SIZE = 10;
    private static final List<String> SUBCOMMANDS =
            List.of("spawn", "reload", "list", "info", "kill", "count", "debug");

    private final EmakiMobsPlugin plugin;
    private final ManagedMobCommandService managedMobService;

    public MobsCommandRouter(EmakiMobsPlugin plugin) {
        this.plugin = plugin;
        this.managedMobService = new ManagedMobCommandService(plugin);
    }

    public void route(CommandSender sender, String[] args) {
        if (args.length == 0) {
            plugin.messageService().send(sender, "command.usage");
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> handleSpawn(sender, args);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "kill" -> handleKill(sender, args);
            case "count" -> handleCount(sender, args);
            case "debug" -> handleDebug(sender, args);
            default -> plugin.messageService().send(sender, "command.usage");
        }
    }

    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String subcommand : SUBCOMMANDS) {
                if (subcommand.startsWith(prefix) && sender.hasPermission(permissionFor(subcommand))) {
                    result.add(subcommand);
                }
            }
            return result;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(permissionFor(subcommand))) {
            return List.of();
        }
        if (("spawn".equals(subcommand) || "info".equals(subcommand)
                || "kill".equals(subcommand) || "count".equals(subcommand))
                && args.length == 2) {
            return mobIdSuggestions(args[1]);
        }
        if ("spawn".equals(subcommand) && args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        if ("list".equals(subcommand) && args.length == 2 && "1".startsWith(args[1])) {
            return List.of("1");
        }
        if ("debug".equals(subcommand) && plugin.debugCommand() != null) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        return List.of();
    }

    private void handleSpawn(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_SPAWN)) {
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.spawn_usage");
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
            plugin.messageService().send(sender, "command.spawn_success", Map.of("mob_id", mobId));
        } else {
            plugin.messageService().send(sender, "command.unknown_mob", Map.of("mob_id", mobId));
        }
    }

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, PERMISSION_RELOAD)) {
            return;
        }
        int count = plugin.reloadContent();
        plugin.messageService().send(sender, "command.reload_success",
                Map.of("count", String.valueOf(count)));
    }

    private void handleList(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_LIST)) {
            return;
        }
        int page = 1;
        if (args.length >= 2) {
            page = parsePositiveInt(args[1]);
            if (page < 1) {
                plugin.messageService().send(sender, "command.invalid_page", Map.of("page", args[1]));
                return;
            }
        }
        List<MobSpec> definitions = new ArrayList<>(plugin.mobRegistry().get().values());
        definitions.sort(Comparator.comparing(MobSpec::id));
        int pages = Math.max(1, (definitions.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) {
            plugin.messageService().send(sender, "command.list_page_out_of_range", Map.of(
                    "page", page,
                    "pages", pages));
            return;
        }
        plugin.messageService().send(sender, "command.list_header", Map.of(
                "page", page,
                "pages", pages,
                "count", definitions.size()));
        if (definitions.isEmpty()) {
            plugin.messageService().send(sender, "command.list_empty");
            return;
        }
        int fromIndex = (page - 1) * PAGE_SIZE;
        int toIndex = Math.min(definitions.size(), fromIndex + PAGE_SIZE);
        for (MobSpec spec : definitions.subList(fromIndex, toIndex)) {
            plugin.messageService().send(sender, "command.list_entry", Map.of(
                    "mob_id", spec.id(),
                    "entity_type", spec.entityType().getKey().asString()));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_INFO)) {
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.info_usage");
            return;
        }
        MobSpec spec = plugin.mobRegistry().get().get(args[1]);
        if (spec == null) {
            plugin.messageService().send(sender, "command.unknown_mob", Map.of("mob_id", args[1]));
            return;
        }
        List<String> triggers = spec.actions().keySet().stream().sorted().toList();
        boolean hasLootTable = plugin.lootTableLoader().all().containsKey(spec.id());
        plugin.messageService().send(sender, "command.info_result", Map.of(
                "mob_id", spec.id(),
                "entity_type", spec.entityType().getKey().asString(),
                "components", spec.components().size(),
                "attributes", spec.eaAttributes().size(),
                "triggers", triggers.isEmpty() ? "-" : String.join(", ", triggers),
                "loot_table", plugin.messageService().message(
                        hasLootTable ? "command.value_yes" : "command.value_no")));
    }

    private void handleCount(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_COUNT)) {
            return;
        }
        String mobId = args.length >= 2 ? args[1] : null;
        if (mobId != null && !plugin.mobRegistry().get().containsKey(mobId)) {
            plugin.messageService().send(sender, "command.unknown_mob", Map.of("mob_id", mobId));
            return;
        }
        managedMobService.count(mobId).whenComplete((count, throwable) -> {
            if (throwable != null) {
                reportOperationFailure(sender, "count", throwable);
                return;
            }
            deliver(sender, "command.count_success", Map.of(
                    "mob_id", mobId == null ? "*" : mobId,
                    "count", count));
        });
    }

    private void handleKill(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_ADMIN)) {
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.kill_usage");
            return;
        }
        String mobId = args[1];
        if (!plugin.mobRegistry().get().containsKey(mobId)) {
            plugin.messageService().send(sender, "command.unknown_mob", Map.of("mob_id", mobId));
            return;
        }
        Location center = null;
        double radius = -1D;
        if (args.length >= 3) {
            if (!(sender instanceof Player player)) {
                plugin.messageService().send(sender, "command.kill_radius_player_only");
                return;
            }
            radius = parseNonNegativeDouble(args[2]);
            if (radius < 0D) {
                plugin.messageService().send(sender, "command.invalid_radius", Map.of("radius", args[2]));
                return;
            }
            center = player.getLocation();
        }
        managedMobService.kill(mobId, center, radius).whenComplete((count, throwable) -> {
            if (throwable != null) {
                reportOperationFailure(sender, "kill", throwable);
                return;
            }
            deliver(sender, "command.kill_success", Map.of(
                    "mob_id", mobId,
                    "count", count));
        });
    }

    private void handleDebug(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_DEBUG)) {
            return;
        }
        if (plugin.debugCommand() == null) {
            plugin.messageService().send(sender, "command.debug_unavailable");
            return;
        }
        plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length),
                plugin.messageService());
    }

    private void deliver(CommandSender sender, String key, Map<String, ?> replacements) {
        if (!plugin.isEnabled() || plugin.isShutdownStarted()) {
            return;
        }
        Runnable delivery = () -> {
            if (plugin.isEnabled() && !plugin.isShutdownStarted()) {
                plugin.messageService().send(sender, key, replacements);
            }
        };
        TaskToken token;
        if (sender instanceof Player player) {
            token = plugin.executionDispatcher().runEntity(plugin, player, delivery,
                    () -> plugin.getLogger().warning(
                            "[EmakiMobs] Command result recipient retired before delivery."));
        } else {
            token = plugin.executionDispatcher().runGlobal(plugin, delivery);
        }
        if (token == null || token.cancelled()) {
            plugin.getLogger().warning("[EmakiMobs] Unable to schedule command result delivery.");
        }
    }

    private void reportOperationFailure(CommandSender sender, String operation, Throwable throwable) {
        plugin.getLogger().log(Level.WARNING,
                "[EmakiMobs] Managed mob command operation failed: " + operation, throwable);
        deliver(sender, "command.operation_failed", Map.of("operation", operation));
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messageService().send(sender, "command.no_permission");
        return false;
    }

    private List<String> mobIdSuggestions(String rawPrefix) {
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        return plugin.mobRegistry().get().keySet().stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }

    private String permissionFor(String subcommand) {
        return switch (subcommand) {
            case "spawn" -> PERMISSION_SPAWN;
            case "reload" -> PERMISSION_RELOAD;
            case "list" -> PERMISSION_LIST;
            case "info" -> PERMISSION_INFO;
            case "kill" -> PERMISSION_ADMIN;
            case "count" -> PERMISSION_COUNT;
            case "debug" -> PERMISSION_DEBUG;
            default -> "emakimobs.use";
        };
    }

    private int parsePositiveInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private double parseNonNegativeDouble(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0D ? parsed : -1D;
        } catch (NumberFormatException exception) {
            return -1D;
        }
    }
}
