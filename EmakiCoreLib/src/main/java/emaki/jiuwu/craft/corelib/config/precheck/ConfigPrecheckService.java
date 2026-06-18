package emaki.jiuwu.craft.corelib.config.precheck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ConfigPrecheckService {

    private final ConfigPrecheckRegistry registry = new ConfigPrecheckRegistry();
    private ConfigPrecheckReport lastReport = ConfigPrecheckReport.empty();
    private ActionRegistry actionRegistry;
    private ActionTemplateRegistry templateRegistry;

    public ConfigPrecheckService() {
        registry.register(new CoreLibConfigPrecheckContributor());
    }

    public void configure(ActionRegistry actionRegistry, ActionTemplateRegistry templateRegistry) {
        this.actionRegistry = actionRegistry;
        this.templateRegistry = templateRegistry;
    }

    public ConfigPrecheckRegistry registry() {
        return registry;
    }

    public ConfigPrecheckReport checkAll(CoreLibConfig config) {
        return run(config, registry.all());
    }

    public ConfigPrecheckReport checkModule(CoreLibConfig config, String module) {
        ConfigPrecheckContributor contributor = registry.get(module);
        if (contributor == null) {
            ConfigPrecheckIssue issue = ConfigPrecheckIssue.of(
                    Texts.isBlank(module) ? "unknown" : module,
                    "",
                    ConfigPrecheckSeverity.ERROR,
                    "Unknown config precheck module. Available modules: " + String.join(", ", registry.moduleIds())
            );
            lastReport = new ConfigPrecheckReport(Instant.now(), List.of(new ConfigPrecheckResult(module, List.of(issue))));
            return lastReport;
        }
        return run(config, List.of(contributor));
    }

    public ConfigPrecheckReport lastReport() {
        return lastReport;
    }

    private ConfigPrecheckReport run(CoreLibConfig config, List<ConfigPrecheckContributor> contributors) {
        ConfigPrecheckContext context = new ConfigPrecheckContext(new ActionLineParser(), actionRegistry, templateRegistry);
        List<ConfigPrecheckResult> results = new ArrayList<>();
        for (ConfigPrecheckContributor contributor : contributors) {
            try {
                results.add(contributor.check(config == null ? CoreLibConfig.defaults() : config, context));
            } catch (RuntimeException exception) {
                ConfigPrecheckIssue issue = ConfigPrecheckIssue.of(
                        contributor.module(),
                        "",
                        ConfigPrecheckSeverity.ERROR,
                        "Precheck contributor failed: " + exception.getMessage()
                );
                results.add(new ConfigPrecheckResult(contributor.module(), List.of(issue)));
            }
        }
        lastReport = new ConfigPrecheckReport(Instant.now(), results);
        return lastReport;
    }
}
