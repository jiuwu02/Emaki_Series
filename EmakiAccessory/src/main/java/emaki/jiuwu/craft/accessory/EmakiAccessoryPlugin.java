package emaki.jiuwu.craft.accessory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.accessory.api.EmakiAccessoryApi;
import emaki.jiuwu.craft.accessory.apiimpl.ServiceBackedAccessoryBridge;
import emaki.jiuwu.craft.accessory.command.AccessoryCommandRouter;
import emaki.jiuwu.craft.accessory.config.AccessoryConfigPrecheckContributor;
import emaki.jiuwu.craft.accessory.config.AppConfig;
import emaki.jiuwu.craft.accessory.gui.AccessoryGuiHandler;
import emaki.jiuwu.craft.accessory.gui.AccessoryGuiService;
import emaki.jiuwu.craft.accessory.listener.AccessoryPlayerListener;
import emaki.jiuwu.craft.accessory.loader.AccessoryPartLoader;
import emaki.jiuwu.craft.accessory.loader.AccessorySetLoader;
import emaki.jiuwu.craft.accessory.model.AccessoryContributionSnapshot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.service.AccessoryAdminService;
import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.accessory.service.AccessoryPartRegistry;
import emaki.jiuwu.craft.accessory.service.AccessorySetService;
import emaki.jiuwu.craft.accessory.service.AccessoryUniqueService;
import emaki.jiuwu.craft.accessory.service.AccessoryWriteSessionRegistry;
import emaki.jiuwu.craft.accessory.service.PlayerAccessoryStore;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

public final class EmakiAccessoryPlugin extends AbstractConfigurableEmakiPlugin<AppConfig>
        implements LogMessagesProvider, AccessoryGuiHandler.Callbacks {

    private static final String MODULE = "accessory";
    private static final String ROOT_COMMAND = "emakiaccessory";
    private static final Set<String> DEBUG_MODULES = Set.of(MODULE);

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  ______  ______  ______  ______  __  __    \s
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  __ \\/\\  ___\\/\\  ___\\/\\  ___\\/\\  ___\\/\\  ___\\/\\  __ \\/\\  == \\/\\ \\_\\ \\  \s
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __ \\ \\ \\___\\ \\ \\___\\ \\  __\\\\ \\___  \\ \\___  \\ \\ \\/\\ \\ \\  __<\\ \\____ \\ \s
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\_\\ \\_____\\ \\_____\\ \\_____\\/\\_____\\/\\_____\\ \\_____\\ \\_\\ \\_\\/\\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/\\/_/\\/_____/\\/_____/\\/_____/\\/_____/\\/_____/\\/_____/\\/_/ /_/\\/_____/
""";
    private static final int STARTUP_ASCII_START_COLOR = 0xF472B6;
    private static final int STARTUP_ASCII_END_COLOR = 0xC084FC;
    private static final int BSTATS_PLUGIN_ID = 33428;

    private final AccessoryLifecycleCoordinator lifecycleCoordinator = new AccessoryLifecycleCoordinator();
    private final AtomicReference<AccessoryPartRegistry> partRegistry =
            new AtomicReference<>(AccessoryPartRegistry.empty());
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean apiInstalled = new AtomicBoolean();

    private AccessoryRuntimeComponents components;
    private AccessoryCommandRouter commandRouter;
    private AccessoryPlayerListener playerListener;
    private ServiceBackedAccessoryBridge apiBridge;
    private DebugCommand debugCommand;
    private TaskToken autoSaveTask;
    private boolean runtimeInitialized;

    private volatile boolean contentReady;
    private BStatsRegistration metrics;

    public EmakiAccessoryPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return components == null ? null : components.appConfigLoader();
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII, STARTUP_ASCII_START_COLOR, STARTUP_ASCII_END_COLOR);
        shutdownStarted.set(false);
        components = lifecycleCoordinator.initialize(this);
        runtimeInitialized = true;
        setDebugLogger(new DebugLogger(this, components.languageLoader()));
        debugLogger().setFallbackLoader(coreLib().languageLoader());
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES, getName());
        registerServices(components);
        ConfigPrecheckLifecycleSupport.register(new AccessoryConfigPrecheckContributor(this));
        components.messageService().info("console.plugin_starting");
        components.bootstrapService().bootstrap();
        reloadContent();
        registerCommandHandler();
        registerEventHandlers();
        components.providerRegistrar().register();
        installPublicApi();
        scheduleAutoSave();
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        components.messageService().info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }

        contentReady = false;
        publishAbsent();
        if (!runtimeInitialized || components == null) {
            return;
        }
        uninstallPublicApi();
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        ConfigPrecheckLifecycleSupport.unregister(MODULE);
        HandlerList.unregisterAll(this);

        components.providerRegistrar().unregister();

        components.guiService().closeAll();
        components.writeSessions().clear();
        PlayerAccessoryStore.FlushResult result = components.accessoryStore()
                .flushAndSeal(appConfig().drainTimeoutSeconds(), TimeUnit.SECONDS);
        if (!result.clean()) {
            getLogger().warning("Accessory flush did not finish cleanly: saved=" + result.savedEntries()
                    + " failed=" + result.failedEntries()
                    + " remainingDirty=" + result.remainingDirtyEntries());
        }
        partRegistry.set(AccessoryPartRegistry.empty());
        runtimeInitialized = false;
        components.messageService().info("console.plugin_stopped");
    }

    public int reloadContent() {
        contentReady = false;
        publishLoading();
        int result = lifecycleCoordinator.reload(this);
        ConfigCommitGate.evaluate(components.messageService(), MODULE);
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
            getLogger().fine("EmakiAccessory readiness publication skipped: " + exception);
        }
    }

    public AccessoryPartRegistry partRegistry() {
        return partRegistry.get();
    }

    void partRegistry(AccessoryPartRegistry registry) {
        partRegistry.set(registry == null ? AccessoryPartRegistry.empty() : registry);
    }

    @Override
    public MessageService messageService() {
        return components == null ? null : components.messageService();
    }

    public LanguageLoader languageLoader() {
        return components == null ? null : components.languageLoader();
    }

    public ExecutionDispatcher executionDispatcher() {
        return components == null ? null : components.executionDispatcher();
    }

    public GuiService guiService() {
        return components == null ? null : components.guiService();
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return components == null ? null : components.guiTemplateLoader();
    }

    public AccessoryPartLoader partLoader() {
        return components == null ? null : components.partLoader();
    }

    public AccessorySetLoader setLoader() {
        return components == null ? null : components.setLoader();
    }

    public AccessoryUniqueService uniqueService() {
        return components == null ? null : components.uniqueService();
    }

    public PlayerAccessoryStore accessoryStore() {
        return components == null ? null : components.accessoryStore();
    }

    public AccessorySetService setService() {
        return components == null ? null : components.setService();
    }

    public AccessoryContributionService contributionService() {
        return components == null ? null : components.contributionService();
    }

    public AccessoryGuiService accessoryGuiService() {
        return components == null ? null : components.accessoryGuiService();
    }

    public AccessoryWriteSessionRegistry writeSessions() {
        return components == null ? null : components.writeSessions();
    }

    public AccessoryAdminService adminService() {
        return components == null ? null : components.adminService();
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public boolean isShutdownStarted() {
        return shutdownStarted.get();
    }

    public boolean openOwn(Player player) {
        if (player == null || components == null) {
            return false;
        }
        PlayerAccessories accessories = components.accessoryStore().cached(player.getUniqueId());
        if (accessories == null) {
            components.messageService().send(player, "general.data_loading");
            return false;
        }
        return open(player, accessories);
    }

    public boolean open(Player viewer, PlayerAccessories accessories) {
        if (viewer == null || accessories == null || components == null) {
            return false;
        }
        AccessoryGuiHandler handler = new AccessoryGuiHandler(
                this, components.accessoryGuiService(), components.uniqueService(), accessories);

        components.writeSessions().acquire(accessories.playerId(), viewer.getUniqueId());
        return components.accessoryGuiService().open(viewer, handler) != null;
    }

    public void refreshContributions(PlayerAccessories accessories) {
        if (accessories == null || components == null) {
            return;
        }
        AccessoryContributionSnapshot snapshot = components.contributionService().recompute(accessories);
        Player target = getServer().getPlayer(accessories.playerId());
        if (target != null) {
            components.setService().publishChanges(target, snapshot.setPieceCount());
        }
    }

    @Override
    public Plugin plugin() {
        return this;
    }

    @Override
    public boolean canWrite(Player viewer, PlayerAccessories accessories) {
        if (viewer == null || accessories == null || components == null) {
            return false;
        }
        boolean ownWindow = viewer.getUniqueId().equals(accessories.playerId());
        if (!ownWindow && !viewer.hasPermission(AccessoryCommandRouter.PERMISSION_EDIT_OTHERS)) {
            return false;
        }

        return components.writeSessions().holdsLease(accessories.playerId(), viewer.getUniqueId());
    }

    @Override
    public void onContentsChanged(Player viewer, PlayerAccessories accessories) {
        if (components == null || accessories == null) {
            return;
        }
        refreshContributions(accessories);
        components.accessoryStore().saveAsync(accessories.playerId());
    }

    @Override
    public void onWindowClosed(Player viewer, PlayerAccessories accessories) {
        if (components == null || accessories == null) {
            return;
        }
        if (viewer != null) {
            components.writeSessions().release(accessories.playerId(), viewer.getUniqueId());
        }
        components.accessoryStore().saveAsync(accessories.playerId());
    }

    @Override
    public void reject(Player viewer, String messageKey, Map<String, ?> replacements) {
        if (viewer != null && components != null) {
            components.messageService().send(viewer, messageKey, replacements);
        }
    }

    private void registerCommandHandler() {
        commandRouter = new AccessoryCommandRouter(this);
        registerCommand(ROOT_COMMAND, "EmakiAccessory command", List.of("eaccessory", "eacc"),
                new AccessoryCommandAdapter(ROOT_COMMAND, "emakiaccessory.use", commandRouter));
    }

    private void registerEventHandlers() {
        getServer().getPluginManager().registerEvents(components.guiService(), this);
        playerListener = new AccessoryPlayerListener(this);
        getServer().getPluginManager().registerEvents(playerListener, this);
    }

    private void scheduleAutoSave() {
        int autosaveSeconds = appConfig().autosaveSeconds();
        if (autosaveSeconds <= 0) {
            return;
        }
        long intervalTicks = Math.max(100L, autosaveSeconds * 20L);
        autoSaveTask = components.executionDispatcher().runGlobalTimer(this,
                () -> components.accessoryStore().saveAllAsync(), intervalTicks, intervalTicks);
    }

    private void installPublicApi() {
        if (apiInstalled.compareAndSet(false, true)) {
            apiBridge = new ServiceBackedAccessoryBridge(this);
            EmakiAccessoryApi.install(apiBridge);
        }
    }

    private void uninstallPublicApi() {
        if (apiInstalled.compareAndSet(true, false)) {
            EmakiAccessoryApi.uninstall(apiBridge);
            apiBridge = null;
        }
    }

    CompletableFuture<Integer> saveAllAsync() {
        return components == null
                ? CompletableFuture.completedFuture(0)
                : components.accessoryStore().saveAllAsync();
    }
}
