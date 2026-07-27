package emaki.jiuwu.craft.corelib.config.precheck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.ActionLineParser;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionTemplateRegistry;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class ConfigPrecheckService {

    private static final String MESSAGE_PREFIX = "console.config_precheck.messages.";

    private final ConfigPrecheckRegistry registry = new ConfigPrecheckRegistry();
    private final Supplier<? extends LogMessages> messagesSupplier;
    private ConfigPrecheckReport lastReport = ConfigPrecheckReport.empty();
    private ActionRegistry actionRegistry;
    private ActionTemplateRegistry templateRegistry;

    public ConfigPrecheckService() {
        this(() -> null);
    }

    public ConfigPrecheckService(LogMessages messages) {
        this(() -> messages);
    }

    private ConfigPrecheckService(Supplier<? extends LogMessages> messagesSupplier) {
        this.messagesSupplier = messagesSupplier == null ? () -> null : messagesSupplier;
        registry.register(new CoreLibConfigPrecheckContributor(this.messagesSupplier));
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
            String moduleId = Texts.isBlank(module) ? "unknown" : Texts.lower(module);
            ConfigPrecheckIssue issue = ConfigPrecheckIssue.of(
                    moduleId,
                    "",
                    ConfigPrecheckSeverity.ERROR,
                    message("unknown_module", Map.of("modules", String.join(", ", registry.moduleIds())))
            );
            lastReport = new ConfigPrecheckReport(
                    Instant.now(),
                    List.of(new ConfigPrecheckResult(moduleId, List.of(issue)))
            );
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
                String error = Texts.toStringSafe(exception.getMessage());
                if (Texts.isBlank(error)) {
                    error = exception.getClass().getSimpleName();
                }
                ConfigPrecheckIssue issue = ConfigPrecheckIssue.of(
                        contributor.module(),
                        "",
                        ConfigPrecheckSeverity.ERROR,
                        message("contributor_failed", Map.of("error", error))
                );
                results.add(new ConfigPrecheckResult(contributor.module(), List.of(issue)));
            }
        }
        lastReport = new ConfigPrecheckReport(Instant.now(), results);
        return lastReport;
    }

    private String message(String key, Map<String, ?> replacements) {
        String messageKey = MESSAGE_PREFIX + Texts.toStringSafe(key);
        LogMessages messages = messagesSupplier.get();
        if (messages == null) {
            return messageKey;
        }
        return messages.message(messageKey, replacements == null ? Map.of() : replacements);
    }
}
