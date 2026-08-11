package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.time.Duration;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import net.kyori.adventure.title.Title;

/**
 * Shows a title and subtitle to the target.
 *
 * <p>The three timing arguments are declared as {@code DURATION} rather than the v1 {@code TIME}; both
 * parse the same text, so {@code fade_in=10t} keeps working while the newer type name also accepts
 * {@code 500ms}.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: writes to that player's connection.</p>
 */
public final class SendTitleStage extends BaseStage {

    public SendTitleStage() {
        super("send_title", "message", "Shows a title to the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("title", CoreStageParameterType.STRING, "Title"),
                CoreStageParameter.optional("subtitle", CoreStageParameterType.STRING, "", "Subtitle"),
                CoreStageParameter.optional("fade_in", CoreStageParameterType.DURATION, "10t", "Fade in"),
                CoreStageParameter.optional("stay", CoreStageParameterType.DURATION, "40t", "Stay"),
                CoreStageParameter.optional("fade_out", CoreStageParameterType.DURATION, "10t", "Fade out"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        long fadeIn = arguments.getDurationTicks("fade_in", 10L);
        long stay = arguments.getDurationTicks("stay", 40L);
        long fadeOut = arguments.getDurationTicks("fade_out", 10L);
        target.showTitle(Title.title(
                MiniMessages.parse(arguments.getString("title")),
                MiniMessages.parse(arguments.getString("subtitle")),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L))));
        return CoreActionOutcome.success();
    }
}
