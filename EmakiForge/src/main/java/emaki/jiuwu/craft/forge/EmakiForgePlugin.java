package emaki.jiuwu.craft.forge;

import java.nio.file.Path;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.AppConfigLoader;
import emaki.jiuwu.craft.forge.loader.BlueprintLoader;
import emaki.jiuwu.craft.forge.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.loader.LanguageLoader;
import emaki.jiuwu.craft.forge.loader.MaterialLoader;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.BootstrapService;
import emaki.jiuwu.craft.forge.service.EditorGuiService;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.MessageService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

public class EmakiForgePlugin extends JavaPlugin implements LogMessagesProvider {

    private static final String ROOT_COMMAND = "emakiforge";

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  ______  ______  ______  ______  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\  ___\\/\\  __ \\/\\  == \\/\\  ___\\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  __\\\\ \\ \\/\\ \\ \\  __<\\ \\ \\__ \\ \\  __\\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\   \\ \\_____\\ \\_\\ \\_\\ \\_____\\ \\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/    \\/_____/\\/_/ /_/\\/_____/\\/_____/
""";

    private final ForgeLifecycleCoordinator lifecycleCoordinator = new ForgeLifecycleCoordinator();
    private final ForgeCommandRouter commandRouter = new ForgeCommandRouter(this);
    private final ForgePlayerDataListener playerDataListener = new ForgePlayerDataListener(this);
    private final ForgeItemRefreshListener itemRefreshListener = new ForgeItemRefreshListener(this);

    private AppConfigLoader appConfigLoader;
    private LanguageLoader languageLoader;
    private BlueprintLoader blueprintLoader;
    private MaterialLoader materialLoader;
    private RecipeLoader recipeLoader;
    private GuiTemplateLoader guiTemplateLoader;
    private PlayerDataStore playerDataStore;
    private MessageService messageService;
    private BootstrapService bootstrapService;
    private GuiService guiService;
    private ItemIdentifierService itemIdentifierService;
    private ForgeItemRefreshService itemRefreshService;
    private ForgeService forgeService;
    private ForgeGuiService forgeGuiService;
    private RecipeBookGuiService recipeBookGuiService;
    private EditorGuiService editorGuiService;
    private BukkitTask autoSaveTask;

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
        registerCommandHandler();
        registerEventHandlers();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        lifecycleCoordinator.shutdown(this, autoSaveTask);
        autoSaveTask = null;
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        autoSaveTask = lifecycleCoordinator.reload(this, autoSaveTask, closeOpenInventories);
    }

    private void applyRuntimeComponents(ForgeRuntimeComponents components) {
        appConfigLoader = components.appConfigLoader();
        languageLoader = components.languageLoader();
        blueprintLoader = components.blueprintLoader();
        materialLoader = components.materialLoader();
        recipeLoader = components.recipeLoader();
        guiTemplateLoader = components.guiTemplateLoader();
        playerDataStore = components.playerDataStore();
        messageService = components.messageService();
        bootstrapService = components.bootstrapService();
        guiService = components.guiService();
        itemIdentifierService = components.itemIdentifierService();
        itemRefreshService = components.itemRefreshService();
        forgeService = components.forgeService();
        forgeGuiService = components.forgeGuiService();
        recipeBookGuiService = components.recipeBookGuiService();
        editorGuiService = components.editorGuiService();
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
        getServer().getPluginManager().registerEvents(playerDataListener, this);
        getServer().getPluginManager().registerEvents(itemRefreshListener, this);
        if (editorGuiService != null) {
            getServer().getPluginManager().registerEvents(editorGuiService.inputService(), this);
        }
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

    public BlueprintLoader blueprintLoader() {
        return blueprintLoader;
    }

    public MaterialLoader materialLoader() {
        return materialLoader;
    }

    public RecipeLoader recipeLoader() {
        return recipeLoader;
    }

    public GuiTemplateLoader guiTemplateLoader() {
        return guiTemplateLoader;
    }

    public PlayerDataStore playerDataStore() {
        return playerDataStore;
    }

    public MessageService messageService() {
        return messageService;
    }

    public BootstrapService bootstrapService() {
        return bootstrapService;
    }

    public GuiService guiService() {
        return guiService;
    }

    public ItemIdentifierService itemIdentifierService() {
        return itemIdentifierService;
    }

    public ForgeItemRefreshService itemRefreshService() {
        return itemRefreshService;
    }

    public ForgeService forgeService() {
        return forgeService;
    }

    public ForgeGuiService forgeGuiService() {
        return forgeGuiService;
    }

    public RecipeBookGuiService recipeBookGuiService() {
        return recipeBookGuiService;
    }

    public EditorGuiService editorGuiService() {
        return editorGuiService;
    }
}
