package emaki.jiuwu.craft.structure.config;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;
import emaki.jiuwu.craft.structure.EmakiStructurePlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class StructureConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    public static final String MODULE = "structure";

    private static final Set<String> KNOWN_TOP_LEVEL = Set.of(
            "version", "language", "generate", "cache", "selection", "template", "spawn",
            "detection", "cooldown");

    private final EmakiStructurePlugin plugin;

    public StructureConfigPrecheckContributor(EmakiStructurePlugin plugin) {
        super(MODULE, plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        var loader = plugin.appConfigLoader();
        if (loader == null || !loader.file().isFile()) {
            issues.add(ConfigPrecheckIssue.of(MODULE, "config.yml",
                    ConfigPrecheckSeverity.WARN, "配置文件不存在，已使用内置默认值"));
            return new ConfigPrecheckResult(module(), List.copyOf(issues));
        }
        addUnknownTopLevelKeys(loader.file(), issues);
        if (issues.isEmpty()) {
            issues.add(ConfigPrecheckIssue.of(MODULE, "config.yml",
                    ConfigPrecheckSeverity.INFO, "配置检查通过"));
        }
        return new ConfigPrecheckResult(module(), List.copyOf(issues));
    }

    private void addUnknownTopLevelKeys(File file, List<ConfigPrecheckIssue> issues) {
        try {
            var section = YamlFiles.load(file);
            for (String key : section.getKeys(false)) {
                if (!KNOWN_TOP_LEVEL.contains(key)) {
                    issues.add(ConfigPrecheckIssue.of(MODULE, "config.yml." + key,
                            ConfigPrecheckSeverity.WARN,
                            "未知顶层配置键 " + key + "，请检查拼写"));
                }
            }
        } catch (RuntimeException exception) {
            issues.add(ConfigPrecheckIssue.of(MODULE, "config.yml",
                    ConfigPrecheckSeverity.ERROR,
                    "配置文件解析失败: " + exception.getMessage()));
        }
    }
}
