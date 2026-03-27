package emaki.jiuwu.craft.attribute;

import emaki.jiuwu.craft.attribute.command.AttributeCommand;
import emaki.jiuwu.craft.attribute.config.AttributeConfig;
import emaki.jiuwu.craft.attribute.loader.AttributePresetRegistry;
import emaki.jiuwu.craft.attribute.loader.AttributeRegistry;
import emaki.jiuwu.craft.attribute.loader.DamageTypeRegistry;
import emaki.jiuwu.craft.attribute.loader.DefaultProfileRegistry;
import emaki.jiuwu.craft.attribute.loader.LoreFormatRegistry;
import emaki.jiuwu.craft.attribute.service.AttributeService;
import emaki.jiuwu.craft.attribute.listener.AttributeListener;
import emaki.jiuwu.craft.attribute.bridge.MythicBridge;
import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.text.ConsoleOutputs;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class EmakiAttributePlugin extends JavaPlugin {

    private static final String STARTUP_ASCII = """
  _____                           _   _      _   _        _   _
 | ____|_ __ ___  _ __ ___   __ _| |_| |__  | |_| |__    / \\ | |_
 |  _| | '_ ` _ \\| '_ ` _ \\ / _` | __| '_ \\ | __| '_ \\  / _ \\| __|
 | |___| | | | | | | | | | | (_| | |_| | | || |_| | | |/ ___ \\ |_
 |_____|_| |_| |_|_| |_| |_|\\__,_|\\__|_| |_| \\__|_| |_/_/   \\_\\__|
""";

    private static final List<String> BUNDLED_DEFAULTS = List.of(
        "defaults/config.yml",
        "defaults/global.yml",
        "defaults/attributes/health.yml",
        "defaults/attributes/mana.yml",
        "defaults/attributes/physical_attack.yml",
        "defaults/attributes/physical_defense.yml",
        "defaults/attributes/physical_crit_rate.yml",
        "defaults/attributes/physical_crit_damage.yml",
        "defaults/attributes/physical_damage_bonus.yml",
        "defaults/attributes/projectile_attack.yml",
        "defaults/attributes/projectile_damage_bonus.yml",
        "defaults/attributes/projectile_crit_rate.yml",
        "defaults/attributes/projectile_crit_damage.yml",
        "defaults/attributes/projectile_defense.yml",
        "defaults/attributes/spell_attack.yml",
        "defaults/attributes/spell_damage_bonus.yml",
        "defaults/attributes/spell_crit_rate.yml",
        "defaults/attributes/spell_crit_damage.yml",
        "defaults/attributes/spell_defense.yml",
        "defaults/attributes/health_regen.yml",
        "defaults/attributes/mana_regen.yml",
        "defaults/attributes/skill_damage_bonus.yml",
        "defaults/attributes/skill_crit_rate.yml",
        "defaults/attributes/skill_crit_damage.yml",
        "defaults/attributes/skill_cdr.yml",
        "defaults/damage_types/physical.yml",
        "defaults/damage_types/projectile.yml",
        "defaults/damage_types/spell.yml",
        "defaults/damage_types/skill.yml",
        "defaults/lore_formats/default_flat.yml",
        "defaults/lore_formats/default_percent.yml",
        "defaults/lore_formats/default_regen.yml",
        "defaults/lore_formats/default_resource.yml"
    );

    private static EmakiAttributePlugin instance;

    private AttributeConfig configModel = AttributeConfig.defaults();
    private AttributeRegistry attributeRegistry;
    private DamageTypeRegistry damageTypeRegistry;
    private DefaultProfileRegistry defaultProfileRegistry;
    private LoreFormatRegistry loreFormatRegistry;
    private AttributePresetRegistry presetRegistry;
    private AttributeService attributeService;
    private AttributeListener listener;
    private AttributeCommand command;
    private MythicBridge mythicBridge;
    private BukkitTask regenTask;

    public static EmakiAttributePlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        ConsoleOutputs.sendGradientAscii(this, STARTUP_ASCII);
        ensureBundledDefaults();
        initializeServices();
        reloadPluginState(true);
        registerCommand();
        registerListeners();
        getLogger().info("Emaki_Attribute 已启用.");
    }

    @Override
    public void onDisable() {
        cancelRegenTask();
        if (instance == this) {
            instance = null;
        }
        getLogger().info("Emaki_Attribute 已关闭.");
    }

    public Path dataPath(String first, String... more) {
        return getDataFolder().toPath().resolve(Path.of(first, more));
    }

    public AttributeConfig configModel() {
        return configModel;
    }

    public AttributeRegistry attributeRegistry() {
        return attributeRegistry;
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

    public AttributeService attributeService() {
        return attributeService;
    }

    public MythicBridge mythicBridge() {
        return mythicBridge;
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

    public void reloadPluginState(boolean resyncPlayers) {
        configModel = loadConfigModel();
        if (attributeService != null) {
            attributeService.reloadConfig(configModel);
        }
        if (attributeRegistry != null) {
            attributeRegistry.load();
        }
        if (damageTypeRegistry != null) {
            damageTypeRegistry.load();
        }
        if (defaultProfileRegistry != null) {
            defaultProfileRegistry.load();
        }
        if (loreFormatRegistry != null) {
            loreFormatRegistry.load();
        }
        if (presetRegistry != null) {
            presetRegistry.load();
        }
        ensureMythicBridge();
        if (attributeService != null && resyncPlayers) {
            attributeService.resyncAllPlayers();
        }
        scheduleRegenTask();
    }

    private void initializeServices() {
        EmakiCoreLibPlugin coreLibPlugin = EmakiCoreLibPlugin.getInstance();
        if (coreLibPlugin == null) {
            throw new IllegalStateException("Emaki_CoreLib must be enabled before Emaki_Attribute.");
        }
        attributeRegistry = new AttributeRegistry(this);
        damageTypeRegistry = new DamageTypeRegistry(this);
        defaultProfileRegistry = new DefaultProfileRegistry(this);
        loreFormatRegistry = new LoreFormatRegistry(this);
        presetRegistry = new AttributePresetRegistry(this);
        attributeService = new AttributeService(
            this,
            coreLibPlugin.pdcService(),
            configModel,
            attributeRegistry,
            damageTypeRegistry,
            defaultProfileRegistry,
            loreFormatRegistry,
            presetRegistry
        );
        listener = new AttributeListener(this, attributeService);
        mythicBridge = Bukkit.getPluginManager().isPluginEnabled("MythicMobs") ? new MythicBridge(this, attributeService) : null;
        command = new AttributeCommand(this, attributeService);
    }

    private void registerCommand() {
        PluginCommand pluginCommand = getCommand("emakiattribute");
        if (pluginCommand == null || command == null) {
            return;
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
    }

    private void registerListeners() {
        if (listener != null) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
        if (mythicBridge != null) {
            getServer().getPluginManager().registerEvents(mythicBridge, this);
        }
    }

    private void scheduleRegenTask() {
        cancelRegenTask();
        if (attributeService == null) {
            return;
        }
        int intervalTicks = Math.max(1, configModel.regenIntervalTicks());
        regenTask = getServer().getScheduler().runTaskTimer(
            this,
            attributeService::regenerateOnlinePlayers,
            intervalTicks,
            intervalTicks
        );
    }

    private void cancelRegenTask() {
        if (regenTask != null) {
            regenTask.cancel();
            regenTask = null;
        }
    }

    private void ensureBundledDefaults() {
        for (String relativePath : BUNDLED_DEFAULTS) {
            ensureBundledFile(relativePath);
        }
    }

    private void ensureBundledFile(String relativePath) {
        File target = new File(getDataFolder(), relativePath);
        if (target.exists()) {
            return;
        }
        try {
            saveResource(relativePath, false);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("无法写入默认资源 " + relativePath + ": " + exception.getMessage());
        }
    }

    private AttributeConfig loadConfigModel() {
        try {
            File file = new File(getDataFolder(), "defaults/config.yml");
            return AttributeConfig.fromConfig(YamlConfiguration.loadConfiguration(file));
        } catch (Exception exception) {
            getLogger().warning("加载属性配置失败: " + exception.getMessage());
            return AttributeConfig.defaults();
        }
    }
}
