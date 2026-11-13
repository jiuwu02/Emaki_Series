package emaki.jiuwu.craft.kits;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.text.ConsoleOutputs;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.kits.api.EmakiKitsApi;
import emaki.jiuwu.craft.kits.apiimpl.DefaultEmakiKitsApi;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.schedule.cron.CronScheduler;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.kits.command.KitCommandAdapter;
import emaki.jiuwu.craft.kits.command.KitCommandRouter;
import emaki.jiuwu.craft.kits.config.AppConfig;
import emaki.jiuwu.craft.kits.config.KitsConfigLoader;
import emaki.jiuwu.craft.kits.gui.ChoiceGuiService;
import emaki.jiuwu.craft.kits.join.InviteCodeService;
import emaki.jiuwu.craft.kits.listener.JoinListener;
import emaki.jiuwu.craft.kits.model.KitDefinition;
import emaki.jiuwu.craft.kits.reward.EconomyPort;
import emaki.jiuwu.craft.kits.reward.RewardDispatcher;
import emaki.jiuwu.craft.kits.service.KitService;
import emaki.jiuwu.craft.kits.store.KitStore;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmakiKitsPlugin extends JavaPlugin {

    private static final String ROOT_COMMAND = "emakikits";
    private static final String DEFAULT_PREFIX =
            "<gray>[ <gradient:#F59E0B:#F97316>EmakiKits</gradient> ]</gray> ";
    private static final int STARTUP_ASCII_START_COLOR = 0xF59E0B;
    private static final int STARTUP_ASCII_END_COLOR = 0xF97316;
    private static final List<String> VERSIONED_FILES = List.of("config.yml");
    private static final List<String> STATIC_FILES = List.of("lang/zh_CN.yml", "lang/en_US.yml");
    private static final List<String> DEFAULT_DATA_FILES = List.of("kits.yml", "gui/choice.yml");
    private static final List<String> EXTRA_DIRECTORIES = List.of("data", "gui");

    private static final String STARTUP_ASCII = """
 ______  __    __  ______  __  __   __  __  __   __  ______  ______
/\\  ___\\/\\ "-./  \\/\\  __ \\/\\ \\/ /  /\\ \\/\\ \\/ /  /\\ \\/\\__  _\\/\\  ___\\
\\ \\  __\\\\ \\ \\-./\\ \\ \\  __ \\ \\  _"-.\\ \\ \\ \\  _"-.\\ \\ \\/_/\\ \\/\\ \\___  \\
 \\ \\_____\\ \\_\\ \\ \\_\\ \\_\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\_\\ \\_\\\\ \\_\\ \\ \\_\\ \\/\\_____\\
  \\/_____/\\/_/  \\/_/\\/_/\\/_/\\/_/\\/_/ \\/_/\\/_/\\/_/ \\/_/  \\/_/  \\/_____/
""";

    private final AtomicBoolean shutdownStarted = new AtomicBoolean(false);
    private volatile boolean runtimeReady;

    private volatile AppConfig config = AppConfig.defaults();
    private KitStore store;
    private MessageService messages;
    private LanguageLoader languageLoader;
    private BootstrapService bootstrapService;
    private EconomyPort economy;
    private RewardDispatcher rewards;
    private KitService service;
    private KitCommandRouter router;
    private ChatInputService chatInput;
    private GuiService guiService;
    private GuiTemplateLoader guiTemplates;
    private ChoiceGuiService choiceGui;
    private InviteCodeService inviteCodeService;
    private CronScheduler cronScheduler;
    private JoinListener joinListener;
    private ThreadOwnership threadOwnership;
    private DefaultEmakiKitsApi apiBridge;
    private int flushTaskId = -1;

    @Override
    public void onEnable() {
        shutdownStarted.set(false);
        EmakiCoreLibPlugin coreLib = EmakiCoreLibApi.requireService(EmakiCoreLibPlugin.class);
        ExecutionDispatcher dispatcher = EmakiCoreLibApi.requireService(ExecutionDispatcher.class);
        threadOwnership = EmakiCoreLibApi.requireService(ThreadOwnership.class);

        languageLoader = new LanguageLoader(this, "lang", "lang", "zh_CN", "zh_CN");
        messages = new MessageService(this, languageLoader, DEFAULT_PREFIX, true);
        languageLoader.load();
        bootstrapService = new BootstrapService(this, messages, VERSIONED_FILES,
                STATIC_FILES, DEFAULT_DATA_FILES, EXTRA_DIRECTORIES, null);
        bootstrapService.bootstrap();

        store = new KitStore(getDataFolder(), getLogger());
        store.load();
        economy = new EconomyPort(coreLib);
        reloadContent();

        rewards = new RewardDispatcher(this, store, economy, () -> config, this::kit);
        service = new KitService(store, rewards, economy, () -> config, this::kit);
        chatInput = new ChatInputService(this, dispatcher);
        guiService = new GuiService(this, dispatcher, coreLib.asyncTaskScheduler(),
                coreLib.performanceMonitor(), coreLib.guiBackend());
        guiTemplates = new GuiTemplateLoader(this);
        guiTemplates.load();
        choiceGui = new ChoiceGuiService(this, guiService, guiTemplates, messages, service);
        inviteCodeService = new InviteCodeService(this, chatInput, messages, service,
                store, () -> config);
        cronScheduler = new CronScheduler();
        router = new KitCommandRouter(this, service, store, messages, () -> config,
                choiceGui, inviteCodeService);

        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII,
                STARTUP_ASCII_START_COLOR, STARTUP_ASCII_END_COLOR);
        registerCommand(ROOT_COMMAND, "EmakiKits 礼包命令",
                List.of("emakikit", "ekits"), new KitCommandAdapter(router));
        getServer().getPluginManager().registerEvents(guiService, this);
        getServer().getPluginManager().registerEvents(chatInput, this);
        joinListener = new JoinListener(service, inviteCodeService, messages, () -> config);
        getServer().getPluginManager().registerEvents(joinListener, this);
        scheduleTimeWindows();
        flushTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this,
                store::flush, 100L, Math.max(1, config.storage().saveDelayTicks()) * 20L).getTaskId();
        apiBridge = new DefaultEmakiKitsApi(this);
        EmakiKitsApi.install(apiBridge);
        runtimeReady = true;
        messages.info("console.plugin_started", Map.of("count", config.kits().size()));
    }

    @Override
    public void onDisable() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        runtimeReady = false;
        if (apiBridge != null) {
            EmakiKitsApi.uninstall(apiBridge);
        }
        if (flushTaskId != -1) {
            getServer().getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        if (cronScheduler != null) {
            cronScheduler.cancelAll();
        }
        if (chatInput != null) {
            chatInput.close();
        }
        if (choiceGui != null) {
            choiceGui.closeAll();
        }
        if (guiService != null) {
            guiService.closeAll();
        }
        HandlerList.unregisterAll(this);
        if (store != null) {
            store.seal();
        }
        messages.info("console.plugin_stopped");
    }

    public int reloadContent() {
        KitsConfigLoader loader = new KitsConfigLoader(getDataFolder());
        KitsConfigLoader.LoadResult result = loader.loadAll();
        config = result.config();
        if (languageLoader != null) {
            languageLoader.load();
        }
        for (AppConfig.ConfigIssue issue : result.issues()) {
            messages.warning("console.config_issue", Map.of(
                    "path", issue.path(), "reason", issue.reasonKey()));
        }
        if (guiTemplates != null) {
            guiTemplates.load();
        }
        if (cronScheduler != null) {
            cronScheduler.cancelAll();
            scheduleTimeWindows();
        }
        return config.kits().size();
    }

    private void scheduleTimeWindows() {
        for (KitDefinition def : config.kits().values()) {
            if (!def.hasTimeLimit()) {
                continue;
            }
            String mode = def.limit().mode();
            if (!"cron".equalsIgnoreCase(mode)) {
                continue;
            }
            try {
                cronScheduler.schedule(this, def.limit().cron(), () ->
                        messages.info("console.time_window_opened", Map.of("kit", def.id())));
            } catch (RuntimeException exception) {
                messages.warning("console.time_window_cron_failed", Map.of(
                        "kit", def.id(), "reason", String.valueOf(exception.getMessage())));
            }
        }
    }

    public KitDefinition kit(String id) {
        return config.kit(id);
    }

    public AppConfig appConfig() {
        return config;
    }

    public MessageService messageService() {
        return messages;
    }

    public KitService kitService() {
        return service;
    }

    public KitStore kitStore() {
        return store;
    }

    public ThreadOwnership threadOwnership() {
        return threadOwnership;
    }

    public boolean runtimeReady() {
        return runtimeReady;
    }
}
