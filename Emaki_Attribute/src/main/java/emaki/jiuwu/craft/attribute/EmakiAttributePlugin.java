package emaki.jiuwu.craft.attribute;

import emaki.jiuwu.craft.attribute.bridge.MythicBridge;
import emaki.jiuwu.craft.attribute.command.AttributeCommand;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.listener.AttributeListener;
import emaki.jiuwu.craft.attribute.loader.AttributeBalanceRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LanguageLoader;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.papi.AttributePlaceholderExpansion;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.service.MessageService;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import java.nio.file.Path;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class EmakiAttributePlugin extends JavaPlugin {

    private static final String STARTUP_ASCII = """
 ______     __    __     ______     __  __     __     ______     ______   ______   ______     __     ______     __  __     ______   ______
/\\  ___\\   /\\ "-./  \\   /\\  __ \\   /\\ \\/ /    /\\ \\   /\\  __ \\   /\\__  _\\ /\\__  _\\ /\\  == \\   /\\ \\   /\\  == \\   /\\ \\/\\ \\   /\\__  _\\ /\\  ___\\
\\ \\  __\\   \\ \\ \\-./\\ \\  \\ \\  __ \\  \\ \\  _"-.  \\ \\ \\  \\ \\  __ \\  \\/_/\\ \\/ \\/_/\\ \\/ \\ \\  __<   \\ \\ \\  \\ \\  __<   \\ \\ \\_\\ \\  \\/_/\\ \\/ \\ \\  __\\
 \\ \\_____\\  \\ \\_\\ \\ \\_\\  \\ \\_\\ \\_\\  \\ \\_\\ \\_\\  \\ \\_\\  \\ \\_\\ \\_\\    \\ \\_\\    \\ \\_\\  \\ \\_\\ \\_\\  \\ \\_\\  \\ \\_____\\  \\ \\_____\\    \\ \\_\\  \\ \\_____\\
  \\/_____/   \\/_/  \\/_/   \\/_/\\/_/   \\/_/\\/_/   \\/_/   \\/_/\\/_/     \\/_/     \\/_/   \\/_/ /_/   \\/_/   \\/_____/   \\/_____/     \\/_/   \\/_____/
""";

    private static EmakiAttributePlugin instance;

    private final AttributeLifecycleCoordinator lifecycleCoordinator = new AttributeLifecycleCoordinator();

    private AttributeConfig configModel = AttributeConfig.defaults();
    private AttributeRegistry attributeRegistry;
    private AttributeBalanceRegistry attributeBalanceRegistry;
    private DamageTypeRegistry damageTypeRegistry;
    private DefaultProfileRegistry defaultProfileRegistry;
    private LoreFormatRegistry loreFormatRegistry;
    private AttributePresetRegistry presetRegistry;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private AttributeService attributeService;
    private AttributeListener listener;
    private AttributeCommand command;
    private MythicBridge mythicBridge;
    private AttributePlaceholderExpansion placeholderExpansion;
    private BukkitTask regenTask;

    public static EmakiAttributePlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        applyRuntimeComponents(lifecycleCoordinator.initialize(this));
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        reloadPluginState(true);
        lifecycleCoordinator.registerCommand(this);
        lifecycleCoordinator.registerListener(this);
        ensurePlaceholderExpansion();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        lifecycleCoordinator.shutdown(this, regenTask);
        regenTask = null;
        if (instance == this) {
            instance = null;
        }
    }

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    public void ensureMythicBridge() {
        if (mythicBridge != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs")) {
            return;
        }
        mythicBridge = new MythicBridge(this, attributeService);
        getServer().getPluginManager().registerEvents(mythicBridge, this);
    }

    public void ensurePlaceholderExpansion() {
        if (placeholderExpansion != null) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        placeholderExpansion = new AttributePlaceholderExpansion(this, attributeService);
        placeholderExpansion.register();
    }

    public void reloadPluginState(boolean resyncPlayers) {
        regenTask = lifecycleCoordinator.reload(this, regenTask, resyncPlayers);
    }

    private void applyRuntimeComponents(AttributeRuntimeComponents components) {
        attributeRegistry = components.attributeRegistry();
        attributeBalanceRegistry = components.attributeBalanceRegistry();
        damageTypeRegistry = components.damageTypeRegistry();
        defaultProfileRegistry = components.defaultProfileRegistry();
        loreFormatRegistry = components.loreFormatRegistry();
        presetRegistry = components.presetRegistry();
        languageLoader = components.languageLoader();
        messageService = components.messageService();
        attributeService = components.attributeService();
        listener = components.listener();
        command = components.command();
        mythicBridge = components.mythicBridge();
    }

    void setConfigModel(AttributeConfig configModel) {
        this.configModel = configModel == null ? AttributeConfig.defaults() : configModel;
    }

    void setPlaceholderExpansion(AttributePlaceholderExpansion placeholderExpansion) {
        this.placeholderExpansion = placeholderExpansion;
    }

    public AttributeConfig configModel() {
        return configModel;
    }

    public AttributeRegistry attributeRegistry() {
        return attributeRegistry;
    }

    public AttributeBalanceRegistry attributeBalanceRegistry() {
        return attributeBalanceRegistry;
    }

    public DamageTypeRegistry damageTypeRegistry() {
        return damageTypeRegistry;
    }

    public DefaultProfileRegistry defaultProfileRegistry() {
        return defaultProfileRegistry;
    }

    public LoreFormatRegistry loreFormatRegistry() {
        return loreFormatRegistry;
    }

    public AttributePresetRegistry presetRegistry() {
        return presetRegistry;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
    }

    public MessageService messageService() {
        return messageService;
    }

    public AttributeService attributeService() {
        return attributeService;
    }

    public AttributeListener listener() {
        return listener;
    }

    public AttributeCommand command() {
        return command;
    }

    public MythicBridge mythicBridge() {
        return mythicBridge;
    }

    public AttributePlaceholderExpansion placeholderExpansion() {
        return placeholderExpansion;
    }
}
