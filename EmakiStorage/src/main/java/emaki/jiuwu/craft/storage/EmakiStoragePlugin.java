package emaki.jiuwu.craft.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.capability.ApiCapability;
import emaki.jiuwu.craft.corelib.api.capability.CapabilityRegistration;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.storage.action.StorageStageRegistrar;
import emaki.jiuwu.craft.storage.api.EmakiStorageApi;
import emaki.jiuwu.craft.storage.api.model.StorageSnapshot;
import emaki.jiuwu.craft.storage.apiimpl.DefaultStorageApi;
import emaki.jiuwu.craft.storage.command.StorageCommandRouter;
import emaki.jiuwu.craft.storage.config.AppConfig;
import emaki.jiuwu.craft.storage.config.StorageConfigPrecheckContributor;
import emaki.jiuwu.craft.storage.gui.StorageAmountFormatter;
import emaki.jiuwu.craft.storage.gui.StorageGuiService;
import emaki.jiuwu.craft.storage.gui.StorageLayoutResolver;
import emaki.jiuwu.craft.storage.listener.StoragePlayerListener;
import emaki.jiuwu.craft.storage.log.StorageOperationLog;
import emaki.jiuwu.craft.storage.placeholder.StoragePlaceholderExpansion;
import emaki.jiuwu.craft.storage.service.PlayerStorageStore;
import emaki.jiuwu.craft.storage.service.StorageCapacityService;
import emaki.jiuwu.craft.storage.service.StorageOverflowService;
import emaki.jiuwu.craft.storage.service.StorageSearchService;
import emaki.jiuwu.craft.storage.service.StorageSortService;
import emaki.jiuwu.craft.storage.service.StorageTextIndexer;
import emaki.jiuwu.craft.storage.service.StorageTransactionService;
import emaki.jiuwu.craft.storage.service.StorageUnlockService;
import emaki.jiuwu.craft.storage.session.StorageSessionManager;

/**
 * EmakiStorage runtime entry point.
 *
 * <p>Owns enable, reload, disable and final cleanup ordering; domain logic lives in the services
 * assembled by {@link StorageLifecycleCoordinator}.
 */
public class EmakiStoragePlugin extends AbstractConfigurableEmakiPlugin<AppConfig>
        implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "emakistorage";
    private static final Set<String> DEBUG_MODULES = Set.of("storage", "chat_input");
    private static final long TICKS_PER_SECOND = 20L;

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\__  _\\/\\  __ \\/\\  == \\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\/_/\\ \\/\\ \\ \\/\\ \\ \\  __<\\ \\  __\\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\ \\_\\ \\ \\_____\\ \\_\\ \\_\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/  \\/_/  \\/_____/\\/_/ /_/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0x4DA6FF;
    private static final int STARTUP_ASCII_END_COLOR = 0xFFD166;

    private final StorageLifecycleCoordinator lifecycleCoordinator = new StorageLifecycleCoordinator();
    private final StorageCommandRouter commandRouter = new StorageCommandRouter(this);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private emaki.jiuwu.craft.corelib.bootstrap.BootstrapService bootstrapService;
    private ExecutionDispatcher executionDispatcher;
    private ThreadOwnership threadOwnership;
    private GuiService guiService;
    private GuiTemplateLoader guiTemplateLoader;
    private ChatInputService chatInputService;
    private StorageTextIndexer textIndexer;
    private PlayerStorageStore dataStore;
    private StorageOperationLog operationLog;
    private StorageCapacityService capacityService;
    private StorageTransactionService transactionService;
    private StorageSearchService searchService;
    private StorageSortService sortService;
    private StorageOverflowService overflowService;
    private StorageUnlockService unlockService;
    private StorageLayoutResolver layoutResolver;
    private StorageAmountFormatter amountFormatter;
    private StorageGuiService storageGuiService;

    private StorageSessionManager sessionManager;
    private StoragePlayerListener playerListener;
    private emaki.jiuwu.craft.storage.service.StorageAutoPickupService autoPickupService;
    private StorageStageRegistrar stageRegistrar;
    private StoragePlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;
    private BStatsRegistration metrics;
    private TaskHandle autosaveTask;
    private volatile StorageLayoutResolver.Layout layout;

    private final EmakiStorageApi.Bridge apiBridge = new DefaultStorageApi(this);
    private CapabilityRegistration capabilityRegistration;

    public EmakiStoragePlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR, STARTUP_ASCII_END_COLOR);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        registerActions();
        // Registered before the first reload because that reload is now gated on this contributor.
        registerConfigPrecheckContributor();
        reloadPluginState();
        registerCommandHandler();
        registerEventHandlers();
        registerPlaceholders();
        scheduleAutosave();
        EmakiStorageApi.install(apiBridge);
        publishCapabilities();
        messageService.info("console.plugin_started");
    }

    /**
     * Advertises the optional operations this build really supports.
     *
     * <p>Published after the API bridge is installed and revoked before it is uninstalled, so a
     * consumer that sees the capability can always reach a live bridge behind it. The three keys map
     * one-to-one onto methods, and none of them is behind a config switch today &mdash; if one ever
     * is, it must stop being published rather than start returning {@code REJECTED}, or the consumer's
     * gate stops meaning anything.
     */
    private void publishCapabilities() {
        capabilityRegistration = EmakiCoreLibApi.publishCapabilities(this, Set.of(
                ApiCapability.of("emakistorage:atomic_batch"),
                ApiCapability.of("emakistorage:batch_count"),
                ApiCapability.of("emakistorage:reservation")));
        if (!capabilityRegistration.successful()) {
            getLogger().warning("[storage] Capability publication was refused: "
                    + capabilityRegistration.reasonKey());
        }
    }

    /**
     * Tears down in the reverse order of enable.
     *
     * <p>Ordering matters: task producers stop first, then registrations are removed, then the data
     * cache is sealed and flushed, and only then is the file lane drained. Draining before the flush
     * would discard writes that had not been queued yet.
     */
    @Override
    public void onDisable() {
        ConfigPrecheckLifecycleSupport.unregister("storage");
        if (capabilityRegistration != null) {
            capabilityRegistration.close();
            capabilityRegistration = null;
        }
        EmakiStorageApi.uninstall(apiBridge);
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        if (guiService != null) {
            guiService.closeAll();
        }
        if (chatInputService != null) {
            chatInputService.close();
        }
        if (autoPickupService != null) {
            autoPickupService.stop();
            autoPickupService = null;
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
            placeholderExpansion = null;
        }
        if (operationLog != null) {
            operationLog.flushAll();
        }
        if (dataStore != null) {
            long timeout = appConfig().persistence().drainTimeoutSeconds();
            PlayerStorageStore.FlushResult result = dataStore.flushAndSeal(timeout, TimeUnit.SECONDS);
            if (!result.clean()) {
                getLogger().warning("[storage] Shutdown flush was not clean: saved="
                        + result.savedEntries() + " failed=" + result.failedEntries()
                        + " remainingDirty=" + result.remainingDirtyEntries()
                        + " drained=" + (result.drainResult() != null && result.drainResult().drained()));
            }
        }
        lifecycleCoordinator.shutdown(this);
    }

    /** Reloads configuration, language, cost tiers and the GUI template. */
    public int reloadPluginState() {
        return lifecycleCoordinator.reload(this);
    }

    private void registerConfigPrecheckContributor() {
        ConfigPrecheckLifecycleSupport.register(new StorageConfigPrecheckContributor(this));
    }

    private void applyRuntimeComponents(StorageRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        executionDispatcher = components.executionDispatcher();
        threadOwnership = components.threadOwnership();
        guiService = components.guiService();
        guiTemplateLoader = components.guiTemplateLoader();
        chatInputService = components.chatInputService();
        textIndexer = components.textIndexer();
        dataStore = components.dataStore();
        operationLog = components.operationLog();
        capacityService = components.capacityService();
        transactionService = components.transactionService();
        searchService = components.searchService();
        sortService = components.sortService();
        overflowService = components.overflowService();
        unlockService = components.unlockService();
        layoutResolver = components.layoutResolver();
        amountFormatter = components.amountFormatter();
        storageGuiService = components.storageGuiService();

        sessionManager = new StorageSessionManager(this);
        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerActions() {
        stageRegistrar = new StorageStageRegistrar(this);
        stageRegistrar.register();
    }

    /** {@return the pipeline stage registrar, or {@code null} before actions are registered} */
    public StorageStageRegistrar stageRegistrar() {
        return stageRegistrar;
    }

    private void registerCommandHandler() {
        registerCommand(ROOT_COMMAND, "emakistorage command", List.of("estorage"),
                new PaperCommandAdapter(ROOT_COMMAND, "emakistorage.use", commandRouter, commandRouter));
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(chatInputService, this);
        playerListener = new StoragePlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        autoPickupService = new emaki.jiuwu.craft.storage.service.StorageAutoPickupService(this);
        autoPickupService.configure();
        getServer().getPluginManager().registerEvents(
                new emaki.jiuwu.craft.storage.listener.StorageAutoPickupListener(this), this);
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            placeholderExpansion = new StoragePlaceholderExpansion(this);
            if (!placeholderExpansion.register()) {
                getLogger().warning("[storage] PlaceholderAPI expansion registration was refused.");
                placeholderExpansion = null;
            }
        } catch (RuntimeException | LinkageError failure) {
            getLogger().warning("[storage] PlaceholderAPI expansion unavailable: "
                    + failure.getClass().getSimpleName());
            placeholderExpansion = null;
        }
    }

    /**
     * Schedules periodic autosave.
     *
     * <p>Amount changes only mark the storage dirty; the actual write happens here, on GUI close, on
     * quit and on disable. Many changes inside one window therefore collapse into a single write.
     */
    private void scheduleAutosave() {
        long interval = appConfig().persistence().autosaveIntervalSeconds();
        if (interval <= 0L) {
            return;
        }
        long ticks = interval * TICKS_PER_SECOND;
        autosaveTask = executionDispatcher.runGlobalTimer(this, () -> {
            if (dataStore != null) {
                dataStore.saveAllAsync();
            }
        }, ticks, ticks);
    }

    /** Records the resolved layout so command and session code can consult it. */
    void applyLayout(StorageLayoutResolver.Layout layout) {
        this.layout = layout;
    }

    /**
     * {@return the resolved storage layout}
     *
     * @deprecated 仓库内零调用（同名字段仍在内部使用，勿据此误删字段）。
     *         布局由 {@code StorageLayoutResolver} 在配置加载期解析，
     *         外部无需从插件主类取用。保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
     */
    @Deprecated(since = "1.0.11", forRemoval = true)
    public StorageLayoutResolver.Layout layout() {
        return layout;
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public emaki.jiuwu.craft.corelib.bootstrap.BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public ExecutionDispatcher executionDispatcher() {
        return executionDispatcher;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    public GuiService guiService() {
        return guiService;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    public ChatInputService chatInputService() {
        return chatInputService;
    }

    public StorageTextIndexer textIndexer() {
        return textIndexer;
    }

    /** {@return 自动拾取服务，未启用时仍返回实例但不会转入任何物品} */
    public emaki.jiuwu.craft.storage.service.StorageAutoPickupService autoPickupService() {
        return autoPickupService;
    }

    public PlayerStorageStore dataStore() {
        return dataStore;
    }

    public StorageOperationLog operationLog() {
        return operationLog;
    }

    public StorageCapacityService capacityService() {
        return capacityService;
    }

    public StorageTransactionService transactionService() {
        return transactionService;
    }

    /**
     * {@return the storage search service}
     *
     * @deprecated 仓库内零调用。仓库搜索由 GUI 路径内部调用，
     *         外部无需从插件主类取用。保留一个完整次版本周期后移除；移除前需再做源码/二进制使用面核对。
     */
    @Deprecated(since = "1.0.11", forRemoval = true)
    public StorageSearchService searchService() {
        return searchService;
    }

    public StorageSortService sortService() {
        return sortService;
    }

    public StorageOverflowService overflowService() {
        return overflowService;
    }

    public StorageUnlockService unlockService() {
        return unlockService;
    }

    public StorageLayoutResolver layoutResolver() {
        return layoutResolver;
    }

    public StorageAmountFormatter amountFormatter() {
        return amountFormatter;
    }

    public StorageGuiService storageGuiService() {
        return storageGuiService;
    }

    public StorageSessionManager sessionManager() {
        return sessionManager;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    /** {@return whether the current thread owns the target player's region} */
    public boolean ownsWriteTarget(Player target) {
        return target != null
                && target.isOnline()
                && threadOwnership != null
                && threadOwnership.isEntityOwned(target);
    }

    /**
     * Runs an operation on the target player's owning thread.
     *
     * <p>Cross-player admin operations are dispatched to the <em>target</em> player's owner, not the
     * caller's, because the entry table is only safe to touch alongside that player's inventory.
     *
     * @param target    the player whose owner thread must run the work
     * @param operation the work to run
     * @param onReject  the value produced when the work cannot be scheduled
     * @return a future completing with the result
     */
    public <R> CompletableFuture<R> runOwnerWriteAsync(Player target,
            Supplier<R> operation,
            Supplier<R> onReject) {
        if (target == null || !target.isOnline()) {
            return CompletableFuture.completedFuture(onReject.get());
        }
        CompletableFuture<R> future = new CompletableFuture<>();
        try {
            TaskHandle scheduled = executionDispatcher.runEntity(this, target, () -> {
                try {
                    future.complete(operation.get());
                } catch (Throwable failure) {
                    future.completeExceptionally(failure);
                }
            }, () -> future.complete(onReject.get()));
            if (scheduled == null) {
                future.complete(onReject.get());
            }
        } catch (Throwable failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    /** {@return the online player for a uuid, or {@code null}} */
    public Player onlinePlayer(UUID playerId) {
        return playerId == null ? null : Bukkit.getPlayer(playerId);
    }

    /** {@return a read-only snapshot result, loading the player's data when necessary} */
    public CompletableFuture<EmakiResult<StorageSnapshot>> getApiBridgeSnapshotAsync(UUID playerId) {
        return apiBridge.operations().readSnapshotAsync(playerId);
    }

    /**
     * Writes a human-readable YAML dump of a player's storage.
     *
     * @param playerId   the storage owner
     * @param playerName the name recorded in the dump
     * @return a future completing with the written file name, or {@code null} on failure
     */
    public CompletableFuture<String> exportStorageAsync(UUID playerId, String playerName) {
        return apiBridge.operations().readSnapshotAsync(playerId).thenApply(result -> {
            StorageSnapshot snapshot = result.orElse(null);
            if (snapshot == null) {
                return null;
            }
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("player_uuid", playerId.toString());
            dump.put("player_name", playerName);
            dump.put("exported_at", java.time.LocalDateTime.now().toString());
            dump.put("used_slots", snapshot.capacity().usedSlots());
            dump.put("total_slots", snapshot.capacity().effectiveSlots());
            dump.put("base_slots", snapshot.capacity().baseSlots());
            dump.put("permission_slots", snapshot.capacity().permissionSlots());
            dump.put("granted_slots", snapshot.capacity().grantedSlots());
            dump.put("purchased_slots", snapshot.capacity().purchasedSlots());
            dump.put("default_stack_limit", snapshot.defaultStackLimit());
            dump.put("sort_mode", snapshot.sortMode());
            List<Map<String, Object>> entries = new ArrayList<>();
            for (var entry : snapshot.entries()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("slot", entry.slotIndex());
                row.put("material", entry.template().getType().getKey().toString());
                row.put("display_name", textIndexer.displayName(entry.template()));
                row.put("amount", entry.amount());
                row.put("stack_limit", entry.stackLimit());
                entries.add(row);
            }
            dump.put("entries", entries);

            String fileName = "export-" + playerId + ".yml";
            java.nio.file.Path target = dataPath("exports", fileName);
            try {
                java.nio.file.Files.createDirectories(target.getParent());
                emaki.jiuwu.craft.corelib.yaml.YamlFiles.save(target.toFile(), dump);
                return "exports/" + fileName;
            } catch (java.io.IOException failure) {
                getLogger().warning("[storage] Failed to export storage for " + playerId
                        + ": " + failure.getMessage());
                return null;
            }
        });
    }

    private static final class PaperCommandAdapter implements BasicCommand {

        private final String rootLabel;
        private final String permission;
        private final org.bukkit.command.CommandExecutor executor;
        private final org.bukkit.command.TabCompleter tabCompleter;

        private PaperCommandAdapter(String rootLabel,
                String permission,
                org.bukkit.command.CommandExecutor executor,
                org.bukkit.command.TabCompleter tabCompleter) {
            this.rootLabel = rootLabel;
            this.permission = permission;
            this.executor = executor;
            this.tabCompleter = tabCompleter;
        }

        @Override
        public void execute(CommandSourceStack source, String[] args) {
            executor.onCommand(source.getSender(), null, rootLabel, args);
        }

        @Override
        public java.util.Collection<String> suggest(CommandSourceStack source, String[] args) {
            String[] completionArgs = args.length == 0 ? new String[] { "" } : args;
            List<String> suggestions = tabCompleter.onTabComplete(source.getSender(), null,
                    rootLabel, completionArgs);
            return suggestions == null ? List.of() : suggestions;
        }

        @Override
        public String permission() {
            return permission;
        }
    }
}
