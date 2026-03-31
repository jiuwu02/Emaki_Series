package emaki.jiuwu.craft.corelib.action.builtin;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import java.time.Duration;
import java.util.Map;
import net.kyori.adventure.title.Title;

public final class SendTitleAction extends BaseAction {

    public SendTitleAction() {
        super(
            "sendtitle",
            "message",
            "Send a title.",
            ActionParameter.required("title", ActionParameterType.STRING, "Title"),
            ActionParameter.optional("subtitle", ActionParameterType.STRING, "", "Subtitle"),
            ActionParameter.optional("fade_in", ActionParameterType.TIME, "10t", "Fade in"),
            ActionParameter.optional("stay", ActionParameterType.TIME, "40t", "Stay"),
            ActionParameter.optional("fade_out", ActionParameterType.TIME, "10t", "Fade out")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        long fadeIn = ActionParsers.parseTicks(arguments.get("fade_in"));
        long stay = ActionParsers.parseTicks(arguments.get("stay"));
        long fadeOut = ActionParsers.parseTicks(arguments.get("fade_out"));
        Title title = Title.title(
            MiniMessages.parse(stringArg(arguments, "title")),
            MiniMessages.parse(stringArg(arguments, "subtitle")),
            Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))
        );
        context.player().showTitle(title);
        return ActionResult.ok();
    }
}
