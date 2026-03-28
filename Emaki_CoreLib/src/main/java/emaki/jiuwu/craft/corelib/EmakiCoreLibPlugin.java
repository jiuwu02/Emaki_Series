package emaki.jiuwu.craft.corelib;

import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.action.ActionExecutor;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.action.builtin.BuiltinActions;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.placeholder.ActionInlineTokenResolver;
import emaki.jiuwu.craft.corelib.placeholder.ActionContextPlaceholderResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderApiResolver;
import emaki.jiuwu.craft.corelib.placeholder.PlaceholderRegistry;
import emaki.jiuwu.craft.corelib.pdc.PdcService;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.bukkit.plugin.java.JavaPlugin;

public final class EmakiCoreLibPlugin extends JavaPlugin implements LogMessagesProvider {

    private static final String STARTUP_ASCII = """
 ______     __    __     ______     __  __     __     ______     ______     ______     ______     __         __     ______
/\\  ___\\   /\\ "-./  \\   /\\  __ \\   /\\ \\/ /    /\\ \\   /\\  ___\\   /\\  __ \\   /\\  == \\   /\\  ___\\   /\\ \\       /\\ \\   /\\  == \\
\\ \\  __\\   \\ \\ \\-./\\ \\  \\ \\  __ \\  \\ \\  _"-.  \\ \\ \\  \\ \\ \\____  \\ \\ \\/\\ \\  \\ \\  __<   \\ \\  __\\   \\ \\ \\____  \\ \\ \\  \\ \\  __<
 \\ \\_____\\  \\ \\_\\ \\ \\_\\  \\ \\_\\ \\_\\  \\ \\_\\ \\_\\  \\ \\_\\  \\ \\_____\\  \\ \\_____\\  \\ \\_\\ \\_\\  \\ \\_____\\  \\ \\_____\\  \\ \\_\\  \\ \\_____\\
  \\/_____/   \\/_/  \\/_/   \\/_/\\/_/   \\/_/\\/_/   \\/_/   \\/_____/   \\/_____/   \\/_/ /_/   \\/_____/   \\/_____/   \\/_/   \\/_____/
""";

    private static EmakiCoreLibPlugin instance;
    private LanguageLoader languageLoader;
    private MessageService messageService;
    private CoreLibConfig configModel = CoreLibConfig.defaults();
    private ActionRegistry actionRegistry;
    private ActionTemplateRegistry actionTemplateRegistry;
    private PlaceholderRegistry placeholderRegistry;
    private EconomyManager economyManager;
    private ActionExecutor actionExecutor;
    private final PdcService pdcService = new PdcService("emaki_corelib");

    public static EmakiCoreLibPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        initializeServices();
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        messageService.info("console.plugin_starting");
        migrateLegacyBundledFiles();
        ensureBundledFile("config.yml");
        reloadActionSystem();
        messageService.info("console.plugin_started");
    }

    @Override
    public void onDisable() {
        if (messageService != null) {
            messageService.info("console.plugin_stopped");
        }
        if (instance == this) {
            instance = null;
        }
    }

    @Override
    public MessageService messageService() {
        return messageService;
    }

    public LanguageLoader languageLoader() {
        return languageLoader;
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

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    private void initializeServices() {
        languageLoader = new LanguageLoader(this);
        messageService = new MessageService(this, languageLoader);
        languageLoader.load();
    }

    private void ensureBundledFile(String relativePath) {
        File target = new File(getDataFolder(), relativePath);
        try {
            boolean copied = YamlFiles.copyResourceIfMissing(this, relativePath, target);
            if (!copied && !target.exists()) {
                messageService.warning("loader.bundled_resource_missing", java.util.Map.of(
                    "type", "资源",
                    "path", target.getPath(),
                    "resource", relativePath
                ));
            }
        } catch (Exception exception) {
            messageService.warning("loader.bundled_resource_write_failed", java.util.Map.of(
                "path", target.getPath(),
                "error", String.valueOf(exception.getMessage())
            ));
        }
    }

    private CoreLibConfig loadConfigModel() {
        try {
            File file = new File(getDataFolder(), "config.yml");
            return CoreLibConfig.fromConfig(YamlFiles.load(file));
        } catch (Exception exception) {
            messageService.warning("console.action_config_load_failed", java.util.Map.of(
                "error", String.valueOf(exception.getMessage())
            ));
            return CoreLibConfig.defaults();
        }
    }

    private void migrateLegacyBundledFiles() {
        Path legacyRoot = getDataFolder().toPath().resolve("defaults");
        if (!Files.exists(legacyRoot) || !Files.isDirectory(legacyRoot)) {
            return;
        }
        try (var paths = Files.walk(legacyRoot)) {
            paths.forEach(source -> {
                try {
                    Path relative = legacyRoot.relativize(source);
                    if (relative.toString().isBlank()) {
                        return;
                    }
                    Path target = getDataFolder().toPath().resolve(relative);
                    if (Files.isDirectory(source)) {
                        YamlFiles.ensureDirectory(target);
                        return;
                    }
                    if (Files.exists(target)) {
                        return;
                    }
                    YamlFiles.ensureDirectory(target.getParent());
                    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException exception) {
                    messageService.warning("loader.legacy_resource_migrate_failed", java.util.Map.of(
                        "path", source.toString(),
                        "error", String.valueOf(exception.getMessage())
                    ));
                }
            });
        } catch (IOException exception) {
            messageService.warning("loader.legacy_resource_scan_failed", java.util.Map.of(
                "path", legacyRoot.toString(),
                "error", String.valueOf(exception.getMessage())
            ));
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

    public PdcService pdcService() {
        return pdcService;
    }
}
