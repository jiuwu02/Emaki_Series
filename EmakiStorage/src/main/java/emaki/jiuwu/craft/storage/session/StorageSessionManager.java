package emaki.jiuwu.craft.storage.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.api.chat.ChatInputRequest;
import emaki.jiuwu.craft.corelib.api.chat.ChatInputResult;
import emaki.jiuwu.craft.corelib.api.dialog.DialogDefinition;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.unlock.UnlockService;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.api.model.StorageCapacity;
import emaki.jiuwu.craft.storage.model.StorageResult;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.config.InputModeConfig;
import emaki.jiuwu.craft.storage.gui.StorageGuiHandler;
import emaki.jiuwu.craft.storage.gui.StorageGuiService;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.SearchQuery;
import emaki.jiuwu.craft.storage.model.SortMode;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.service.StorageUnlockService;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.dialog.DialogService;

public final class StorageSessionManager implements StorageGuiHandler.Callbacks {

    private final EmakiStoragePlugin plugin;

    public StorageSessionManager(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean openOwn(Player player) {
        if (player == null) {
            return false;
        }
        PlayerStorage storage = plugin.dataStore().cached(player.getUniqueId());
        if (storage == null) {
            plugin.messageService().send(player, "general.data_loading");
            return false;
        }
        return open(player, storage);
    }

    public CompletableFuture<Boolean> openForAdminAsync(Player admin, UUID target, String targetName) {
        if (admin == null || target == null) {
            return CompletableFuture.completedFuture(false);
        }
        PlayerStorage cached = plugin.dataStore().cached(target);
        if (cached != null) {
            return plugin.runOwnerWriteAsync(admin, () -> open(admin, cached), () -> false);
        }
        return plugin.dataStore().beginSessionAsync(target, targetName)
                .thenCompose(loaded -> {
                    if (loaded == null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return plugin.runOwnerWriteAsync(admin, () -> open(admin, loaded), () -> false);
                });
    }

    private boolean open(Player viewer, PlayerStorage storage) {
        StorageGuiHandler handler = new StorageGuiHandler(plugin, storage,
                plugin.storageGuiService(), plugin.transactionService(), plugin.capacityService(),
                plugin.messageService(), this, plugin.appConfig());
        GuiSession session = plugin.storageGuiService().open(viewer, storage, handler);
        return session != null;
    }

    @Override
    public void onWindowClosed(Player viewer, PlayerStorage storage) {
        if (storage == null) {
            return;
        }
        plugin.dataStore().saveAsync(storage.playerId());
    }

    @Override
    public void promptWithdrawAmount(Player viewer, GuiSession session, StorageKey key) {
        AppConfig config = plugin.appConfig();
        UUID storageOwner = ownerOf(session);
        long generation = plugin.dataStore().currentGeneration(storageOwner);
        if (promptWithdrawByDialog(viewer, session, key, storageOwner, generation)) {
            return;
        }
        if (!config.behavior().withdrawInput().allowsChat()) {
            plugin.messageService().send(viewer, "dialog.unavailable");
            return;
        }
        plugin.messageService().send(viewer, "chat.withdraw.prompt",
                Map.of("cancel", String.join(", ", config.search().cancelKeywords())));
        plugin.chatInputService().await(new ChatInputRequest(plugin, viewer,
                config.search().inputTimeoutSeconds(), config.search().cancelKeywords(),
                result -> handleWithdrawInput(viewer, session, key, storageOwner, generation, result)));
    }

    private boolean promptWithdrawByDialog(Player viewer,
            GuiSession session,
            StorageKey key,
            UUID storageOwner,
            long generation) {
        InputModeConfig input = plugin.appConfig().behavior().withdrawInput();
        DialogDefinition definition = resolveDialog(input, Map.of());
        if (definition == null) {
            return false;
        }
        return dialogService().show(viewer, definition, (player, submission) ->
                applyWithdrawInput(player, session, key, storageOwner, generation,
                        submission.text(input.inputKey())));
    }

    private void handleWithdrawInput(Player viewer,
            GuiSession session,
            StorageKey key,
            UUID storageOwner,
            long generation,
            ChatInputResult result) {
        if (!result.submitted()) {
            reportInputOutcome(viewer, result);
            return;
        }
        applyWithdrawInput(viewer, session, key, storageOwner, generation, result.text());
    }

    private void applyWithdrawInput(Player viewer,
            GuiSession session,
            StorageKey key,
            UUID storageOwner,
            long generation,
            String input) {
        PlayerStorage storage = plugin.dataStore().cached(storageOwner);
        if (storage == null || !plugin.dataStore().isCurrentGeneration(storageOwner, generation)) {
            plugin.messageService().send(viewer, "general.session_expired");
            return;
        }
        long amount = parseAmount(input, storage, key);
        if (amount <= 0L) {
            plugin.messageService().send(viewer, "chat.withdraw.invalid",
                    Map.of("input", input == null ? "" : input));
            return;
        }
        StorageResult withdrawn = plugin.transactionService().withdraw(storage, viewer, key, amount,
                StorageOperationSource.GUI);
        if (withdrawn.applied()) {
            plugin.messageService().send(viewer, "chat.withdraw.done",
                    Map.of("amount", withdrawn.appliedAmount()));
        } else {
            plugin.messageService().send(viewer, "gui.withdraw.failed");
        }
        refresh(viewer, session, storage);
    }

    private long parseAmount(String raw, PlayerStorage storage, StorageKey key) {
        if (raw == null || raw.isBlank()) {
            return -1L;
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if (text.equals("all") || text.equals("全部")) {
            var entry = storage.entry(key);
            return entry == null ? -1L : entry.amount();
        }
        return parseCompactAmount(text);
    }

    public static long parseCompactAmount(String text) {
        if (text == null || text.isBlank()) {
            return -1L;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("max")) {
            return Long.MAX_VALUE;
        }
        long multiplier = 1L;
        char suffix = normalized.charAt(normalized.length() - 1);
        int unitIndex = "kmbtpe".indexOf(suffix);
        if (unitIndex >= 0) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
            for (int step = 0; step <= unitIndex; step++) {
                multiplier *= 1000L;
            }
        }
        if (normalized.isEmpty()) {
            return -1L;
        }
        try {
            if (normalized.indexOf('.') >= 0) {
                double value = Double.parseDouble(normalized) * multiplier;
                if (!Double.isFinite(value) || value < 1.0D || value > (double) Long.MAX_VALUE) {
                    return -1L;
                }
                return (long) value;
            }
            long value = Long.parseLong(normalized);
            if (value <= 0L) {
                return -1L;
            }
            long scaled = value * multiplier;
            if (multiplier != 1L && scaled / multiplier != value) {
                return -1L;
            }
            return scaled;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    @Override
    public void promptSearch(Player viewer, GuiSession session) {
        AppConfig config = plugin.appConfig();
        UUID dialogOwner = ownerOf(session);
        long dialogGeneration = plugin.dataStore().currentGeneration(dialogOwner);
        if (promptSearchByDialog(viewer, session, dialogOwner, dialogGeneration)) {
            return;
        }
        if (!config.search().input().allowsChat()) {
            plugin.messageService().send(viewer, "dialog.unavailable");
            return;
        }
        plugin.messageService().send(viewer, "chat.search.prompt", Map.of(
                "name", config.search().operators().name(),
                "lore", config.search().operators().lore(),
                "id", config.search().operators().id(),
                "exclude", config.search().operators().exclude(),
                "cancel", String.join(", ", config.search().cancelKeywords())));
        UUID storageOwner = ownerOf(session);
        long generation = plugin.dataStore().currentGeneration(storageOwner);
        plugin.chatInputService().await(new ChatInputRequest(plugin, viewer,
                config.search().inputTimeoutSeconds(), config.search().cancelKeywords(),
                result -> handleSearchInput(viewer, session, storageOwner, generation, result)));
    }

    private boolean promptSearchByDialog(Player viewer,
            GuiSession session,
            UUID storageOwner,
            long generation) {
        InputModeConfig input = plugin.appConfig().search().input();
        var operators = plugin.appConfig().search().operators();
        DialogDefinition definition = resolveDialog(input, Map.of(
                "name", operators.name(),
                "lore", operators.lore(),
                "id", operators.id(),
                "exclude", operators.exclude()));
        if (definition == null) {
            return false;
        }
        return dialogService().show(viewer, definition, (player, submission) ->
                applySearchInput(player, session, storageOwner, generation,
                        submission.text(input.inputKey())));
    }

    private DialogDefinition resolveDialog(InputModeConfig input, Map<String, ?> replacements) {
        if (!input.allowsDialog() || !input.dialogUsable()) {
            return null;
        }
        var dialogService = dialogService();
        if (dialogService == null || !dialogService.enabled()) {
            return null;
        }
        DialogDefinition source = input.dialog();
        List<DialogDefinition.Body> body = new ArrayList<>();
        for (DialogDefinition.Body entry : source.body()) {
            body.add(new DialogDefinition.Body(
                    text(entry.text(), replacements), entry.item(), entry.width()));
        }
        List<DialogDefinition.Input> inputs = new ArrayList<>();
        for (DialogDefinition.Input entry : source.inputs()) {
            inputs.add(new DialogDefinition.Input(
                    entry.type(),
                    entry.key(),
                    text(entry.label(), replacements),
                    entry.labelVisible(),
                    entry.initial(),
                    entry.maxLength(),
                    entry.width(),
                    entry.start(),
                    entry.end(),
                    entry.step(),
                    entry.initialBoolean(),
                    entry.onTrue(),
                    entry.onFalse(),
                    entry.options()));
        }
        List<DialogDefinition.Button> buttons = new ArrayList<>();
        for (DialogDefinition.Button entry : source.buttons()) {
            buttons.add(button(entry, replacements));
        }
        return new DialogDefinition(
                source.id(),
                source.type(),
                text(source.title(), replacements),
                source.externalTitle() == null ? null : text(source.externalTitle(), replacements),
                source.canCloseWithEscape(),
                source.pause(),
                source.afterAction(),
                body,
                inputs,
                buttons,
                source.exitButton() == null ? null : button(source.exitButton(), replacements),
                source.columns());
    }

    private DialogDefinition.Button button(DialogDefinition.Button source,
            Map<String, ?> replacements) {
        return new DialogDefinition.Button(
                text(source.label(), replacements),
                source.tooltip() == null ? null : text(source.tooltip(), replacements),
                source.width(),
                source.action());
    }

    private String text(String raw, Map<String, ?> replacements) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String resolved = plugin.messageService().message(raw, replacements);
        return resolved == null ? raw : resolved;
    }

    private DialogService dialogService() {
        var coreLib = JavaPlugin
                .getPlugin(EmakiCoreLibPlugin.class);
        return coreLib == null ? null : coreLib.dialogService();
    }

    private void handleSearchInput(Player viewer,
            GuiSession session,
            UUID storageOwner,
            long generation,
            ChatInputResult result) {
        if (!result.submitted()) {
            reportInputOutcome(viewer, result);
            return;
        }
        applySearchInput(viewer, session, storageOwner, generation, result.text());
    }

    private void applySearchInput(Player viewer,
            GuiSession session,
            UUID storageOwner,
            long generation,
            String input) {
        String text = input == null ? "" : input;
        PlayerStorage storage = plugin.dataStore().cached(storageOwner);
        if (storage == null || !plugin.dataStore().isCurrentGeneration(storageOwner, generation)) {
            plugin.messageService().send(viewer, "general.session_expired");
            return;
        }
        SearchQuery query = SearchQuery.parse(text, plugin.appConfig().search().operators());
        StorageGuiService.ViewState state = plugin.storageGuiService().viewState(viewer.getUniqueId());
        applyQuery(viewer, session, storage, state, query, text);
        plugin.messageService().send(viewer, "chat.search.applied",
                Map.of("query", text, "count", state.visible().size()));
    }

    @Override
    public void clearSearch(Player viewer, GuiSession session) {
        PlayerStorage storage = plugin.dataStore().cached(ownerOf(session));
        if (storage == null) {
            return;
        }
        StorageGuiService.ViewState state = plugin.storageGuiService().viewState(viewer.getUniqueId());
        applyQuery(viewer, session, storage, state, SearchQuery.empty(), "");
        plugin.messageService().send(viewer, "chat.search.cleared");
    }

    private void applyQuery(Player viewer,
            GuiSession session,
            PlayerStorage storage,
            StorageGuiService.ViewState state,
            SearchQuery query,
            String queryText) {
        state.applyQuery(query, queryText);
        plugin.storageGuiService().refreshView(viewer, storage, state);
        plugin.storageGuiService().applyPage(session, 0);
    }

    @Override
    public void openUnlock(Player viewer, GuiSession session, PlayerStorage storage) {
        AppConfig config = plugin.appConfig();
        if (!config.unlock().purchaseEnabled()) {
            plugin.messageService().send(viewer, "unlock.disabled");
            return;
        }
        if (!viewer.hasPermission("emakistorage.unlock.purchase")) {
            plugin.messageService().send(viewer, "general.no_permission");
            return;
        }
        StorageCapacity capacity = plugin.capacityService()
                .capacityOf(storage, viewer, plugin.storageGuiService().slotsPerPage());
        int slots = plugin.unlockService().batchOptions().stream().findFirst().orElse(1);
        UnlockService.UnlockResult result = plugin.unlockService()
                .purchase(storage, viewer, capacity, slots, StorageOperationSource.GUI);
        if (result.success()) {
            plugin.messageService().send(viewer, "unlock.success", Map.of(
                    "slots", result.unlocked(),
                    "cost", result.quote().currencyTotal()));
        } else {
            plugin.messageService().send(viewer, unlockFailureKey(result.reasonKey()));
        }
        refresh(viewer, session, storage);
    }

    private String unlockFailureKey(String reasonKey) {
        if (reasonKey == null) {
            return "unlock.failed";
        }
        return switch (reasonKey) {
            case "purchase_disabled" -> "unlock.disabled";
            case "no_price_defined" -> "unlock.no_price";
            case "max_slots_reached" -> "unlock.at_ceiling";
            case "price_over_cap", "price_overflow" -> "unlock.price_invalid";
            case "insufficient_currency" -> "unlock.insufficient_currency";
            case "insufficient_items" -> "unlock.insufficient_items";
            case "unknown_item_source" -> "unlock.unknown_item";
            case "cancelled" -> "unlock.cancelled";
            default -> "unlock.failed";
        };
    }

    @Override
    public void cycleSort(Player viewer, GuiSession session, PlayerStorage storage, boolean reverse) {
        if (!plugin.appConfig().behavior().playerSortEnabled()) {
            plugin.messageService().send(viewer, "gui.sort.disabled");
            return;
        }
        SortMode next = reverse ? storage.sortMode().reversed() : storage.sortMode().nextDimension();
        storage.sortMode(next);
        plugin.sortService().sortNow(storage, next);
        storage.markDirty();
        plugin.messageService().send(viewer, "gui.sort.changed",
                Map.of("mode", plugin.messageService().message("gui.sort.modes." + next.id())));
        refresh(viewer, session, storage);
    }

    private void refresh(Player viewer, GuiSession session, PlayerStorage storage) {
        StorageGuiService.ViewState state = plugin.storageGuiService().viewState(viewer.getUniqueId());
        plugin.storageGuiService().refreshView(viewer, storage, state);
        plugin.storageGuiService().refresh(session);
    }

    private void reportInputOutcome(Player viewer, ChatInputResult result) {
        switch (result.status()) {
            case CANCELLED -> plugin.messageService().send(viewer, "chat.input.cancelled");
            case TIMEOUT -> plugin.messageService().send(viewer, "chat.input.timeout");
            case QUIT, SUBMITTED -> {
            }
        }
    }

    private UUID ownerOf(GuiSession session) {
        if (session != null && session.handler() instanceof StorageGuiHandler handler
                && handler.storage() != null) {
            return handler.storage().playerId();
        }
        return session == null || session.viewer() == null ? null : session.viewer().getUniqueId();
    }
}
