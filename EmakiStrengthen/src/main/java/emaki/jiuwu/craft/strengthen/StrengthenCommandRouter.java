package emaki.jiuwu.craft.strengthen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import emaki.jiuwu.craft.corelib.api.command.CommandKeys;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport;
import emaki.jiuwu.craft.corelib.command.EditorCommandSupport.Guard;

final class StrengthenCommandRouter implements TabExecutor {

    private static final String PERMISSION_ROOT = "emakistrengthen";
    private static final String PERMISSION_USE = PERMISSION_ROOT + ".use";
    private static final String PERMISSION_RELOAD = PERMISSION_ROOT + ".reload";
    private static final String PERMISSION_ADMIN = PERMISSION_ROOT + ".admin";
    private static final String PERMISSION_DEBUG = PERMISSION_ROOT + ".debug";
    private static final String PERMISSION_EDIT = PERMISSION_ROOT + ".edit";

    private final EmakiStrengthenPlugin plugin;

    StrengthenCommandRouter(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelpPage(sender, helpRoot(label), Arrays.asList(args).subList(1, args.length));
                yield true;
            }
            case "open" -> handleOpen(sender);
            case "affix" -> handleAffix(sender, args);
            case "edit" -> handleEdit(sender, args);
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
            return CommandTabHelper.completeSubcommands(
                    List.of("help", "open", "affix", "edit", "reload", "inspect", "refresh", "setstar",
                            "branch", "clearstate", "clearcrack", "givecatalyst", "operation", "pity",
                            "debug"), args[0]);
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
                case "edit" -> {
                    if (plugin.editorModule() != null) {
                        String prefix = args[1].toLowerCase(Locale.ROOT);
                        for (String id : plugin.editorModule().knownIds()) {
                            if (id.startsWith(prefix)) {
                                result.add(id);
                            }
                        }
                    }
                }
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
                case "pity" -> {
                    String pityAction = args[1].toLowerCase(Locale.ROOT);
                    if ("set".equals(pityAction)) {
                        for (String scope : List.of("player", "item")) {
                            if (scope.startsWith(args[2].toLowerCase(Locale.ROOT))) {
                                result.add(scope);
                            }
                        }
                    } else if ("list".equals(pityAction) || "clear".equals(pityAction)) {
                        completePityGroups(result, args[2]);
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
        if (args.length == 4 && "pity".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            completePityGroups(result, args[3]);
        }
        if (args.length == 6 && "pity".equalsIgnoreCase(args[0]) && "set".equalsIgnoreCase(args[1])) {
            result.addAll(CommandTabHelper.completeIntRange(args[5], 1, 100));
        }
        return result;
    }

    private void completePityGroups(List<String> result, String rawPrefix) {
        EmakiResult<List<EnhancementPityStateView>> states = pityOperations().pityStates("");
        if (!states.isSuccess()) {
            return;
        }
        String prefix = rawPrefix.toLowerCase(Locale.ROOT);
        states.orElse(List.of()).stream()
                .map(EnhancementPityStateView::group)
                .distinct()
                .filter(group -> group != null && group.toLowerCase(Locale.ROOT).startsWith(prefix))
                .forEach(result::add);
    }

    private boolean handleEdit(CommandSender sender, String[] args) {
        EditorCommandSupport.Guard editGuard = EditorCommandSupport.check(
                sender, () -> plugin.editorService() != null && plugin.editorModule() != null, PERMISSION_EDIT);
        if (editGuard == Guard.DENIED) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (editGuard == Guard.NON_PLAYER) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (editGuard == Guard.UNAVAILABLE) {
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 2) {
            plugin.editorService().openBrowser(player);
            return true;
        }
        if (plugin.editorModule().openById(player, args[1]) == null) {
            plugin.messageService().send(sender, "editor.open_failed",
                    Map.of("id", Texts.normalizeId(args[1])));
        }
        return true;
    }

    private boolean handleAffix(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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

    private boolean handleOpen(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.messageService().send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission(PERMISSION_USE) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        if (!plugin.strengthenGuiService().open(player)) {
            plugin.messageService().send(sender, "gui.open_failed");
        }
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD) && !sender.hasPermission(PERMISSION_ADMIN)) {
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().sendRaw(sender, plugin.messageService().message("general.reload_elapsed", Map.of("elapsed", elapsedMs)));
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
                player,
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        StrengthenAttemptService.BranchSelection selection =
                plugin.attemptService().selectBranch(player, held, args.length >= 2 ? args[1] : "");
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        ItemStack rebuilt = plugin.attemptService().applyAdminState(player,
                player.getInventory().getItemInMainHand(), null, 0, null);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
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
            plugin.messageService().send(sender, CommandKeys.NO_PERMISSION);
            return true;
        }
        return plugin.debugCommand().handle(sender, Arrays.copyOfRange(args, 1, args.length), plugin.messageService());
    }

    private ItemStack createMaterialItem(String materialToken, int amount) {
        return plugin.coreItemFactory().create(ItemSourceUtil.parse(materialToken), Math.max(1, amount));
    }

    private void sendHelp(CommandSender sender, String label) {
        sendHelpPage(sender, helpRoot(label), List.of());
    }

    private String helpRoot(String label) {
        return "/" + (label == null || label.isBlank() ? "emakistrengthen" : label);
    }

    private void sendHelpPage(CommandSender sender, String root, List<String> path) {
        if (path.isEmpty()) {
            sendHelpLines(sender, root, "command.help_header",
                    "command.help_entry_help", "command.help_entry_open", "command.help_entry_affix",
                    "command.help_entry_edit", "command.help_entry_reload", "command.help_entry_inspect",
                    "command.help_entry_refresh", "command.help_entry_setstar", "command.help_entry_branch",
                    "command.help_entry_clearstate", "command.help_entry_clearcrack",
                    "command.help_entry_givecatalyst", "command.help_entry_operation",
                    "command.help_entry_pity", "command.help_entry_debug");
            sendHelpFooter(sender);
            return;
        }
        switch (path.get(0).toLowerCase(Locale.ROOT)) {
            case "setstar" -> sendSetStarHelp(sender, root);
            case "givecatalyst" -> sendGiveCatalystHelp(sender, root);
            case "pity" -> sendPityHelp(sender, root, path);
            case "debug" -> sendDebugHelp(sender, root, path);
            default -> sendHelpPage(sender, root, List.of());
        }
    }

    private void sendSetStarHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_setstar_header", Map.of("root", root)));
        int maxStar = plugin.recipeLoader().all().values().stream()
                .mapToInt(recipe -> recipe == null ? 0 : recipe.limits().maxStar())
                .max()
                .orElse(12);
        for (int star = 0; star <= maxStar; star++) {
            String value = Integer.toString(star);
            sendValueEntry(sender, value,
                    messageService.message("command.help_value_desc_setstar", Map.of("value", value)),
                    root + " setstar " + value);
        }
        sendHelpFooter(sender);
    }

    private void sendGiveCatalystHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_givecatalyst_header", Map.of("root", root)));
        List<String> values = new ArrayList<>(plugin.recipeLoader().materialCatalog().keySet());
        values.sort(String::compareTo);
        if (values.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String value : values) {
            sendValueEntry(sender, value,
                    messageService.message("command.help_value_desc_givecatalyst", Map.of("value", value)),
                    root + " givecatalyst " + value);
        }
        sendHelpFooter(sender);
    }

    private void sendPityHelp(CommandSender sender, String root, List<String> path) {
        if (path.size() >= 2 && "set".equalsIgnoreCase(path.get(1))) {
            sendHelpLines(sender, root, "command.help_pity_set_header",
                    "command.help_pity_set_entry_scope", "command.help_pity_set_entry_group",
                    "command.help_pity_set_entry_key", "command.help_pity_set_entry_counter");
            sendHelpFooter(sender);
            return;
        }
        if (path.size() >= 2 && "clear".equalsIgnoreCase(path.get(1))) {
            sendPityClearHelp(sender, root);
            return;
        }
        sendHelpLines(sender, root, "command.help_pity_header",
                "command.help_pity_entry_list", "command.help_pity_entry_set",
                "command.help_pity_entry_clear", "command.help_pity_entry_diagnose");
        sendHelpFooter(sender);
    }

    private void sendPityClearHelp(CommandSender sender, String root) {
        var messageService = plugin.messageService();
        sendHelpLine(sender, messageService.message("command.help_pity_clear_header", Map.of("root", root)));
        List<String> groups = pityGroupIds();
        if (groups.isEmpty()) {
            sendHelpLine(sender, messageService.message("command.help_value_empty"));
            sendHelpFooter(sender);
            return;
        }
        for (String group : groups) {
            sendValueEntry(sender, group,
                    messageService.message("command.help_value_desc_pity_clear", Map.of("value", group)),
                    root + " pity clear " + group);
        }
        sendHelpFooter(sender);
    }

    private List<String> pityGroupIds() {
        EmakiResult<List<EnhancementPityStateView>> states = pityOperations().pityStates("");
        if (!states.isSuccess()) {
            return List.of();
        }
        List<String> groups = new ArrayList<>();
        for (EnhancementPityStateView view : states.orElse(List.of())) {
            String group = view.group();
            if (group != null && !groups.contains(group)) {
                groups.add(group);
            }
        }
        groups.sort(String::compareTo);
        return groups;
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

    private void sendPlayerHelp(CommandSender sender, String root, String command, String headerKey, String descKey) {
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
}
