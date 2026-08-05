package emaki.jiuwu.craft.accessory.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.accessory.EmakiAccessoryPlugin;
import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;

/**
 * Reports EmakiAccessory configuration problems into CoreLib's precheck report.
 *
 * <p>Covers the four surfaces an administrator can break independently: the main config, the parts
 * file, the GUI template directory and the set directory. Loader issues are forwarded rather than
 * re-derived, so the report says the same thing the startup log did.
 */
public final class AccessoryConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiAccessoryPlugin plugin;

    /**
     * Creates the contributor.
     *
     * @param plugin the owning plugin
     */
    public AccessoryConfigPrecheckContributor(EmakiAccessoryPlugin plugin) {
        super("accessory", plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);
        checkFile(new File(plugin.getDataFolder(), "parts.yml"), "parts.yml", issues);
        checkDirectory(new File(plugin.getDataFolder(), "gui"), "gui", issues);
        checkDirectory(new File(plugin.getDataFolder(), "sets"), "sets", issues);
        addLoaderIssues("parts.yml",
                plugin.partLoader() == null ? null : plugin.partLoader().issues(), issues);
        addLoaderIssues("gui",
                plugin.guiTemplateLoader() == null ? null : plugin.guiTemplateLoader().issues(), issues);
        addLoaderIssues("gui",
                plugin.accessoryGuiService() == null ? null : plugin.accessoryGuiService().issues(), issues);
        addLoaderIssues("sets",
                plugin.setLoader() == null ? null : plugin.setLoader().issues(), issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}
