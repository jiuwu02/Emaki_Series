package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import emaki.jiuwu.craft.corelib.CoreLibConfig;
import emaki.jiuwu.craft.corelib.action.pipeline.compile.CompileDiagnostic;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.config.precheck.ConfigPrecheckSeverity;

final class CoreLibConfigPrecheckContributor extends AbstractModuleConfigPrecheckContributor {

    private static final String UNKNOWN_STAGE = "action.validate.unknown_stage";

    private static final String UNKNOWN_SEQUENCE = "action.validate.unknown_sequence";

    CoreLibConfigPrecheckContributor(Supplier<? extends LogMessages> messagesSupplier) {
        super("corelib", messagesSupplier);
    }

    @Override
    public ConfigPrecheckResult check(CoreLibConfig config, ConfigPrecheckContext context) {
        CoreLibConfig safeConfig = config == null ? CoreLibConfig.defaults() : config;
        List<ConfigPrecheckIssue> issues = new ArrayList<>();
        checkLoopConfig(safeConfig.loopConfig(), issues);
        checkSequences(safeConfig.actionTemplates(), context, issues);
        if (issues.isEmpty()) {
            addMessageIssue("config.yml", ConfigPrecheckSeverity.INFO, "passed", issues);
        }
        return new ConfigPrecheckResult(module(), issues);
    }

    private void checkLoopConfig(CoreLibConfig.LoopConfig loop, List<ConfigPrecheckIssue> issues) {
        CoreLibConfig.LoopConfig safe = loop == null ? CoreLibConfig.LoopConfig.defaults() : loop;
        if (safe.maxTimes() <= 0) {
            addMessageIssue("action.loop.max_times", ConfigPrecheckSeverity.ERROR, "loop_max_times_invalid", issues);
        }
        if (safe.minSyncIntervalTicks() < 1L) {
            addMessageIssue("action.loop.min_sync_interval", ConfigPrecheckSeverity.ERROR, "loop_min_sync_interval_invalid", issues);
        }
        if (safe.minAsyncIntervalTicks() < 1L) {
            addMessageIssue("action.loop.min_async_interval", ConfigPrecheckSeverity.ERROR, "loop_min_async_interval_invalid", issues);
        }
        if (safe.maxActiveLoopsTotal() <= 0
                || safe.maxActiveLoopsPerPlayer() <= 0
                || safe.maxActiveLoopsPerPlugin() <= 0) {
            addMessageIssue("action.loop", ConfigPrecheckSeverity.ERROR, "loop_active_limits_invalid", issues);
        }
    }

    private void checkSequences(Map<String, List<String>> sequences,
            ConfigPrecheckContext context,
            List<ConfigPrecheckIssue> issues) {
        if (sequences == null || sequences.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : sequences.entrySet()) {
            String id = entry.getKey();
            if (Texts.isBlank(id)) {
                addMessageIssue("action.templates", ConfigPrecheckSeverity.ERROR, "template_blank_id", issues);
                continue;
            }
            if (!context.canCompile()) {
                continue;
            }
            List<String> lines = entry.getValue() == null ? List.of() : entry.getValue();
            for (int index = 0; index < lines.size(); index++) {
                for (CompileDiagnostic diagnostic : context.compileDiagnostics(lines.get(index))) {
                    addDiagnosticIssue(id, index + 1, diagnostic, issues);
                }
            }
        }
    }

    private void addDiagnosticIssue(String sequenceId,
            int line,
            CompileDiagnostic diagnostic,
            List<ConfigPrecheckIssue> issues) {
        String path = "action.templates." + sequenceId;
        String reasonKey = diagnostic.reasonKey();
        if (UNKNOWN_STAGE.equals(reasonKey)) {
            addMessageIssue(path, ConfigPrecheckSeverity.WARN, "template_action_unregistered",
                    Map.of("action", diagnostic.token()), issues);
            return;
        }
        if (UNKNOWN_SEQUENCE.equals(reasonKey)) {
            addMessageIssue(path, ConfigPrecheckSeverity.WARN, "template_missing_reference",
                    Map.of("template", diagnostic.token()), issues);
            return;
        }
        addMessageIssue(path, ConfigPrecheckSeverity.WARN, "template_invalid_syntax",
                Map.of("line", line, "error", renderDiagnostic(diagnostic)), issues);
    }
}
