package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationParsers;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import java.time.Duration;
import java.util.Map;
import net.kyori.adventure.title.Title;

public final class SendTitleOperation extends BaseOperation {

    public SendTitleOperation() {
        super(
            "send_title",
            "message",
            "Send a title.",
            OperationParameter.required("title", OperationParameterType.STRING, "Title"),
            OperationParameter.optional("subtitle", OperationParameterType.STRING, "", "Subtitle"),
            OperationParameter.optional("fade_in", OperationParameterType.TIME, "10t", "Fade in"),
            OperationParameter.optional("stay", OperationParameterType.TIME, "40t", "Stay"),
            OperationParameter.optional("fade_out", OperationParameterType.TIME, "10t", "Fade out")
        );
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        long fadeIn = OperationParsers.parseTicks(arguments.get("fade_in"));
        long stay = OperationParsers.parseTicks(arguments.get("stay"));
        long fadeOut = OperationParsers.parseTicks(arguments.get("fade_out"));
        Title title = Title.title(
            MiniMessages.parse(stringArg(arguments, "title")),
            MiniMessages.parse(stringArg(arguments, "subtitle")),
            Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))
        );
        context.player().showTitle(title);
        return OperationResult.ok();
    }
}
