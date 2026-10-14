package emaki.jiuwu.craft.corelib.action.builtin.stage;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;

public final class SendTitleFadeStage extends BaseStage {

    public SendTitleFadeStage() {
        super("send_title_fade", "message", "发送带淡入淡出时长的标题。",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("title", CoreStageParameterType.STRING, "标题文本"),
                CoreStageParameter.optional("subtitle", CoreStageParameterType.STRING, "", "副标题文本"),
                CoreStageParameter.optional("fade_in", CoreStageParameterType.INTEGER, "10", "淡入 tick"),
                CoreStageParameter.optional("stay", CoreStageParameterType.INTEGER, "40", "停留 tick"),
                CoreStageParameter.optional("fade_out", CoreStageParameterType.INTEGER, "10", "淡出 tick"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (!(context.currentTarget().entityOrNull() instanceof Player player)) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        int fadeIn = Math.max(0, arguments.getInt("fade_in", 10));
        int stay = Math.max(0, arguments.getInt("stay", 40));
        int fadeOut = Math.max(0, arguments.getInt("fade_out", 10));
        player.showTitle(net.kyori.adventure.title.Title.title(
                MiniMessages.parse(arguments.getString("title")),
                MiniMessages.parse(arguments.getString("subtitle")),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L))));
        return CoreActionOutcome.success();
    }
}
