package emaki.jiuwu.craft.corelib.config.precheck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.pipeline.registry.StageRegistry;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;

public final class ConfigPrecheckService {

    private static final String MESSAGE_PREFIX = "console.config_precheck.messages.";

    private final ConfigPrecheckRegistry registry = new ConfigPrecheckRegistry();
    private final Supplier<? extends LogMessages> messagesSupplier;
    private ConfigPrecheckReport lastReport = ConfigPrecheckReport.empty();
    private StageRegistry stageRegistry;

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

    /**
     * Binds the stage table pipeline checks validate against.
     *
     * <p>Called with the freshly built candidate registry before it is installed, which is what lets a
     * reload reject a configuration without having replaced the live stage table first.</p>
     *
     * @param stageRegistry the stage table
     */
    public void configure(StageRegistry stageRegistry) {
        this.stageRegistry = stageRegistry;
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
        CoreLibConfig safeConfig = config == null ? CoreLibConfig.defaults() : config;
        // Built from the config being checked rather than the live one: a reload prechecks its candidate, so
        // a sequence added in that candidate has to be visible to `run` validation right now.
        ConfigPrecheckContext context = ConfigPrecheckContext.of(stageRegistry,
                safeConfig.actionTemplates(),
                safeConfig.pipelineConfig() == null ? null : safeConfig.pipelineConfig().toLimits());
        List<ConfigPrecheckResult> results = new ArrayList<>();
        for (ConfigPrecheckContributor contributor : contributors) {
            try {
                results.add(contributor.check(safeConfig, context));
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
