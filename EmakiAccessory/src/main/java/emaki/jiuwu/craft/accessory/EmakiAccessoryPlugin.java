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
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.accessory.api.EmakiAccessoryApi;
import emaki.jiuwu.craft.accessory.apiimpl.ServiceBackedAccessoryBridge;
import emaki.jiuwu.craft.accessory.command.AccessoryCommandRouter;
import emaki.jiuwu.craft.accessory.config.AccessoryConfigPrecheckContributor;
import emaki.jiuwu.craft.accessory.config.AccessorySlotSourceConfig;
import emaki.jiuwu.craft.accessory.config.AppConfig;
import emaki.jiuwu.craft.accessory.gui.AccessoryGuiHandler;
import emaki.jiuwu.craft.accessory.gui.AccessoryGuiService;
import emaki.jiuwu.craft.accessory.listener.AccessoryPlayerListener;
import emaki.jiuwu.craft.accessory.loader.AccessoryPageLoader;
import emaki.jiuwu.craft.accessory.loader.AccessoryPartLoader;
import emaki.jiuwu.craft.accessory.loader.AccessorySetLoader;
import emaki.jiuwu.craft.accessory.model.AccessoryContributionSnapshot;
import emaki.jiuwu.craft.accessory.model.PlayerAccessories;
import emaki.jiuwu.craft.accessory.service.AccessoryAdminService;
import emaki.jiuwu.craft.accessory.service.AccessoryContributionService;
import emaki.jiuwu.craft.accessory.service.AccessoryDurabilityService;
import emaki.jiuwu.craft.accessory.service.AccessoryPageRegistry;
import emaki.jiuwu.craft.accessory.service.AccessoryPartRegistry;
import emaki.jiuwu.craft.accessory.service.AccessoryRetrievalService;
import emaki.jiuwu.craft.accessory.service.AccessorySetService;
import emaki.jiuwu.craft.accessory.service.AccessoryUniqueService;
import emaki.jiuwu.craft.accessory.service.AccessoryValidationService;
import emaki.jiuwu.craft.accessory.service.AccessoryWriteSessionRegistry;
import emaki.jiuwu.craft.accessory.service.PlayerAccessoryStore;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigCommitGate;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckLifecycleSupport;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
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
    private final AtomicReference<AccessoryPageRegistry> pageRegistry =
            new AtomicReference<>(AccessoryPageRegistry.empty());
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
        pageRegistry.set(AccessoryPageRegistry.empty());
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

    public AccessoryPageLoader pageLoader() {
        return components == null ? null : components.pageLoader();
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

    public boolean openOwn(Player player, String pageId) {
        if (player == null || components == null) {
            return false;
        }
        UUID targetId = player.getUniqueId();
        if (components.accessoryStore().cached(targetId) == null) {
            components.messageService().send(player, "general.data_loading");
            return false;
        }
        return open(player, targetId, pageId);
    }

    public boolean open(Player viewer, UUID targetId, String pageId) {
        if (viewer == null || targetId == null || components == null) {
            return false;
        }
        PlayerAccessories view = components.accessoryStore().cached(targetId);
        if (view == null) {
            components.messageService().send(viewer, "general.data_loading");
            return false;
        }
        AccessoryPageRegistry pages = pageRegistry();
        String requested = Texts.isBlank(pageId)
                ? pages.resolveEnabledPage(view.enabledPage())
                : Texts.normalizeId(pageId);
        if (!pages.hasPage(requested)) {
            components.messageService().send(viewer, "command.page_unknown",
                    Map.of("page", Texts.toStringSafe(pageId)));
            return false;
        }
        if (!canUsePage(viewer, requested)) {
            components.messageService().send(viewer, "command.page_no_permission",
                    Map.of("page", requested));
            return false;
        }
        AccessoryGuiHandler handler = new AccessoryGuiHandler(
                this, components.accessoryGuiService(), components.uniqueService(),
                targetId, components.accessoryStore().currentGeneration(targetId), requested);

        components.writeSessions().acquire(targetId, viewer.getUniqueId());
        return components.accessoryGuiService().open(viewer, handler) != null;
    }

    public int retrievePage(Player owner, UUID targetId, String pageId) {
        if (owner == null || targetId == null || components == null) {
            return 0;
        }
        Integer retrieved = mutateSession(targetId, components.accessoryStore().currentGeneration(targetId),
                accessories -> {
                    int count = AccessoryRetrievalService.retrievePage(owner, accessories, pageId);
                    recomputeContributions(accessories);
                    return count;
                });
        return retrieved == null ? 0 : retrieved;
    }

    public PlayerAccessories view(UUID targetId) {
        return components == null || targetId == null
                ? null
                : components.accessoryStore().cached(targetId);
    }

    public boolean edit(UUID targetId, long expectedGeneration, Consumer<PlayerAccessories> mutation) {
        if (mutation == null) {
            return false;
        }
        Boolean applied = mutateSession(targetId, expectedGeneration, accessories -> {
            mutation.accept(accessories);
            recomputeContributions(accessories);
            return Boolean.TRUE;
        });
        return Boolean.TRUE.equals(applied);
    }

    public boolean refreshContributions(UUID targetId) {
        if (components == null || targetId == null) {
            return false;
        }
        Boolean applied = mutateSession(targetId, components.accessoryStore().currentGeneration(targetId),
                accessories -> {
                    recomputeContributions(accessories);
                    return Boolean.TRUE;
                });
        return Boolean.TRUE.equals(applied);
    }

    public void damageActiveAccessories(Player player) {
        if (components == null || player == null || !appConfig().durability().enabled()) {
            return;
        }
        UUID targetId = player.getUniqueId();
        List<String> broken = components.accessoryStore().mutate(targetId,
                components.accessoryStore().currentGeneration(targetId),
                accessories -> {
                    String activePage = components.contributionService().effectivePage(accessories);
                    if (Texts.isBlank(activePage)) {
                        return List.<String>of();
                    }
                    return AccessoryDurabilityService.deduct(accessories, pageRegistry(), partRegistry(),
                            activePage, appConfig().durability().damagePerHit());
                });
        if (broken == null || broken.isEmpty()) {
            return;
        }
        refreshContributions(targetId);
        for (String slotInstanceId : broken) {
            components.messageService().send(player, "durability.broken_notify",
                    Map.of("slot", slotInstanceId));
        }
    }

    private <R> R mutateSession(UUID targetId,
            long expectedGeneration,
            Function<PlayerAccessories, R> mutation) {
        if (components == null || targetId == null || mutation == null) {
            return null;
        }
        R result = components.accessoryStore().mutate(targetId, expectedGeneration, mutation);
        if (result != null) {
            components.accessoryStore().saveAsync(targetId);
        }
        return result;
    }

    private void recomputeContributions(PlayerAccessories accessories) {
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
    public AccessorySlotSourceConfig slotSources() {
        AppConfig current = appConfig();
        return current == null ? AccessorySlotSourceConfig.defaults() : current.slotSources();
    }

    @Override
    public AccessoryPageRegistry pageRegistry() {
        return pageRegistry.get();
    }

    void pageRegistry(AccessoryPageRegistry registry) {
        pageRegistry.set(registry == null ? AccessoryPageRegistry.empty() : registry);
    }

    @Override
    public boolean canUsePage(Player viewer, String pageId) {
        if (viewer == null) {
            return false;
        }
        String permission = pageRegistry().permissionOf(pageId);
        return Texts.isBlank(permission) || viewer.hasPermission(permission);
    }

    @Override
    public void onPageSwitchRequested(Player viewer, UUID targetId, String pageId) {
        if (viewer == null || targetId == null || components == null) {
            return;
        }
        markPageSwitching(viewer);
        viewer.closeInventory();
        components.executionDispatcher().runEntityLater(this, viewer, () -> {
            if (!open(viewer, targetId, pageId)) {
                onWindowClosed(viewer, targetId);
            }
        }, () -> onWindowClosed(viewer, targetId), 1L);
    }

    private void markPageSwitching(Player viewer) {
        GuiSession session = components.guiService().getSession(viewer.getUniqueId());
        if (session != null && session.handler() instanceof AccessoryGuiHandler handler) {
            handler.beginPageSwitch();
        }
    }

    @Override
    public boolean canWrite(Player viewer, UUID targetId) {
        if (viewer == null || targetId == null || components == null) {
            return false;
        }
        boolean ownWindow = viewer.getUniqueId().equals(targetId);
        if (!ownWindow && !viewer.hasPermission(AccessoryCommandRouter.PERMISSION_EDIT_OTHERS)) {
            return false;
        }

        return components.writeSessions().holdsLease(targetId, viewer.getUniqueId());
    }

    @Override
    public void onWindowClosed(Player viewer, UUID targetId) {
        if (components == null || targetId == null) {
            return;
        }
        if (viewer != null) {
            components.writeSessions().release(targetId, viewer.getUniqueId());
            if (viewer.getUniqueId().equals(targetId)) {
                mutateSession(targetId, components.accessoryStore().currentGeneration(targetId),
                        accessories -> returnInvalidAccessories(viewer, accessories));
            }
        }
        components.accessoryStore().saveAsync(targetId);
    }

    private int returnInvalidAccessories(Player owner, PlayerAccessories accessories) {
        int returned = AccessoryValidationService.returnInvalidFor(
                owner, accessories, pageRegistry(), slotSources(),
                slot -> components.messageService().send(owner, "gui.invalid_returned", Map.of("slot", slot)));
        if (returned > 0) {
            recomputeContributions(accessories);
        }
        return returned;
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
