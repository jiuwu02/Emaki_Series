package emaki.jiuwu.craft.codex;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.codex.action.GrantAdvancementAction;
import emaki.jiuwu.craft.codex.action.LockRecipeAction;
import emaki.jiuwu.craft.codex.action.UnlockRecipeAction;
import emaki.jiuwu.craft.codex.advancement.AdvancementJsonBuilder;
import emaki.jiuwu.craft.codex.advancement.AdvancementListener;
import emaki.jiuwu.craft.codex.advancement.AdvancementPlatform;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.packet.AdvancementPacketGateway;
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.codex.listener.PlayerConnectionListener;
import emaki.jiuwu.craft.codex.recipe.RecipeCollector;
import emaki.jiuwu.craft.codex.recipe.RecipeIndex;
import emaki.jiuwu.craft.codex.recipe.RecipeVisibilityService;
import emaki.jiuwu.craft.codex.recipe.loader.ManualRecipeLoader;
import emaki.jiuwu.craft.codex.recipe.sync.RecipeSyncGateway;
import emaki.jiuwu.craft.codex.store.PlayerUnlockStore;
import emaki.jiuwu.craft.codex.api.EmakiCodexApi;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.async.TaskHandle;
import emaki.jiuwu.craft.corelib.debug.DebugCommand;
import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.corelib.metrics.BStatsRegistration;
import emaki.jiuwu.craft.corelib.plugin.AbstractConfigurableEmakiPlugin;
import emaki.jiuwu.craft.corelib.service.EmakiServiceRegistry;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

/**
 * EmakiCodex main plugin: bridges server recipes to client viewers and registers
 * vanilla advancements driven by corelib actions. Follows the EmakiForge lifecycle
 * skeleton.
 */
public class EmakiCodexPlugin extends AbstractConfigurableEmakiPlugin<AppConfig>
        implements LogMessagesProvider, EmakiServiceRegistry {

    private static final String ROOT_COMMAND = "codex";
    private static final Set<String> DEBUG_MODULES = Set.of("recipe", "advancement", "sync");
    private static final int BSTATS_PLUGIN_ID = 32376;

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  _____   ______  __  __
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  __-./\\  ___\\/\\_\\_\\_\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\ \\___\\ \\ \\/\\ \\ \\ \\/\\ \\ \\  __\\\\/_/\\_\\/_
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_____\\ \\_____\\ \\____-\\ \\_____\\/\\_\\/\\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/\\/_____/\\/____/ \\/_____/\\/_/\\/_/
""";

    private final CodexLifecycleCoordinator lifecycleCoordinator = new CodexLifecycleCoordinator();
    private final CodexCommandRouter commandRouter = new CodexCommandRouter(this);
    private final PlayerConnectionListener connectionListener = new PlayerConnectionListener(this);

    private YamlConfigLoader<AppConfig> appConfigLoader;
    private MessageService messageService;
    private emaki.jiuwu.craft.corelib.loader.LanguageLoader languageLoader;
    private emaki.jiuwu.craft.corelib.bootstrap.BootstrapService bootstrapService;
    private PlayerUnlockStore unlockStore;
    private ManualRecipeLoader manualRecipeLoader;
    private AdvancementPageLoader advancementPageLoader;
    private RecipeIndex recipeIndex;
    private RecipeCollector recipeCollector;
    private RecipeVisibilityService recipeVisibilityService;
    private RecipeSyncGateway recipeSyncGateway;
    private AdvancementPlatform advancementPlatform;
    private AdvancementJsonBuilder advancementJsonBuilder;
    private AdvancementRegistrar advancementRegistrar;
    private AdvancementService advancementService;
    private AdvancementPacketGateway advancementPacketGateway;

    private AdvancementListener advancementListener;
    private DebugCommand debugCommand;
    private TaskHandle autoSaveTask;
    private BStatsRegistration metrics;

    private final EmakiCodexApi.Bridge apiBridge = new CodexApiBridge();

    public EmakiCodexPlugin() {
        super(AppConfig::defaults);
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        registerActions();
        reloadPluginState();
        registerCommandHandler();
        registerEventHandlers();
        EmakiCodexApi.install(apiBridge);
        metrics = coreLib().registerBStats(this, BSTATS_PLUGIN_ID);
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        EmakiCodexApi.uninstall(apiBridge);
        if (metrics != null) {
            metrics.close();
            metrics = null;
        }
        lifecycleCoordinator.shutdown(this, autoSaveTask);
        autoSaveTask = null;
    }

    public void reloadPluginState() {
        autoSaveTask = lifecycleCoordinator.reload(this, autoSaveTask);
    }

    private void applyRuntimeComponents(CodexRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        unlockStore = components.unlockStore();
        manualRecipeLoader = components.manualRecipeLoader();
        advancementPageLoader = components.advancementPageLoader();
        recipeIndex = components.recipeIndex();
        recipeCollector = components.recipeCollector();
        recipeVisibilityService = components.recipeVisibilityService();
        recipeSyncGateway = components.recipeSyncGateway();
        advancementPlatform = components.advancementPlatform();
        advancementJsonBuilder = components.advancementJsonBuilder();
        advancementRegistrar = components.advancementRegistrar();
        advancementService = components.advancementService();
        advancementPacketGateway = components.advancementPacketGateway();

        setDebugLogger(new DebugLogger(this, languageLoader));
        debugCommand = new DebugCommand(debugLogger(), DEBUG_MODULES);
        registerServices(components);
    }

    private void registerActions() {
        EmakiCoreLibPlugin coreLib = coreLib();
        coreLib.actionRegistry().register(this, "codex", new UnlockRecipeAction(this));
        coreLib.actionRegistry().register(this, "codex", new LockRecipeAction(this));
        coreLib.actionRegistry().register(this, "codex", new GrantAdvancementAction(this));
    }

    private void registerCommandHandler() {
        PluginCommand pluginCommand = getCommand(ROOT_COMMAND);
        if (pluginCommand == null) {
            return;
        }
        pluginCommand.setExecutor(commandRouter);
        pluginCommand.setTabCompleter(commandRouter);
    }

    private void registerEventHandlers() {
        advancementListener = new AdvancementListener(this, advancementRegistrar);
        getServer().getPluginManager().registerEvents(connectionListener, this);
        getServer().getPluginManager().registerEvents(advancementListener, this);
        registerAdvancementPacketChannel();
    }

    private void registerAdvancementPacketChannel() {
        if (!appConfig().packetCoordinates()) {
            return;
        }
        if (advancementPacketGateway.register()) {
            messageService.info("console.advancement_packet_enabled");
        } else {
            messageService.info("console.advancement_packet_absent");
        }
    }

    @Override
    public YamlConfigLoader<AppConfig> appConfigLoader() {
        return appConfigLoader;
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public emaki.jiuwu.craft.corelib.loader.LanguageLoader languageLoader() {
        return languageLoader;
    }

    public emaki.jiuwu.craft.corelib.bootstrap.BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public EmakiCoreLibPlugin coreLib() {
        return JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
    }

    public PlayerUnlockStore unlockStore() {
        return unlockStore;
    }

    public ManualRecipeLoader manualRecipeLoader() {
        return manualRecipeLoader;
    }

    public AdvancementPageLoader advancementPageLoader() {
        return advancementPageLoader;
    }

    public RecipeIndex recipeIndex() {
        return recipeIndex;
    }

    public RecipeCollector recipeCollector() {
        return recipeCollector;
    }

    public RecipeVisibilityService recipeVisibilityService() {
        return recipeVisibilityService;
    }

    public RecipeSyncGateway recipeSyncGateway() {
        return recipeSyncGateway;
    }

    public AdvancementRegistrar advancementRegistrar() {
        return advancementRegistrar;
    }

    public AdvancementService advancementService() {
        return advancementService;
    }

    public AdvancementPacketGateway advancementPacketGateway() {
        return advancementPacketGateway;
    }

    public DebugCommand debugCommand() {
        return debugCommand;
    }

    /** Bridge implementation backing the public {@link EmakiCodexApi} facade. */
    private final class CodexApiBridge implements EmakiCodexApi.Bridge {

        @Override
        public String apiVersion() {
            return getDescription().getVersion();
        }

        @Override
        public String pluginName() {
            return getName();
        }

        @Override
        public boolean isReady() {
            return isEnabled() && recipeIndex != null && advancementRegistrar != null;
        }

        @Override
        public boolean unlockRecipe(UUID player, String recipeId) {
            boolean changed = unlockStore.unlock(player, recipeId);
            syncIfOnline(player);
            return changed;
        }

        @Override
        public boolean lockRecipe(UUID player, String recipeId) {
            boolean changed = unlockStore.lock(player, recipeId);
            syncIfOnline(player);
            return changed;
        }

        @Override
        public boolean isRecipeVisible(UUID player, String recipeId) {
            return recipeVisibilityService.isVisible(player, recipeId);
        }

        @Override
        public Set<String> unlockedRecipes(UUID player) {
            return unlockStore.unlockedRecipes(player);
        }

        @Override
        public boolean grantAdvancement(UUID player, String advancementId) {
            Player target = resolveOnline(player);
            return target != null && advancementService.grant(target, advancementId);
        }

        @Override
        public boolean revokeAdvancement(UUID player, String advancementId) {
            Player target = resolveOnline(player);
            return target != null && advancementService.revoke(target, advancementId);
        }

        private void syncIfOnline(UUID player) {
            Player target = resolveOnline(player);
            if (target != null) {
                recipeSyncGateway.sync(target);
            }
        }

        private Player resolveOnline(UUID player) {
            if (player == null) {
                return null;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(player);
            return offline.getPlayer();
        }
    }
}
