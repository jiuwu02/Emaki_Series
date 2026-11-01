package emaki.jiuwu.craft.corelib.debug;

import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.action.pipeline.exec.StepReporter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageKind;
import emaki.jiuwu.craft.corelib.api.text.Texts;

public final class ChatStepReporter implements StepReporter {

    private final MessageWriter writer;
    private final Consumer<String> sink;

    public interface MessageWriter {

        String write(String key, Map<String, ?> replacements);
    }

    public ChatStepReporter(MessageWriter writer, Consumer<String> sink) {
        this.writer = writer;
        this.sink = sink;
    }

    @Override
    public void source(String stageId, int selected, String reasonKey) {
        sink.accept(writer.write("command.action_debug_source", Map.of(
                "stage", Texts.toStringSafe(stageId),
                "count", String.valueOf(selected),
                "reason", reasonKey == null ? "-" : reasonKey)));
    }

    @Override
    public void gate(String stageId, int inbound, int outbound, String reasonKey) {
        sink.accept(writer.write("command.action_debug_gate", Map.of(
                "stage", Texts.toStringSafe(stageId),
                "inbound", String.valueOf(inbound),
                "outbound", String.valueOf(outbound),
                "reason", reasonKey == null ? "-" : reasonKey)));
    }

    @Override
    public void action(String stageId,
            CoreStageKind kind,
            Map<String, String> arguments,
            int success,
            int failure,
            int skipped,
            String reasonKey) {
        sink.accept(writer.write("command.action_debug_action", Map.of(
                "stage", Texts.toStringSafe(stageId),
                "kind", kind == null ? "-" : Texts.lower(kind.name()),
                "success", String.valueOf(success),
                "failure", String.valueOf(failure),
                "skipped", String.valueOf(skipped),
                "reason", reasonKey == null ? "-" : reasonKey)));
    }

    @Override
    public void control(String keyword, String detail) {
        sink.accept(writer.write("command.action_debug_control", Map.of(
                "keyword", Texts.toStringSafe(keyword),
                "detail", Texts.toStringSafe(detail))));
    }

    @Override
    public void retry(String stageId, int attempt, int maxAttempts, String reasonKey) {
        sink.accept(writer.write("command.action_debug_retry", Map.of(
                "stage", Texts.toStringSafe(stageId),
                "attempt", String.valueOf(attempt),
                "max", String.valueOf(maxAttempts),
                "reason", reasonKey == null ? "-" : reasonKey)));
    }

    @Override
    public void onError(String stageId, String failureKind, String reasonKey) {
        sink.accept(writer.write("command.action_debug_on_error", Map.of(
                "stage", Texts.toStringSafe(stageId),
                "kind", Texts.toStringSafe(failureKind),
                "reason", reasonKey == null ? "-" : reasonKey)));
    }
}
