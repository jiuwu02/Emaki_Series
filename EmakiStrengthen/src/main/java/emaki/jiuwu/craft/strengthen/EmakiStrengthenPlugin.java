package emaki.jiuwu.craft.strengthen;

import java.nio.file.Path;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.strengthen.api.EmakiStrengthenApi;
import emaki.jiuwu.craft.strengthen.config.AppConfig;
import emaki.jiuwu.craft.strengthen.loader.AppConfigLoader;
import emaki.jiuwu.craft.strengthen.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.strengthen.loader.LanguageLoader;
import emaki.jiuwu.craft.strengthen.loader.StrengthenRecipeLoader;
import emaki.jiuwu.craft.strengthen.service.BootstrapService;
import emaki.jiuwu.craft.strengthen.service.ChanceCalculator;
import emaki.jiuwu.craft.strengthen.service.MessageService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRecipeResolver;
import emaki.jiuwu.craft.strengthen.service.StrengthenActionCoordinator;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;
import emaki.jiuwu.craft.strengthen.service.StrengthenEconomyService;
import emaki.jiuwu.craft.strengthen.service.StrengthenGuiService;
import emaki.jiuwu.craft.strengthen.service.StrengthenRefreshService;
import emaki.jiuwu.craft.strengthen.service.StrengthenSnapshotBuilder;

public final class EmakiStrengthenPlugin extends JavaPlugin implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakistrengthen";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  __   __  ______  ______  __  __  ______  __   __    
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\__  _\\/\\  == \\/\\  ___\\/\\ "-.\\ \\/\\  ___\\/\\__  _\\/\\ \\_\\ \\/\\  ___\\/\\ "-.\\ \\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\___  \\/_/\\ \\/\\ \\  __<\\ \\  __\\\\ \\ \\-.  \\ \\ \\__ \\/_/\\ \\/\\ \\  __ \\ \\  __\\\\ \\ \\-.  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\/\\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\_____\\ \\_\\\\"\\_\\ \\_____\\ \\ \\_\\ \\ \\_\\ \\_\\ \\_____\\ \\_\\\\"\\_\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/ /_/\\/_____/\\/_/ \\/_/\\/_____/  \\/_/  \\/_/\\/_/\\/_____/\\/_/ \\/_/
""";

    private final StrengthenLifecycleCoordinator lifecycleCoordinator = new StrengthenLifecycleCoordinator();
    private final StrengthenCommandRouter commandRouter = new StrengthenCommandRouter(this);
    private final StrengthenItemRefreshListener itemRefreshListener = new StrengthenItemRefreshListener(this);
    private final GuiItemBuilder.ItemFactory coreItemFactory = (source, amount) -> {
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.getInstance();
        return coreLib == null ? null : coreLib.itemSourceService().createItem(source, amount);
    };

    private AppConfigLoader appConfigLoader;
    private LanguageLoader languageLoader;
    private StrengthenRecipeLoader recipeLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private StrengthenRecipeResolver recipeResolver;
    private ChanceCalculator chanceCalculator;
    private StrengthenEconomyService economyService;
    private StrengthenSnapshotBuilder snapshotBuilder;
    private StrengthenActionCoordinator actionCoordinator;
    private StrengthenAttemptService attemptService;
    private StrengthenRefreshService refreshService;
    private StrengthenGuiService strengthenGuiService;

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    @Override
    public void onEnable() {
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        registerApi();
        registerCommandHandler();
        registerEventHandlers();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        lifecycleCoordinator.shutdown(this);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        lifecycleCoordinator.reload(this, closeOpenInventories);
    }

    private void applyRuntimeComponents(StrengthenRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        recipeLoader = components.recipeLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        recipeResolver = components.recipeResolver();
        chanceCalculator = components.chanceCalculator();
        economyService = components.economyService();
        snapshotBuilder = components.snapshotBuilder();
        actionCoordinator = components.actionCoordinator();
        attemptService = components.attemptService();
        refreshService = components.refreshService();
        strengthenGuiService = components.strengthenGuiService();
    }

    private void registerApi() {
        getServer().getServicesManager().register(EmakiStrengthenApi.class, attemptService, this, ServicePriority.Normal);
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
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(itemRefreshListener, this);
    }

    public AppConfigLoader appConfigLoader() {
        return appConfigLoader;
    }

    public AppConfig appConfig() {
        return appConfigLoader == null ? AppConfig.defaults() : appConfigLoader.current();
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public StrengthenRecipeLoader recipeLoader() {
        return recipeLoader;
    }

    public StrengthenRecipeLoader replaceLoader() {
        return recipeLoader;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public GuiService guiService() {
        return guiService;
    }

    public StrengthenRecipeResolver recipeResolver() {
        return recipeResolver;
    }

    public ChanceCalculator chanceCalculator() {
        return chanceCalculator;
    }

    public StrengthenEconomyService economyService() {
        return economyService;
    }

    public StrengthenSnapshotBuilder snapshotBuilder() {
        return snapshotBuilder;
    }

    public StrengthenActionCoordinator actionCoordinator() {
        return actionCoordinator;
    }

    public StrengthenAttemptService attemptService() {
        return attemptService;
    }

    public StrengthenRefreshService refreshService() {
        return refreshService;
    }

    public StrengthenGuiService strengthenGuiService() {
        return strengthenGuiService;
    }

    public GuiItemBuilder.ItemFactory coreItemFactory() {
        return coreItemFactory;
    }
}
