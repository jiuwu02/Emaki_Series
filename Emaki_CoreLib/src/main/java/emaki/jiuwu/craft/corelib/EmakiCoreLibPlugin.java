package emaki.jiuwu.craft.corelib;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.operation.OperationExecutor;
import emaki.jiuwu.craft.corelib.operation.OperationLineParser;
import emaki.jiuwu.craft.corelib.operation.OperationRegistry;
import emaki.jiuwu.craft.corelib.operation.OperationTemplateRegistry;
import emaki.jiuwu.craft.corelib.operation.builtin.BuiltinOperations;
import emaki.jiuwu.craft.corelib.placeholder.OperationContextPlaceholderResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderApiResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmakiCoreLibPlugin extends JavaPlugin {

    private static EmakiCoreLibPlugin instance;
    private CoreLibConfig configModel = CoreLibConfig.defaults();
    private OperationRegistry operationRegistry;
    private OperationTemplateRegistry operationTemplateRegistry;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private OperationExecutor operationExecutor;

    public static EmakiCoreLibPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        ensureBundledFile("defaults/config.yml");
        ensureBundledFile("defaults/lang/zh_CN.yml");
        reloadOperationSystem();
        getLogger().info("Emaki_CoreLib enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Emaki_CoreLib disabled.");
        if (instance == this) {
            instance = null;
        }
    }

    public void reloadOperationSystem() {
        configModel = loadConfigModel();
        operationRegistry = new OperationRegistry();
        operationTemplateRegistry = new OperationTemplateRegistry();
        placeholderRegistry = new PlaceholderRegistry();
        economyManager = new EconomyManager(this);
        placeholderRegistry.register(new OperationContextPlaceholderResolver());
        placeholderRegistry.register(new PlaceholderApiResolver());
        for (var entry : configModel.operationTemplates().entrySet()) {
            operationTemplateRegistry.register(entry.getKey(), entry.getValue());
        }
        BuiltinOperations.registerAll(operationRegistry, economyManager);
        operationExecutor = new OperationExecutor(this, operationRegistry, new OperationLineParser(), placeholderRegistry, operationTemplateRegistry);
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
            getLogger().warning("Failed to load CoreLib operation config: " + exception.getMessage());
            return CoreLibConfig.defaults();
        }
    }

    public CoreLibConfig configModel() {
        return configModel;
    }

    public OperationRegistry operationRegistry() {
        return operationRegistry;
    }

    public OperationTemplateRegistry operationTemplateRegistry() {
        return operationTemplateRegistry;
    }

    public PlaceholderRegistry placeholderRegistry() {
        return placeholderRegistry;
    }

    public EconomyManager economyManager() {
        return economyManager;
    }

    public OperationExecutor operationExecutor() {
        return operationExecutor;
    }
}
