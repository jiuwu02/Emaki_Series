package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.List;
import java.util.function.Supplier;

import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.Texts;

/**
 * Reusable candidate-then-commit gate for module configuration reloads.
 *
 * <p>Modules historically called their config loader unconditionally and only logged the precheck
 * report afterwards, which meant an invalid config had already replaced the running state by the
 * time the operator saw the failure. Worse, {@code YamlConfigLoader#load()} falls back to
 * {@code defaults()} when parsing throws, so a single typo silently reset an entire module instead
 * of keeping the last known good values.
 *
 * <p>This helper generalises the pattern that {@code EmakiCoreLibPlugin} already applies to itself:
 * capture the currently active value, let the loader produce a candidate, run the module precheck,
 * and only keep the candidate when the precheck succeeds. On failure the previous value is restored
 * so the running configuration is never replaced by a rejected candidate.
 *
 * <p>The gate performs no scheduling and touches no Bukkit state, so it is safe to call from
 * whichever thread already owns the module's reload path.
 */
public final class ConfigCommitGate {

    private ConfigCommitGate() {
    }

    /**
     * Outcome of a gated reload attempt.
     *
     * @param committed whether the candidate was accepted and is now active
     * @param moduleId  the precheck module id that was evaluated
     * @param failures  formatted precheck failure lines; empty when {@code committed} is true
     */
    public record Result(boolean committed, String moduleId, List<String> failures) {

        public Result {
            moduleId = Texts.isBlank(moduleId) ? "" : moduleId;
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        /** @return {@code true} when the candidate was rejected and the previous value was restored */
        public boolean rejected() {
            return !committed;
        }
    }

    /**
     * Loads a configuration candidate and commits it only when the module precheck succeeds.
     *
     * <p>Typical use from a module reload path:
     * <pre>{@code
     * ConfigCommitGate.Result result = ConfigCommitGate.commit(
     *         messages,
     *         "level",
     *         appConfigLoader::current,
     *         appConfigLoader::load,
     *         appConfigLoader::overrideCurrent);
     * if (result.rejected()) {
     *     return; // previous config is still active
     * }
     * }</pre>
     *
     * @param messages       module log messages used to report the precheck outcome; may be {@code null}
     * @param moduleId       precheck module id, as registered through {@link ConfigPrecheckLifecycleSupport}
     * @param currentReader  reads the currently active value before the candidate is produced
     * @param candidateLoader produces the candidate and makes it the loader's current value
     * @param restorer       restores a previously captured value when the candidate is rejected
     * @param <T>            configuration model type
     * @return the gate result; never {@code null}
     */
    public static <T> Result commit(LogMessages messages,
            String moduleId,
            Supplier<T> currentReader,
            Supplier<T> candidateLoader,
            java.util.function.Consumer<T> restorer) {
        if (candidateLoader == null) {
            return new Result(false, moduleId, List.of("config commit gate received no candidate loader"));
        }
        T previous = currentReader == null ? null : currentReader.get();
        candidateLoader.get();
        Result result = evaluate(messages, moduleId);
        if (result.rejected() && restorer != null) {
            // Keep the last known good configuration active. Without this the module would run on
            // whatever the loader fell back to, which is defaults() rather than the previous values.
            restorer.accept(previous);
        }
        return result;
    }

    /**
     * Runs the module precheck and reports it, without loading anything.
     *
     * <p>Use this when a module cannot express its reload as a single loader call and instead needs
     * to decide for itself what to roll back. Callers must still honour a rejected result by not
     * applying the candidate to their runtime services.
     *
     * @param messages module log messages used to report the precheck outcome; may be {@code null}
     * @param moduleId precheck module id
     * @return the gate result; never {@code null}
     */
    public static Result evaluate(LogMessages messages, String moduleId) {
        if (Texts.isBlank(moduleId)) {
            return new Result(false, moduleId, List.of("config commit gate received a blank module id"));
        }
        EmakiCoreLibPlugin coreLib = JavaPlugin.getPlugin(EmakiCoreLibPlugin.class);
        if (coreLib == null || coreLib.configPrecheckService() == null) {
            // Fail closed: an unavailable precheck service cannot certify the candidate. Committing
            // anyway would reintroduce the very "bad config silently becomes active" behaviour.
            return new Result(false, moduleId, List.of("config precheck service is unavailable"));
        }
        ConfigPrecheckReport report = coreLib.configPrecheckService()
                .checkModule(coreLib.configModel(), moduleId);
        if (messages != null) {
            ConfigPrecheckMessages.logReport(messages, moduleId, report);
        }
        if (report == null) {
            return new Result(false, moduleId, List.of("config precheck produced no report"));
        }
        if (report.success()) {
            return new Result(true, moduleId, List.of());
        }
        return new Result(false, moduleId,
                messages == null ? List.of() : report.formatLines(messages, moduleId));
    }
}
