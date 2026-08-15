package emaki.jiuwu.craft.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;

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
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
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
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.storage.listener.StorageAutoPickupListener;
import emaki.jiuwu.craft.storage.service.StorageAutoPickupService;

public class EmakiStoragePlugin extends AbstractConfigurableEmakiPlugin<AppConfig>
        implements LogMessagesProvider {

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
    private BootstrapService bootstrapService;
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
    private StorageSortService sortService;
    private StorageOverflowService overflowService;
    private StorageUnlockService unlockService;
    private StorageLayoutResolver layoutResolver;
    private StorageAmountFormatter amountFormatter;
    private StorageGuiService storageGuiService;

    private StorageSessionManager sessionManager;
    private StoragePlayerListener playerListener;
    private StorageAutoPickupService autoPickupService;
    private StorageStageRegistrar stageRegistrar;
    private StoragePlaceholderExpansion placeholderExpansion;
    private DebugCommand debugCommand;
    private BStatsRegistration metrics;
    private TaskToken autosaveTask;

    private volatile boolean contentReady;

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

    @Override
    public void onDisable() {
        contentReady = false;
        publishAbsent();
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

    public int reloadPluginState() {
        contentReady = false;
        publishLoading();
        int result = lifecycleCoordinator.reload(this);
        contentReady = true;
        publishReady();
        return result;
    }

    public boolean contentReady() {
        return contentReady;
    }

    private void publishReady() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleReady(getName()));
    }

    private void publishLoading() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleLoading(getName()));
    }

    private void publishAbsent() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleAbsent(getName()));
    }

    private void publishReadiness(Consumer<EmakiCoreLibPlugin> action) {
        try {
            action.accept(JavaPlugin.getPlugin(EmakiCoreLibPlugin.class));
        } catch (RuntimeException | LinkageError exception) {
            getLogger().fine("EmakiStorage readiness publication skipped: " + exception);
        }
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
        autoPickupService = new StorageAutoPickupService(this);
        autoPickupService.configure();
        getServer().getPluginManager().registerEvents(
                new StorageAutoPickupListener(this), this);
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

    public BootstrapService bootstrapService() {
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

    public StorageAutoPickupService autoPickupService() {
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

    public boolean ownsWriteTarget(Player target) {
        return target != null
                && target.isOnline()
                && threadOwnership != null
                && threadOwnership.isEntityOwned(target);
    }

    public <R> CompletableFuture<R> runOwnerWriteAsync(Player target,
            Supplier<R> operation,
            Supplier<R> onReject) {
        if (target == null || !target.isOnline()) {
            return CompletableFuture.completedFuture(onReject.get());
        }
        CompletableFuture<R> future = new CompletableFuture<>();
        try {
            TaskToken scheduled = executionDispatcher.runEntity(this, target, () -> {
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

    public Player onlinePlayer(UUID playerId) {
        return playerId == null ? null : Bukkit.getPlayer(playerId);
    }

    public CompletableFuture<EmakiResult<StorageSnapshot>> getApiBridgeSnapshotAsync(UUID playerId) {
        return apiBridge.operations().readSnapshotAsync(playerId);
    }

    public CompletableFuture<String> exportStorageAsync(UUID playerId, String playerName) {
        return apiBridge.operations().readSnapshotAsync(playerId).thenApply(result -> {
            StorageSnapshot snapshot = result.orElse(null);
            if (snapshot == null) {
                return null;
            }
            Map<String, Object> dump = new LinkedHashMap<>();
            dump.put("player_uuid", playerId.toString());
            dump.put("player_name", playerName);
            dump.put("exported_at", LocalDateTime.now().toString());
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
            Path target = dataPath("exports", fileName);
            try {
                Files.createDirectories(target.getParent());
                YamlFiles.save(target.toFile(), dump);
                return "exports/" + fileName;
            } catch (IOException failure) {
                getLogger().warning("[storage] Failed to export storage for " + playerId
                        + ": " + failure.getMessage());
                return null;
            }
        });
    }

    private static final class PaperCommandAdapter implements BasicCommand {

        private final String rootLabel;
        private final String permission;
        private final CommandExecutor executor;
        private final TabCompleter tabCompleter;

        private PaperCommandAdapter(String rootLabel,
                String permission,
                CommandExecutor executor,
                TabCompleter tabCompleter) {
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
        public Collection<String> suggest(CommandSourceStack source, String[] args) {
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
