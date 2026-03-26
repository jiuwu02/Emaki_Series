package emaki.jiuwu.craft.forge;

import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.AppConfigLoader;
import emaki.jiuwu.craft.forge.loader.BlueprintLoader;
import emaki.jiuwu.craft.forge.loader.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.loader.LanguageLoader;
import emaki.jiuwu.craft.forge.loader.MaterialLoader;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.BootstrapService;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgePdcService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.MessageService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

public class EmakiForgePlugin extends JavaPlugin implements Listener, TabExecutor {

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
    private ForgePdcService pdcService;
    private ForgeService forgeService;
    private ForgeGuiService forgeGuiService;
    private RecipeBookGuiService recipeBookGuiService;
    private BukkitTask autoSaveTask;

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    @Override
    public void onEnable() {
        initializeServices();
        messageService.info("console.plugin_starting");
        bootstrapService.bootstrap();
        reloadPluginState(false);
        if (getCommand("jiuwuforge") != null) {
            getCommand("jiuwuforge").setExecutor(this);
            getCommand("jiuwuforge").setTabCompleter(this);
        }
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(this, this);
        scheduleAutoSave();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (playerDataStore != null) {
            messageService.info("console.saving_player_data");
            messageService.info("console.player_data_saved", Map.of("count", playerDataStore.saveAll()));
        }
        if (forgeGuiService != null) {
            forgeGuiService.clearAllSessions();
        }
        if (recipeBookGuiService != null) {
            recipeBookGuiService.clearAllBooks();
        }
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
    }

    private void initializeServices() {
        appConfigLoader = new AppConfigLoader(this);
        languageLoader = new LanguageLoader(this);
        blueprintLoader = new BlueprintLoader(this);
        materialLoader = new MaterialLoader(this);
        recipeLoader = new RecipeLoader(this);
        guiTemplateLoader = new GuiTemplateLoader(this);
        playerDataStore = new PlayerDataStore(this);
        messageService = new MessageService(this, languageLoader, this::appConfig);
        bootstrapService = new BootstrapService(this, messageService);
        guiService = new GuiService(this);
        itemIdentifierService = new ItemIdentifierService(this);
        pdcService = new ForgePdcService(this);
        forgeService = new ForgeService(this);
        forgeGuiService = new ForgeGuiService(this, guiService);
        recipeBookGuiService = new RecipeBookGuiService(this, guiService);
    }

    public void reloadPluginState(boolean closeOpenInventories) {
        if (closeOpenInventories) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (forgeGuiService.getSession(player) != null || recipeBookGuiService.isRecipeBookInventory(player)) {
                    player.closeInventory();
                }
            }
            forgeGuiService.clearAllSessions();
            recipeBookGuiService.clearAllBooks();
        }
        appConfigLoader.load();
        languageLoader.load();
        languageLoader.setLanguage(appConfig().language());
        blueprintLoader.load();
        materialLoader.load();
        recipeLoader.load();
        guiTemplateLoader.load();
        playerDataStore.load();
        itemIdentifierService.refresh();
        validateConfiguredExternalSources();
        scheduleAutoSave();
    }

    private void scheduleAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
        if (appConfig().historyEnabled() && appConfig().historyAutoSave()) {
            autoSaveTask = getServer().getScheduler().runTaskTimer(
                this,
                () -> playerDataStore.saveAll(),
                appConfig().historySaveInterval(),
                appConfig().historySaveInterval()
            );
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        return switch (args[0].toLowerCase()) {
            case "help" -> {
                sendHelp(sender);
                yield true;
            }
            case "forge" -> handleForge(sender);
            case "book" -> handleBook(sender);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender);
            case "list" -> handleList(sender, args);
            default -> {
                messageService.send(sender, "general.unknown_command");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("help", "forge", "book", "reload", "debug", "list")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    result.add(sub);
                }
            }
            return result;
        }
        if (args.length == 2 && "list".equalsIgnoreCase(args[0])) {
            for (String sub : List.of("recipes", "blueprints", "materials")) {
                if (sub.startsWith(args[1].toLowerCase())) {
                    result.add(sub);
                }
            }
        }
        return result;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerDataStore.save(event.getPlayer().getUniqueId());
        playerDataStore.clear(event.getPlayer().getUniqueId());
    }

    private boolean handleForge(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "general.player_only");
            return true;
        }
        return forgeGuiService.openGeneralForgeGui(player);
    }

    private boolean handleBook(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messageService.send(sender, "general.player_only");
            return true;
        }
        if (!sender.hasPermission("jiuwuforge.book") && !sender.hasPermission("jiuwuforge.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }
        return recipeBookGuiService.openRecipeBook(player);
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("jiuwuforge.reload") && !sender.hasPermission("jiuwuforge.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }
        bootstrapService.bootstrap();
        reloadPluginState(true);
        messageService.send(sender, "general.reload_success");
        messageService.sendRaw(sender, messageService.message("general.reload_summary", Map.of(
            "blueprints", blueprintLoader.all().size(),
            "materials", materialLoader.all().size(),
            "recipes", recipeLoader.all().size(),
            "guis", guiTemplateLoader.all().size()
        )));
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("jiuwuforge.debug") && !sender.hasPermission("jiuwuforge.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }
        AppConfig current = appConfig();
        appConfigLoader.overrideCurrent(new AppConfig(
            current.language(),
            current.configVersion(),
            current.releaseDefaultData(),
            current.qualitySettings(),
            current.defaultNumberFormat(),
            current.integerNumberFormat(),
            current.percentageNumberFormat(),
            current.opBypass(),
            current.invalidAsFailure(),
            current.historyEnabled(),
            current.historyAutoSave(),
            current.historySaveInterval(),
            !current.debug()
        ));
        messageService.send(sender, appConfig().debug() ? "debug.enabled" : "debug.disabled");
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        if (!sender.hasPermission("jiuwuforge.admin")) {
            messageService.send(sender, "general.no_permission");
            return true;
        }
        if (args.length < 2) {
            messageService.send(sender, "general.invalid_args");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "recipes" -> {
                messageService.sendRaw(sender, messageService.message("command.list.recipes_header", Map.of("count", recipeLoader.all().size())));
                recipeLoader.all().forEach((id, recipe) -> messageService.sendRaw(sender, messageService.message("command.list.recipe_line", Map.of("id", id, "name", recipe.displayName()))));
            }
            case "blueprints" -> {
                messageService.sendRaw(sender, messageService.message("command.list.blueprints_header", Map.of("count", blueprintLoader.all().size())));
                blueprintLoader.all().forEach((id, blueprint) -> messageService.sendRaw(sender, messageService.message("command.list.blueprint_line", Map.of("id", id, "name", blueprint.displayName()))));
            }
            case "materials" -> {
                messageService.sendRaw(sender, messageService.message("command.list.materials_header", Map.of("count", materialLoader.all().size())));
                materialLoader.all().forEach((id, material) -> messageService.sendRaw(sender, messageService.message("command.list.material_line", Map.of("id", id, "name", material.displayName(), "capacity", material.capacityCost()))));
            }
            default -> messageService.send(sender, "general.invalid_args");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        messageService.sendRaw(sender, messageService.message("command.help.header"));
        Map<String, String> lines = Map.of(
            "forge", "打开独立锻造台",
            "book", "打开配方图鉴",
            "reload", "重载配置文件",
            "debug", "切换调试模式",
            "help", "显示帮助信息",
            "list <type>", "列出配置项"
        );
        lines.forEach((commandName, description) ->
            messageService.sendRaw(sender, messageService.message("command.help.line", Map.of("cmd", commandName, "desc", description))));
        messageService.sendRaw(sender, messageService.message("command.help.footer"));
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

    public ForgePdcService pdcService() {
        return pdcService;
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

    private void validateConfiguredExternalSources() {
        for (var entry : blueprintLoader.all().entrySet()) {
            validateSource(entry.getValue().source(), "blueprint:" + entry.getKey() + ".source");
        }
        for (var entry : materialLoader.all().entrySet()) {
            validateSource(entry.getValue().source(), "material:" + entry.getKey() + ".source");
        }
        for (var entry : recipeLoader.all().entrySet()) {
            validateSource(entry.getValue().targetItemSource(), "recipe:" + entry.getKey() + ".target_item");
            if (entry.getValue().result() != null) {
                validateSource(entry.getValue().result().outputItem(), "recipe:" + entry.getKey() + ".result.output_item");
            }
        }
        for (Map.Entry<String, GuiTemplate> entry : guiTemplateLoader.all().entrySet()) {
            for (GuiSlot slot : entry.getValue().slots().values()) {
                ItemSource source = ItemSourceUtil.parse(slot.item());
                if (source != null) {
                    validateSource(source, "gui:" + entry.getKey() + ".slots." + slot.key() + ".item");
                }
            }
        }
    }

    private void validateSource(ItemSource source, String location) {
        if (source != null) {
            itemIdentifierService.validateConfiguredSource(source, location);
        }
    }
}
