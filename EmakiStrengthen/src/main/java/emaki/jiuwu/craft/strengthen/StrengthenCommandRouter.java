package emaki.jiuwu.craft.strengthen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.command.CommandTabHelper;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.api.StrengthenOperations;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementOperationView;
import emaki.jiuwu.craft.strengthen.api.model.EnhancementPityStateView;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptService;
import emaki.jiuwu.craft.strengthen.enhancement.pity.InMemoryPityStateStore;
import emaki.jiuwu.craft.strengthen.enhancement.pity.PityPersistenceRetryScheduler;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.legacy.LegacyStrengthenConfigRewriter;
import emaki.jiuwu.craft.strengthen.legacy.LegacyStrengthenConfigRewriter.FileReport;
import emaki.jiuwu.craft.strengthen.legacy.LegacyStrengthenConfigRewriter.RunReport;
import emaki.jiuwu.craft.strengthen.legacy.LegacyStrengthenConfigRewriter.Status;

final class StrengthenCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakistrengthen";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";

    private final EmakiStrengthenPlugin plugin;

    StrengthenCommandRouter(EmakiStrengthenPlugin plugin) {
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
            case "open" -> handleOpen(sender);
            case "affix" -> handleAffix(sender, args);
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender, args);
            case "refresh" -> handleRefresh(sender, args);
            case "setstar" -> handleSetStar(sender, args);
            case "branch" -> handleBranch(sender, args);
            case "clearstate" -> handleClearState(sender);
            case "clearcrack" -> handleClearCrack(sender);
            case "givecatalyst" -> handleGiveCatalyst(sender, args);
            case "operation" -> handleOperation(sender, args);
            case "pity" -> handlePity(sender, args);
            case "convert-legacy" -> handleConvertLegacy(sender, args);
            case "debug" -> handleDebug(sender, args);
            default -> {
                plugin.messageService().send(sender, "general.unknown_command");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("help", "open", "affix", "reload", "inspect", "refresh", "setstar", "branch", "clearstate", "clearcrack", "givecatalyst", "operation", "pity", "convert-legacy", "debug")) {
                if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length >= 2 && "debug".equalsIgnoreCase(args[0])) {
            return plugin.debugCommand().tabComplete(Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "inspect", "refresh" -> result.addAll(CommandTabHelper.completeOnlinePlayers(args[1]));
                case "setstar" -> {
                    int maxStar = plugin.recipeLoader().all().values().stream()
                            .mapToInt(recipe -> recipe == null ? 0 : recipe.limits().maxStar())
                            .max()
                            .orElse(12);
                    for (int star = 0; star <= maxStar; star++) {
                        String value = Integer.toString(star);
                        if (value.startsWith(args[1])) {
                            result.add(value);
                        }
                    }
                }
                case "givecatalyst" -> plugin.recipeLoader().materialCatalog().keySet().stream()
                        .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .forEach(result::add);
                case "operation" -> {
                    if ("list".startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add("list");
                    }
                    if (plugin.enhancementAttemptService() != null) {
                        plugin.enhancementAttemptService().operationViews().stream()
                                .map(EnhancementOperationView::operationId)
                                .filter(id -> id.startsWith(args[1]))
                                .forEach(result::add);
                    }
                }
                case "pity" -> {
                    for (String sub : List.of("list", "set", "clear", "diagnose")) {
                        if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            result.add(sub);
                        }
                    }
                }
                case "convert-legacy" -> {
                    for (String sub : List.of("confirm", "--apply")) {
                        if (sub.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                            result.add(sub);
                        }
                    }
                }
                case "branch" -> {
                    if (sender instanceof Player player) {
                        plugin.attemptService()
                                .selectBranch(player.getInventory().getItemInMainHand(), "")
                                .options()
                                .keySet()
                                .stream()
                                .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                                .forEach(result::add);
                    }
                }
                case "affix" -> plugin.enhancementRecipeLoader().all().values().stream()
                        .filter(recipe -> recipe != null
                                && "affix".equals(Texts.lower(recipe.target().provider())))
                        .map(recipe -> recipe.id())
                        .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .forEach(result::add);
                default -> {
                }
            }
            return result;
        }
        if (args.length == 3) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "setstar" -> plugin.recipeLoader().all().keySet().stream()
                        .filter(id -> id.startsWith(args[2].toLowerCase(Locale.ROOT)))
                        .forEach(result::add);
                case "givecatalyst" -> {
                    for (String amount : List.of("1", "8", "16", "32", "64")) {
                        if (amount.startsWith(args[2])) {
                            result.add(amount);
                        }
                    }
                }
                default -> {
                }
            }
            return result;
        }
        if (args.length == 4 && "givecatalyst".equalsIgnoreCase(args[0])) {
            result.addAll(CommandTabHelper.completeOnlinePlayers(args[3]));
        }
        return result;
    }

    private boolean handleAffix(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (plugin.affixGuiService() == null) {
            plugin.messageService().send(sender, "gui.open_failed");
            return true;
        }
        String recipeId = args.length >= 2 ? args[1] : "";
        plugin.affixGuiService().open(player, recipeId);
        return true;
    }

    private boolean handleConvertLegacy(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        boolean apply = args.length >= 2
                && ("confirm".equalsIgnoreCase(args[1]) || "--apply".equalsIgnoreCase(args[1]));
        LegacyStrengthenConfigRewriter rewriter = new LegacyStrengthenConfigRewriter(
                plugin.dataPath("recipes"), plugin.getLogger());
        RunReport report = rewriter.run(apply);
        var ms = plugin.messageService();
        ms.sendRaw(sender, ms.message("command.convert_legacy.header", Map.of(
                "mode", ms.message(apply ? "command.convert_legacy.mode.apply" : "command.convert_legacy.mode.dry_run"),
                "files", report.files().size()
        )));
        for (FileReport file : report.files()) {
            sendConvertLegacyFile(sender, file, apply);
        }
        ms.sendRaw(sender, ms.message("command.convert_legacy.summary", Map.of(
                "converted", report.count(Status.CONVERTED),
                "skipped", report.count(Status.NO_LEGACY_BLOCK),
                "conflict", report.count(Status.CONFLICT),
                "unconvertible", report.count(Status.UNCONVERTIBLE),
                "failed", report.count(Status.FAILED)
        )));
        if (!apply) {
            ms.send(sender, report.hasConvertible()
                    ? "command.convert_legacy.dry_run_hint"
                    : "command.convert_legacy.nothing_to_do");
        }
        return true;
    }

    private void sendConvertLegacyFile(CommandSender sender, FileReport file, boolean apply) {
        var ms = plugin.messageService();
        if (file.status() == Status.NO_LEGACY_BLOCK) {
            return;
        }
        ms.sendRaw(sender, ms.message("command.convert_legacy.file", Map.of(
                "file", file.fileName(),
                "status", ms.message("command.convert_legacy.status." + Texts.lower(file.status().name())),
                "detail", file.detail()
        )));
        if (apply && Texts.isNotBlank(file.backupName())) {
            ms.sendRaw(sender, ms.message("command.convert_legacy.backup", Map.of("backup", file.backupName())));
        }
        if (!apply) {
            for (String line : file.diff()) {
                ms.sendRaw(sender, ms.message("command.convert_legacy.diff_line", Map.of("line", line)));
            }
        }
    }

    private boolean handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (!plugin.strengthenGuiService().open(player)) {
            plugin.messageService().send(sender, "gui.open_failed");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        plugin.bootstrapService().bootstrap();
        plugin.messageService().send(sender, "general.reloading");
        long startTime = System.currentTimeMillis();
        plugin.reloadPluginStateAsync(true).thenRun(() -> runForSender(sender, () -> {
            long elapsedMs = System.currentTimeMillis() - startTime;
            plugin.messageService().send(sender, "general.reload_success");
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_summary", Map.of(
                    "recipes", plugin.recipeLoader().all().size(),
                    "materials", plugin.recipeLoader().materialCatalog().size(),
                    "guis", plugin.guiTemplateLoader().all().size()
            )));
            plugin.messageService().sendRaw(sender, "<gray>重载耗时: <white>" + elapsedMs + "ms</white></gray>");
        }));
        return true;
    }

    private void runForSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player player) {
            plugin.executionDispatcher().runEntity(plugin, player, task);
            return;
        }
        plugin.executionDispatcher().runGlobal(plugin, task);
    }

    private boolean handleInspect(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN) && !sender.hasPermission(PERMISSION_USE)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player player = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (player == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        StrengthenState state = plugin.attemptService().readState(player.getInventory().getItemInMainHand());
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.header", Map.of("player", player.getName())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "eligible", "value", state.eligible())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "reason",
                "value", state.eligibleReason().isBlank() ? "-" : state.eligibleReason()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "recipe",
                "value", state.recipeId().isBlank() ? "-" : state.recipeId()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "source",
                "value", Texts.isBlank(state.baseSource()) ? "-" : state.baseSource()
        )));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "star", "value", state.currentStar())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of("key", "temper", "value", state.crackLevel())));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.inspect.line", Map.of(
                "key", "first_reach",
                "value", state.firstReachFlags().isEmpty() ? "-" : state.firstReachFlags()
        )));
        return true;
    }

    private boolean handleOperation(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        EnhancementAttemptService service = plugin.enhancementAttemptService();
        if (service == null) {
            plugin.messageService().send(sender, "command.operation.unavailable");
            return true;
        }
        if (args.length >= 2 && !"list".equalsIgnoreCase(args[1])) {
            EnhancementOperationView view = service.operationView(args[1]);
            if (view == null) {
                plugin.messageService().send(sender, "command.operation.not_found",
                        Map.of("operation_id", args[1]));
                return true;
            }
            sendOperationLine(sender, view);
            return true;
        }
        List<EnhancementOperationView> views = service.operationViews();
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.operation.header",
                Map.of("count", views.size())));
        if (views.isEmpty()) {
            plugin.messageService().send(sender, "command.operation.empty");
            return true;
        }
        views.forEach(view -> sendOperationLine(sender, view));
        return true;
    }

    private boolean handlePity(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        InMemoryPityStateStore store = plugin.pityStateStore();
        if (store == null) {
            plugin.messageService().send(sender, "command.pity.unavailable");
            return true;
        }
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "list";
        return switch (action) {
            case "diagnose" -> handlePityDiagnose(sender, store);
            case "set" -> handlePitySet(sender, args);
            case "clear" -> handlePityClear(sender, args);
            default -> handlePityList(sender, args);
        };
    }

    private boolean handlePityList(CommandSender sender, String[] args) {
        String group = args.length >= 3 ? args[2] : "";
        EmakiResult<List<EnhancementPityStateView>> result = pityOperations().pityStates(group);
        if (!result.isSuccess()) {
            plugin.messageService().send(sender, "command.pity.unavailable");
            return true;
        }
        List<EnhancementPityStateView> views = result.orElse(List.of());
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.pity.header",
                Map.of("count", views.size())));
        if (views.isEmpty()) {
            plugin.messageService().send(sender, "command.pity.empty");
            return true;
        }
        for (EnhancementPityStateView view : views) {
            plugin.messageService().sendRaw(sender, plugin.messageService().message("command.pity.line", Map.of(
                    "group", view.group(),
                    "scope", view.scope(),
                    "key", view.ownerKey(),
                    "counter", view.counter(),
                    "triggered", view.triggered()
            )));
        }
        return true;
    }

    private boolean handlePitySet(CommandSender sender, String[] args) {
        if (args.length < 6) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Integer counter = Numbers.tryParseInt(args[5], null);
        if (counter == null || counter < 0) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        EmakiResult<Unit> result = pityOperations().setPityCounter(args[2], args[3], args[4], counter);
        if (!result.isSuccess()) {
            plugin.messageService().send(sender, "command.pity.invalid_scope",
                    Map.of("legal", "player, item"));
            return true;
        }
        plugin.messageService().send(sender, "command.pity.set_success",
                Map.of("group", args[3], "counter", counter));
        return true;
    }

    private boolean handlePityClear(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        EmakiResult<Integer> result = pityOperations().clearPityGroup(args[2]);
        if (!result.isSuccess()) {
            plugin.messageService().send(sender, "command.pity.not_found");
            return true;
        }
        plugin.messageService().send(sender, "command.pity.clear_success",
                Map.of("count", result.orElse(0)));
        return true;
    }

    private boolean handlePityDiagnose(CommandSender sender, InMemoryPityStateStore store) {
        PityPersistenceRetryScheduler scheduler = plugin.pityRetryScheduler();
        plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.pity.diagnose_header"));
        Map<String, Object> lines = new LinkedHashMap<>();
        lines.put("records", store.size());
        lines.put("dirty", store.isDirty());
        lines.put("persistent", store.persistent());
        lines.put("retry_running", scheduler != null && scheduler.running());
        lines.put("retry_attempts", scheduler == null ? 0 : scheduler.attemptCount());
        lines.forEach((key, value) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.pity.diagnose_line",
                        Map.of("key", key, "value", value))));
        return true;
    }

    private StrengthenOperations pityOperations() {
        return EmakiStrengthenApi.operations();
    }

    private void sendOperationLine(CommandSender sender, EnhancementOperationView view) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.operation.line", Map.of(
                "operation_id", view.operationId(),
                "player", view.playerId() == null ? "-" : view.playerId().toString(),
                "phase", view.phase().isBlank() ? "-" : view.phase(),
                "pending", view.compensationPending()
        )));
    }

    private boolean handleRefresh(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        Player player = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : (sender instanceof Player self ? self : null);
        if (player == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        plugin.refreshService().refreshPlayerInventory(player);
        plugin.messageService().send(sender, "command.refresh.success", Map.of("player", player.getName()));
        return true;
    }

    private boolean handleSetStar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Integer star = Numbers.tryParseInt(args[1], null);
        if (star == null) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        ItemStack rebuilt = plugin.attemptService().applyAdminState(
                player.getInventory().getItemInMainHand(),
                star,
                null,
                args.length >= 3 ? args[2] : null
        );
        if (rebuilt == null) {
            plugin.messageService().send(sender, "command.admin_state_failed");
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        plugin.messageService().send(sender, "command.setstar.success", Map.of("star", star));
        return true;
    }

    private boolean handleBranch(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        StrengthenAttemptService.BranchSelection selection =
                plugin.attemptService().selectBranch(held, args.length >= 2 ? args[1] : "");
        switch (selection.outcome()) {
            case FAILED -> plugin.messageService().send(sender, selection.errorKey());
            case PENDING_CHOICE -> sendBranchOptions(sender, selection, "command.branch.options");
            case INVALID_CHOICE -> sendBranchOptions(sender, selection, "command.branch.invalid");
            case APPLIED -> {
                player.getInventory().setItemInMainHand(selection.rebuilt());
                plugin.messageService().send(sender, "command.branch.success", Map.of(
                        "branch", selection.displayName(),
                        "path", selection.branchPath()
                ));
            }
        }
        return true;
    }

    private void sendBranchOptions(CommandSender sender,
            StrengthenAttemptService.BranchSelection selection,
            String headerKey) {
        plugin.messageService().send(sender, headerKey);
        selection.options().forEach((id, displayName) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.branch.option", Map.of(
                        "id", id,
                        "name", displayName
                ))));
    }

    private boolean handleClearState(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (!plugin.attemptService().readState(player.getInventory().getItemInMainHand()).hasLayer()) {
            plugin.messageService().send(sender, "command.clearstate.no_layer");
            return true;
        }
        ItemStack rebuilt = plugin.attemptService().clearStrengthenLayer(player.getInventory().getItemInMainHand());
        if (rebuilt == null) {
            plugin.messageService().send(sender, "command.admin_state_failed");
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        plugin.messageService().send(sender, "command.clearstate.success");
        return true;
    }

    private boolean handleClearCrack(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        ItemStack rebuilt = plugin.attemptService().applyAdminState(player.getInventory().getItemInMainHand(), null, 0, null);
        if (rebuilt == null) {
            plugin.messageService().send(sender, "command.admin_state_failed");
            return true;
        }
        player.getInventory().setItemInMainHand(rebuilt);
        plugin.messageService().send(sender, "command.clearcrack.success");
        return true;
    }

    private boolean handleGiveCatalyst(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        String materialToken = plugin.recipeLoader().resolveMaterialToken(args[1]);
        if (Texts.isBlank(materialToken)) {
            plugin.messageService().send(sender, "command.catalyst_not_found");
            return true;
        }
        Integer amount = args.length >= 3 ? Numbers.tryParseInt(args[2], null) : 1;
        if (amount == null || amount <= 0) {
            plugin.messageService().send(sender, "general.invalid_args");
            return true;
        }
        Player target = args.length >= 4 ? Bukkit.getPlayerExact(args[3]) : (sender instanceof Player self ? self : null);
        if (target == null) {
            plugin.messageService().send(sender, "general.player_not_found");
            return true;
        }
        ItemStack itemStack = createMaterialItem(materialToken, amount);
        if (itemStack == null) {
            plugin.messageService().send(sender, "command.catalyst_create_failed");
            return true;
        }
        InventoryItemUtil.giveOrDrop(target, itemStack);
        plugin.messageService().send(sender, "command.givecatalyst.success", Map.of(
                "player", target.getName(),
                "material", materialToken,
                "amount", amount
        ));
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION_DEBUG) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, "general.no_permission");
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private ItemStack createMaterialItem(String materialToken, int amount) {
        return plugin.coreItemFactory().create(ItemSourceUtil.parse(materialToken), Math.max(1, amount));
    }

    private void sendHelp(CommandSender sender) {
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.header"));
        Map<String, String> lines = new LinkedHashMap<>();
        lines.put("help", plugin.messageService().message("command.help.desc.help"));
        lines.put("open", plugin.messageService().message("command.help.desc.open"));
        lines.put("affix [recipe]", plugin.messageService().message("command.help.desc.affix"));
        lines.put("reload", plugin.messageService().message("command.help.desc.reload"));
        lines.put("inspect [player]", plugin.messageService().message("command.help.desc.inspect"));
        lines.put("refresh [player]", plugin.messageService().message("command.help.desc.refresh"));
        lines.put("setstar <star> [recipe]", plugin.messageService().message("command.help.desc.setstar"));
        lines.put("branch [id]", plugin.messageService().message("command.help.desc.branch"));
        lines.put("clearstate", plugin.messageService().message("command.help.desc.clearstate"));
        lines.put("clearcrack", plugin.messageService().message("command.help.desc.clearcrack"));
        lines.put("givecatalyst <id> [amount] [player]", plugin.messageService().message("command.help.desc.givecatalyst"));
        lines.put("operation [list|<id>]", plugin.messageService().message("command.help.desc.operation"));
        lines.put("pity [list|set|clear|diagnose] [...]", plugin.messageService().message("command.help.desc.pity"));
        lines.put("convert-legacy [confirm]", plugin.messageService().message("command.help.desc.convert_legacy"));
        lines.put("debug <status|player|module|all> [...]", plugin.messageService().message("command.help.desc.debug"));
        lines.forEach((name, description) -> plugin.messageService().sendRaw(sender,
                plugin.messageService().message("command.help.line", Map.of("cmd", name, "desc", description))));
        plugin.messageService().sendRaw(sender, plugin.messageService().message("command.help.footer"));
    }
}
