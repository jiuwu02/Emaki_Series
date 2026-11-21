package emaki.jiuwu.craft.mobs.command;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.mobs.EmakiMobsPlugin;
import emaki.jiuwu.craft.mobs.loader.MobSpec;
import emaki.jiuwu.craft.mobs.service.MobFactory;
import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;
import emaki.jiuwu.craft.corelib.log.LogTags;
import emaki.jiuwu.craft.corelib.log.LogTag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class MobsCommandRouter {

    private static final String PERMISSION_SPAWN = "emakimobs.spawn";
    private static final String PERMISSION_RELOAD = "emakimobs.reload";
    private static final String PERMISSION_LIST = "emakimobs.list";
    private static final String PERMISSION_INFO = "emakimobs.info";
    private static final String PERMISSION_COUNT = "emakimobs.count";
    private static final String PERMISSION_DEBUG = "emakimobs.debug";
    private static final String PERMISSION_EDIT = "emakimobs.edit";
    private static final String PERMISSION_ADMIN = "emakimobs.admin";
    private static final String HELP_ROOT = "/emakimobs";
    private static final int PAGE_SIZE = 10;
    private static final int FIND_LIST_LIMIT = 20;
    private static final List<String> SUBCOMMANDS =
            List.of("help", "spawn", "reload", "list", "info", "kill", "count", "find", "tp",
                    "debug", "edit");

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
            case "help" -> sendHelp(sender, Arrays.asList(args).subList(1, args.length));
            case "spawn" -> handleSpawn(sender, args);
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender, args);
            case "kill" -> handleKill(sender, args);
            case "count" -> handleCount(sender, args);
            case "find" -> handleFind(sender, args);
            case "tp" -> handleTp(sender, args);
            case "debug" -> handleDebug(sender, args);
            case "edit" -> handleEdit(sender, args);
            default -> plugin.messageService().send(sender, "command.usage");
        }
    }

    public List<String> suggest(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            List<String> result = CommandTabHelper.filterByPrefix(SUBCOMMANDS,
                    args.length == 0 ? "" : args[0]);
            result.removeIf(subcommand -> !"help".equals(subcommand)
                    && !sender.hasPermission(permissionFor(subcommand)));
            return result;
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (!"help".equals(subcommand) && !sender.hasPermission(permissionFor(subcommand))) {
            return List.of();
        }
        if (("spawn".equals(subcommand) || "info".equals(subcommand)
                || "count".equals(subcommand) || "find".equals(subcommand))
                && args.length == 2) {
            return mobIdSuggestions(args[1]);
        }
        if ("kill".equals(subcommand) && args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            if ("all".startsWith(prefix)) {
                result.add("all");
            }
            result.addAll(mobIdSuggestions(args[1]));
            return result;
        }
        if ("spawn".equals(subcommand) && args.length == 3) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }
        if ("list".equals(subcommand) && args.length == 2) {
            int pages = Math.max(1, (plugin.mobRegistry().get().size() + PAGE_SIZE - 1) / PAGE_SIZE);
            return CommandTabHelper.completeIntRange(args[1], 1, pages);
        }
        if ("edit".equals(subcommand) && args.length == 2) {
            if (plugin.editorModule() == null) {
                return List.of();
            }
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.editorModule().completeIds().stream()
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if ("tp".equals(subcommand) && args.length == 2 && sender instanceof Player player) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            if (plugin.mobIdentifier() != null) {
                for (Entity entity : player.getNearbyEntities(64, 64, 64)) {
                    if (entity instanceof LivingEntity living && plugin.mobIdentifier().readId(living) != null) {
                        String uuid = entity.getUniqueId().toString();
                        if (uuid.startsWith(prefix)) {
                            result.add(uuid);
                        }
                    }
                }
            }
            return result;
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
        MobSpec spec = plugin.mobRegistry().get().get(mobId);
        if (spec != null && MobFactory.blockedByDifficulty(target.getWorld(), spec.entityType())) {
            plugin.messageService().send(sender, "command.spawn_blocked", Map.of("mob_id", mobId));
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
        boolean all = "all".equalsIgnoreCase(args[1]);
        String mobId = all ? null : args[1];
        if (!all && !plugin.mobRegistry().get().containsKey(mobId)) {
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
            deliver(sender, all ? "command.kill_all_success" : "command.kill_success", Map.of(
                    "mob_id", all ? "all" : mobId,
                    "count", count));
        });
    }

    private void handleFind(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_COUNT)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.find_usage");
            return;
        }
        String mobId = args[1];
        if (!plugin.mobRegistry().get().containsKey(mobId)) {
            plugin.messageService().send(sender, "command.unknown_mob", Map.of("mob_id", mobId));
            return;
        }
        double radius = -1D;
        if (args.length >= 3) {
            radius = parseNonNegativeDouble(args[2]);
            if (radius < 0D) {
                plugin.messageService().send(sender, "command.invalid_radius", Map.of("radius", args[2]));
                return;
            }
        }
        double selectionRadius = radius;
        managedMobService.find(mobId, player.getLocation(), selectionRadius).whenComplete((found, throwable) -> {
            if (throwable != null) {
                reportOperationFailure(sender, "find", throwable);
                return;
            }
            List<ManagedMobCommandService.Match> matches = new ArrayList<>(found);
            matches.sort(Comparator.comparingDouble(ManagedMobCommandService.Match::distanceSquared));
            List<ManagedMobCommandService.Match> ordered = List.copyOf(matches);
            dispatchTo(player, () -> sendFindResult(player, mobId, selectionRadius, ordered));
        });
    }

    private void sendFindResult(Player player, String mobId, double radius,
                                List<ManagedMobCommandService.Match> matches) {
        plugin.messageService().send(player, "command.find_header", Map.of(
                "mob_id", mobId,
                "count", matches.size(),
                "radius", radius < 0D ? "∞" : String.valueOf(Math.round(radius))));
        if (matches.isEmpty()) {
            plugin.messageService().send(player, "command.find_empty");
            return;
        }
        int shown = Math.min(matches.size(), FIND_LIST_LIMIT);
        for (int index = 0; index < shown; index++) {
            ManagedMobCommandService.Match match = matches.get(index);
            long distance = Math.round(Math.sqrt(match.distanceSquared()));
            String hover = plugin.messageService().message("command.find_entry_hover", Map.of(
                    "mob_id", match.mobId(),
                    "uuid", match.uuid().toString(),
                    "health", formatNumber(match.health()),
                    "max_health", formatNumber(match.maxHealth())));
            plugin.messageService().sendRaw(player, plugin.messageService().message(
                    "command.find_entry", Map.of(
                            "hover", hover,
                            "uuid", match.uuid().toString(),
                            "mob_id", match.mobId(),
                            "distance", distance,
                            "world", match.worldName(),
                            "x", match.blockX(),
                            "y", match.blockY(),
                            "z", match.blockZ())));
        }
        if (matches.size() > shown) {
            plugin.messageService().send(player, "command.find_truncated", Map.of(
                    "count", matches.size() - shown));
        }
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (!requirePermission(sender, PERMISSION_ADMIN)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "command.tp_usage");
            return;
        }
        UUID targetId;
        try {
            targetId = UUID.fromString(args[1]);
        } catch (IllegalArgumentException exception) {
            plugin.messageService().send(sender, "command.tp_invalid", Map.of("value", args[1]));
            return;
        }
        Entity entity = Bukkit.getEntity(targetId);
        if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()
                || !plugin.mobIdentifier().isManaged(living)) {
            plugin.messageService().send(sender, "command.tp_not_managed", Map.of("value", args[1]));
            return;
        }
        Location destination = living.getLocation();
        TaskToken token = plugin.executionDispatcher().runEntity(plugin, player, () -> {
            player.teleport(destination);
            plugin.messageService().send(player, "command.tp_success", Map.of("value", args[1]));
        }, () -> plugin.messageService().send(player, "command.tp_failed"));
        if (token == null || token.cancelled()) {
            plugin.messageService().send(player, "command.tp_failed");
        }
    }

    private static String formatNumber(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private void handleEdit(CommandSender sender, String[] args) {
        EditorCommandSupport.Guard editGuard = EditorCommandSupport.check(
                sender, () -> plugin.editorService() != null && plugin.editorModule() != null, PERMISSION_EDIT);
        if (editGuard == Guard.DENIED) {
            plugin.messageService().send(sender, "command.no_permission");
            return;
        }
        if (editGuard == Guard.NON_PLAYER) {
            plugin.messageService().send(sender, "general.player_only");
            return;
        }
        if (editGuard == Guard.UNAVAILABLE) {
            return;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            plugin.editorService().openBrowser(player);
            return;
        }
        if (plugin.editorModule().openById(player, args[1]) == null) {
            plugin.messageService().send(sender, "editor.open_failed", Map.of("id", args[1]));
        }
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
        dispatchTo(sender, () -> {
            if (plugin.isEnabled() && !plugin.isShutdownStarted()) {
                plugin.messageService().send(sender, key, replacements);
            }
        });
    }

    private void dispatchTo(CommandSender sender, Runnable delivery) {
        if (!plugin.isEnabled() || plugin.isShutdownStarted()) {
            return;
        }
        TaskToken token;
        if (sender instanceof Player player) {
            token = plugin.executionDispatcher().runEntity(plugin, player, delivery,
                    () -> plugin.messageService().warning(LogTag.COMMAND, "console.command_result_recipient_retired"));
        } else {
            token = plugin.executionDispatcher().runGlobal(plugin, delivery);
        }
        if (token == null || token.cancelled()) {
            plugin.messageService().warning(LogTag.COMMAND, "console.command_result_schedule_failed");
        }
    }

    private void reportOperationFailure(CommandSender sender, String operation, Throwable throwable) {
        LogTags.sendRaw(plugin, LogTag.COMMAND, Level.WARNING, plugin.messageService().message("console.command_operation_failed", Map.of("operation", operation)));
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

    private void sendHelp(CommandSender sender, List<String> path) {
        sendHelpPage(sender, HELP_ROOT, path);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_help", "command.help_entry_spawn", "command.help_entry_reload",
                    "command.help_entry_list", "command.help_entry_info", "command.help_entry_count",
                    "command.help_entry_find", "command.help_entry_kill", "command.help_entry_tp",
                    "command.help_entry_debug", "command.help_entry_edit");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "spawn" -> sendMobIdHelp(sender, root, "spawn",
                    "command.help_spawn_header", "command.help_value_desc_spawn");
            case "info" -> sendMobIdHelp(sender, root, "info",
                    "command.help_info_header", "command.help_value_desc_info");
            case "find" -> sendMobIdHelp(sender, root, "find",
                    "command.help_find_header", "command.help_value_desc_find");
            case "kill" -> sendKillHelp(sender, root);
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendMobIdHelp(CommandSender sender, String root, String command,
            String headerKey, String descKey) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message(headerKey, Map.of("root", root)));
        List<String> ids = new ArrayList<>(plugin.mobRegistry().get().keySet());
        ids.sort(Comparator.naturalOrder());
        if (ids.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String id : ids) {
            sendValueEntry(sender, id,
                    messageService.message(descKey, Map.of("value", id)),
                    root + " " + command + " " + id);
        }
        sendHelpFooter(sender);
    }

    private void sendKillHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_kill_header", Map.of("root", root)));
        sendValueEntry(sender, "all",
                messageService.message("command.help_value_desc_kill_all"),
                root + " kill all");
        List<String> ids = new ArrayList<>(plugin.mobRegistry().get().keySet());
        ids.sort(Comparator.naturalOrder());
        for (String id : ids) {
            sendValueEntry(sender, id,
                    messageService.message("command.help_value_desc_kill", Map.of("value", id)),
                    root + " kill " + id);
        }
        sendHelpFooter(sender);
    }

    private void sendDebugHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() < 2) {
            sendHelpLines(sender, root, "command.help_debug_header",
                    "command.help_debug_entry_status", "command.help_debug_entry_on",
                    "command.help_debug_entry_off", "command.help_debug_entry_player",
                    "command.help_debug_entry_module", "command.help_debug_entry_action");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(1).toLowerCase(Locale.ROOT)) {
            case "player" -> sendPlayerHelp(sender, root, "debug player",
                    "command.help_debug_player_header", "command.help_value_desc_debug_player");
            case "module" -> sendModuleHelp(sender, root);
            case "action" -> sendDebugActionHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of("debug"));
        }
    }

    private void sendPlayerHelp(CommandSender sender, String root, String command,
            String headerKey, String descKey) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message(headerKey, Map.of("root", root)));
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName));
        if (players.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (Player player : players) {
            String name = player.getName();
            sendValueEntry(sender, name,
                    messageService.message(descKey, Map.of("value", name)),
                    root + " " + command + " " + name);
        }
        sendHelpFooter(sender);
    }

    private void sendModuleHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_debug_module_header", Map.of("root", root)));
        List<String> modules = plugin.debugCommand() == null
                ? List.of()
                : plugin.debugCommand().tabComplete(new String[] {"module", ""});
        if (modules.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String module : modules) {
            sendValueEntry(sender, module,
                    messageService.message("command.help_value_desc_debug_module", Map.of("value", module)),
                    root + " debug module " + module);
        }
        sendHelpFooter(sender);
    }

    private void sendDebugActionHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 3 && "run".equalsIgnoreCase(path.get(2))) {
            sendHelpLines(sender, root, "command.help_debug_action_run_header",
                    "command.help_debug_action_run_entry_shadow", "command.help_debug_action_run_entry_execute");
            sendHelpFooter(sender);
            return;
        }
        sendHelpLines(sender, root, "command.help_debug_action_header",
                "command.help_debug_action_entry_run", "command.help_debug_action_entry_check",
                "command.help_debug_action_entry_registry");
        sendHelpFooter(sender);
    }

    private void sendValueEntry(CommandSender sender, String value, String desc, String run) {
        sendHelpLine(sender, plugin.messageService().message("command.help_value_entry", Map.of(
                "value", value,
                "desc", desc,
                "run", run)));
    }

    private void sendHelpLines(CommandSender sender, String root, String... keys) {
        var messageService = plugin.messageService();
        Map<String, ?> replacements = Map.of("root", root);
        for (String key : keys) {
            sendHelpLine(sender, messageService.message(key, replacements));
        }
    }

    private void sendHelpLine(CommandSender sender, String line) {
        var messageService = plugin.messageService();
        messageService.sendComponent(sender, messageService.render(line));
    }

    private void sendHelpFooter(CommandSender sender) {
        sendHelpLine(sender, plugin.messageService().message("command.help_footer"));
    }

    private String permissionFor(String subcommand) {
        return switch (subcommand) {
            case "spawn" -> PERMISSION_SPAWN;
            case "reload" -> PERMISSION_RELOAD;
            case "list" -> PERMISSION_LIST;
            case "info" -> PERMISSION_INFO;
            case "kill" -> PERMISSION_ADMIN;
            case "count" -> PERMISSION_COUNT;
            case "find" -> PERMISSION_COUNT;
            case "tp" -> PERMISSION_ADMIN;
            case "debug" -> PERMISSION_DEBUG;
            case "edit" -> PERMISSION_EDIT;
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
