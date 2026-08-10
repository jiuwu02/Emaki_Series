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
import emaki.jiuwu.craft.corelib.execution.TaskHandle;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

/**
 * EmakiAccessory's entry point.
 *
 * <p>Orchestration only: the component graph is built by {@link AccessoryLifecycleCoordinator} and the
 * domain logic lives in the services it produces. This class additionally implements the GUI handler
 * callbacks, because deciding "may this viewer write" and "what happens after a change" needs the write
 * lease, the store and the contribution cache together, and the plugin is the one place that holds all
 * three.
 *
 * <p>Disable reverses enable: windows close first so items in flight are returned to the payload, then
 * providers are revoked, then contents are flushed and the file lane drained.
 */
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
    private TaskHandle autoSaveTask;
    private boolean runtimeInitialized;
    // "Data is loaded", not "components exist": components is non-null from initialize() onward, so a
    // null-check answered true while reloadContent() was still rebuilding parts, templates and sets.
    private volatile boolean contentReady;

    /** Creates the plugin with its shipped configuration defaults. */
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
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
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
        components.messageService().info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        // Ahead of the runtimeInitialized guard: a partially enabled module may already have published
        // "loading", and that has to be revoked even when the rest of the teardown is skipped.
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
        // Providers stop first: once contents are being flushed, a combat snapshot must not observe a
        // half-torn-down module.
        components.providerRegistrar().unregister();
        // Closing windows persists whatever a player was holding in the payload before the flush runs.
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

    /**
     * Reloads configuration, parts, GUI templates and sets.
     *
     * @return how many slot instances are active after the reload
     */
    public int reloadContent() {
        contentReady = false;
        publishLoading();
        int result = lifecycleCoordinator.reload(this);
        ConfigCommitGate.evaluate(components.messageService(), MODULE);
        contentReady = true;
        publishReady();
        return result;
    }

    /**
     * {@return whether this module's configured content has finished loading}
     *
     * <p>Read by the API bridge so {@code status()} means "data is loaded" rather than "the runtime
     * components were constructed".</p>
     */
    public boolean contentReady() {
        return contentReady;
    }

    /**
     * Publishes "my data is loaded" to CoreLib's readiness registry.
     *
     * <p>Called from a plain method body with no lock held, so the waiting third-party callbacks that
     * the registry runs synchronously cannot deadlock against this module's state.</p>
     */
    private void publishReady() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleReady(getName()));
    }

    private void publishLoading() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleLoading(getName()));
    }

    private void publishAbsent() {
        publishReadiness(coreLibPlugin -> coreLibPlugin.markModuleAbsent(getName()));
    }

    /**
     * Runs a readiness publication, tolerating CoreLib being gone.
     *
     * @param action what to publish
     */
    private void publishReadiness(Consumer<EmakiCoreLibPlugin> action) {
        try {
            action.accept(JavaPlugin.getPlugin(EmakiCoreLibPlugin.class));
        } catch (RuntimeException | LinkageError exception) {
            getLogger().fine("EmakiAccessory readiness publication skipped: " + exception);
        }
    }

    /** {@return the active part configuration} */
    public AccessoryPartRegistry partRegistry() {
        return partRegistry.get();
    }

    /**
     * Installs a freshly resolved part configuration.
     *
     * @param registry the new configuration
     */
    void partRegistry(AccessoryPartRegistry registry) {
        partRegistry.set(registry == null ? AccessoryPartRegistry.empty() : registry);
    }

    @Override
    public MessageService messageService() {
        return components == null ? null : components.messageService();
    }

    /** {@return the language loader, or {@code null} before enable completes} */
    public LanguageLoader languageLoader() {
        return components == null ? null : components.languageLoader();
    }

    /** {@return CoreLib's execution dispatcher, or {@code null} before enable completes} */
    public ExecutionDispatcher executionDispatcher() {
        return components == null ? null : components.executionDispatcher();
    }

    /** {@return CoreLib's GUI service, or {@code null} before enable completes} */
    public GuiService guiService() {
        return components == null ? null : components.guiService();
    }

    /** {@return the GUI template loader, or {@code null} before enable completes} */
    public GuiTemplateLoader guiTemplateLoader() {
        return components == null ? null : components.guiTemplateLoader();
    }

    /** {@return the parts loader, or {@code null} before enable completes} */
    public AccessoryPartLoader partLoader() {
        return components == null ? null : components.partLoader();
    }

    /** {@return the sets loader, or {@code null} before enable completes} */
    public AccessorySetLoader setLoader() {
        return components == null ? null : components.setLoader();
    }

    /** {@return the duplicate-accessory rule, or {@code null} before enable completes} */
    public AccessoryUniqueService uniqueService() {
        return components == null ? null : components.uniqueService();
    }

    /** {@return the per-player store, or {@code null} before enable completes} */
    public PlayerAccessoryStore accessoryStore() {
        return components == null ? null : components.accessoryStore();
    }

    /** {@return the accessory set evaluator, or {@code null} before enable completes} */
    public AccessorySetService setService() {
        return components == null ? null : components.setService();
    }

    /** {@return the contribution snapshot cache, or {@code null} before enable completes} */
    public AccessoryContributionService contributionService() {
        return components == null ? null : components.contributionService();
    }

    /** {@return the accessory window manager, or {@code null} before enable completes} */
    public AccessoryGuiService accessoryGuiService() {
        return components == null ? null : components.accessoryGuiService();
    }

    /** {@return the write lease registry, or {@code null} before enable completes} */
    public AccessoryWriteSessionRegistry writeSessions() {
        return components == null ? null : components.writeSessions();
    }

    /** {@return administrative data operations, or {@code null} before enable completes} */
    public AccessoryAdminService adminService() {
        return components == null ? null : components.adminService();
    }

    /** {@return the structured debug command handler, or {@code null} before enable completes} */
    public DebugCommand debugCommand() {
        return debugCommand;
    }

    /** {@return the CoreLib plugin instance} */
    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    /** {@return whether the disable path has begun} */
    public boolean isShutdownStarted() {
        return shutdownStarted.get();
    }

    /**
     * Opens a player's own accessory window.
     *
     * <p>Refuses rather than queues when the data has not loaded yet: opening an empty window over
     * unloaded data would look like the accessories had been wiped.
     *
     * @param player the player
     * @return whether a window was opened
     */
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

    /**
     * Opens an accessory window over an arbitrary payload, for administrative viewing.
     *
     * @param viewer      the player who will see the window
     * @param accessories the payload to show
     * @return whether a window was opened
     */
    public boolean open(Player viewer, PlayerAccessories accessories) {
        if (viewer == null || accessories == null || components == null) {
            return false;
        }
        AccessoryGuiHandler handler = new AccessoryGuiHandler(
                this, components.accessoryGuiService(), components.uniqueService(), accessories);
        // The lease is advisory for read-only viewers: acquiring simply fails and canWrite then refuses
        // every mutation, so a second viewer still gets a usable read-only window.
        components.writeSessions().acquire(accessories.playerId(), viewer.getUniqueId());
        return components.accessoryGuiService().open(viewer, handler) != null;
    }

    /**
     * Recomputes and republishes one player's contributions.
     *
     * <p>Call on the owner thread of the player holding the items: the attribute parser writes its result
     * back into item PDC as a cache, so it is not safe off-thread.
     *
     * @param accessories the payload whose contributions changed
     */
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
        // The lease is checked on the commit path rather than at render time, so a viewer who lost the
        // lease cannot keep writing through an already-open window.
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

    /** {@return a future that completes once pending accessory writes settle} */
    CompletableFuture<Integer> saveAllAsync() {
        return components == null
                ? CompletableFuture.completedFuture(0)
                : components.accessoryStore().saveAllAsync();
    }
}
