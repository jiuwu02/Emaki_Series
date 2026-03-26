package emaki.jiuwu.craft.corelib;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.action.builtin.BuiltinActions;
import emaki.jiuwu.craft.corelib.placeholder.ActionInlineTokenResolver;
import emaki.jiuwu.craft.corelib.placeholder.ActionContextPlaceholderResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderApiResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmakiCoreLibPlugin extends JavaPlugin {

    private static final String STARTUP_ASCII = """
 ______     __    __     ______     __  __     __     ______     ______     ______     ______     __         __     ______
/\\  ___\\   /\\ "-./  \\   /\\  __ \\   /\\ \\/ /    /\\ \\   /\\  ___\\   /\\  __ \\   /\\  == \\   /\\  ___\\   /\\ \\       /\\ \\   /\\  == \\
\\ \\  __\\   \\ \\ \\-./\\ \\  \\ \\  __ \\  \\ \\  _"-.  \\ \\ \\  \\ \\ \\____  \\ \\ \\/\\ \\  \\ \\  __<   \\ \\  __\\   \\ \\ \\____  \\ \\ \\  \\ \\  __<
 \\ \\_____\\  \\ \\_\\ \\ \\_\\  \\ \\_\\ \\_\\  \\ \\_\\ \\_\\  \\ \\_\\  \\ \\_____\\  \\ \\_____\\  \\ \\_\\ \\_\\  \\ \\_____\\  \\ \\_____\\  \\ \\_\\  \\ \\_____\\
  \\/_____/   \\/_/  \\/_/   \\/_/\\/_/   \\/_/\\/_/   \\/_/   \\/_____/   \\/_____/   \\/_/ /_/   \\/_____/   \\/_____/   \\/_/   \\/_____/
""";

    private static EmakiCoreLibPlugin instance;
    private CoreLibConfig configModel = CoreLibConfig.defaults();
    private ActionRegistry actionRegistry;
    private ActionTemplateRegistry actionTemplateRegistry;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private ActionExecutor actionExecutor;

    public static EmakiCoreLibPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        ensureBundledFile("defaults/config.yml");
        ensureBundledFile("defaults/lang/zh_CN.yml");
        reloadActionSystem();
        getLogger().info("核心库已启用.");
    }

    @Override
    public void onDisable() {
        getLogger().info("核心库已关闭.");
        if (instance == this) {
            instance = null;
        }
    }

    public void reloadActionSystem() {
        configModel = loadConfigModel();
        actionRegistry = new ActionRegistry();
        actionTemplateRegistry = new ActionTemplateRegistry();
        placeholderRegistry = new PlaceholderRegistry();
        economyManager = new EconomyManager(this);
        placeholderRegistry.register(new ActionContextPlaceholderResolver());
        placeholderRegistry.register(new ActionInlineTokenResolver());
        placeholderRegistry.register(new PlaceholderApiResolver());
        for (var entry : configModel.actionTemplates().entrySet()) {
            actionTemplateRegistry.register(entry.getKey(), entry.getValue());
        }
        BuiltinActions.registerAll(actionRegistry, economyManager);
        actionExecutor = new ActionExecutor(this, actionRegistry, new ActionLineParser(), placeholderRegistry, actionTemplateRegistry);
    }

    private void ensureBundledFile(String relativePath) {
        File target = new File(getDataFolder(), relativePath);
        if (target.exists()) {
            return;
        }
        saveResource(relativePath, false);
    }

    private CoreLibConfig loadConfigModel() {
        try {
            File file = new File(getDataFolder(), "defaults/config.yml");
            return CoreLibConfig.fromConfig(YamlConfiguration.loadConfiguration(file));
        } catch (Exception exception) {
            getLogger().warning("Failed to load CoreLib action config: " + exception.getMessage());
            return CoreLibConfig.defaults();
        }
    }

    public CoreLibConfig configModel() {
        return configModel;
    }

    public ActionRegistry actionRegistry() {
        return actionRegistry;
    }

    public ActionTemplateRegistry actionTemplateRegistry() {
        return actionTemplateRegistry;
    }

    public PlaceholderRegistry placeholderRegistry() {
        return placeholderRegistry;
    }

    public EconomyManager economyManager() {
        return economyManager;
    }

    public ActionExecutor actionExecutor() {
        return actionExecutor;
    }
}
