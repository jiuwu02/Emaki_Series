package emaki.jiuwu.craft.codex.config;

import static emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity.INFO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.config.precheck.AbstractModuleConfigPrecheckContributor;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckContext;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckIssue;
import emaki.jiuwu.craft.corelib.config.precheck.ConfigPrecheckResult;

public final class CodexConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private final EmakiCodexPlugin plugin;

    public CodexConfigPrecheckContributor(EmakiCodexPlugin plugin) {
        super("codex", plugin::messageService);
        this.plugin = plugin;
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkFile(new File(plugin.getDataFolder(), "config.yml"), "config.yml", issues);

        checkDirectory(new File(plugin.getDataFolder(), "advancements"), "advancements", issues);
        addLoaderIssues("advancements",
                plugin.advancementPageLoader() == null ? null : plugin.advancementPageLoader().issues(), issues);
        checkDirectory(new File(plugin.getDataFolder(), "codex"), "codex", issues);
        addLoaderIssues("codex",
                plugin.codexCategoryLoader() == null ? null : plugin.codexCategoryLoader().issues(), issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }
}
