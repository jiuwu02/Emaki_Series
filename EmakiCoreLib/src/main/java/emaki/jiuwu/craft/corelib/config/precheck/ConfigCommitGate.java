package emaki.jiuwu.craft.corelib.config.precheck;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;


import emaki.jiuwu.craft.corelib.EmakiCoreLibPlugin;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ConfigCommitGate {

    private ConfigCommitGate() {
    }

    public record Result(boolean committed, String moduleId, List<String> failures) {

        public Result {
            moduleId = Texts.isBlank(moduleId) ? "" : moduleId;
            failures = failures == null ? List.of() : List.copyOf(failures);
        }

        public boolean rejected() {
            return !committed;
        }
    }

    public static <T> Result commit(LogMessages messages,
            String moduleId,
            Supplier<T> currentReader,
            Supplier<T> candidateLoader,
            Consumer<T> restorer) {
        if (candidateLoader == null) {
            return new Result(false, moduleId, List.of("配置提交关卡未收到候选加载器"));
        }
        T previous = currentReader == null ? null : currentReader.get();
        candidateLoader.get();
        Result result = evaluate(messages, moduleId);
        if (result.rejected() && restorer != null) {

            restorer.accept(previous);
        }
        return result;
    }

    public static Result evaluate(LogMessages messages, String moduleId) {
        if (Texts.isBlank(moduleId)) {
            return new Result(false, moduleId, List.of("配置提交关卡收到空的模块 id"));
        }
        EmakiCoreLibPlugin coreLib = EmakiCoreLibPlugin.lookup();
        if (coreLib == null || coreLib.configPrecheckService() == null) {

            return new Result(false, moduleId, List.of("配置预检服务不可用"));
        }
        ConfigPrecheckReport report = coreLib.configPrecheckService()
                .checkModule(coreLib.configModel(), moduleId);
        if (messages != null) {
            ConfigPrecheckMessages.logReport(messages, moduleId, report);
        }
        if (report == null) {
            return new Result(false, moduleId, List.of("配置预检未产生报告"));
        }
        if (report.success()) {
            return new Result(true, moduleId, List.of());
        }
        return new Result(false, moduleId,
                messages == null ? List.of() : report.formatLines(messages, moduleId));
    }
}
