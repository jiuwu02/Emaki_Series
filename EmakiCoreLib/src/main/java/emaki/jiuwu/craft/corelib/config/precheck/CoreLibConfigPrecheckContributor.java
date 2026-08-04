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

    /** Reported when a stage id resolves against no registered stage. */
    private static final String UNKNOWN_STAGE = "action.validate.unknown_stage";

    /** Reported when a {@code run} target names no configured sequence. */
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

    /**
     * Compiles every configured sequence line and reports what the compiler rejected.
     *
     * <p>No parsing or argument checking is repeated here. The v1 version re-implemented both and therefore
     * had to know each action's arguments by hand; asking the pipeline compiler means the precheck accepts exactly
     * what the runtime accepts, including bracket, branch, repeat-cap and required-argument errors it never
     * used to catch.</p>
     */
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

    /**
     * Turns one compile diagnostic into a precheck issue.
     *
     * <p>Reported as warnings, not errors, for two reasons. A blocking issue here aborts the action system
     * reload, and on the first start after the upgrade that would disable the plugin before the one-shot
     * old-syntax migration ever runs, leaving a server with old template lines unable to start at all. And
     * the runtime itself does not treat an uncompilable sequence as fatal: it drops that sequence and logs
     * it, so blocking here would make the precheck stricter than the engine it speaks for.</p>
     *
     * <p>An unknown stage is additionally expected during a reload: this runs against the candidate stage
     * table, which holds CoreLib's builtin stages only, so a sequence calling a business module's stage
     * cannot resolve yet.</p>
     */
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
                Map.of("line", line, "error", diagnostic.toString()), issues);
    }
}
