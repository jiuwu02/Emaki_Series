package emaki.jiuwu.craft.codex.action;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.codex.EmakiCodexPlugin;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import net.kyori.adventure.text.Component;

public final class BroadcastAchievementStage implements CoreActionStage {

    private static final String DEFAULT_FRAME = "task";
    private static final Map<String, String> FRAME_FALLBACKS = Map.of(
            "task", "<white>%player% 达成了成就 <green>[%title%]</green></white>",
            "goal", "<white>%player% 达成了目标 <green>[%title%]</green></white>",
            "challenge", "<white>%player% 完成了挑战 <light_purple>[%title%]</light_purple></white>");

    private final EmakiCodexPlugin plugin;

    public BroadcastAchievementStage(@NotNull EmakiCodexPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String id() {
        return "codex_broadcast_achievement";
    }

    @Override
    public @NotNull String description() {
        return "Announces an achievement to the whole server with a vanilla-style sentence.";
    }

    @Override
    public @NotNull String category() {
        return "codex";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of(
                CoreStageParameter.required("title", CoreStageParameterType.STRING, "Achievement title"),
                CoreStageParameter.optional("description", CoreStageParameterType.STRING, "",
                        "Hover tooltip content"),
                CoreStageParameter.optional("frame", CoreStageParameterType.STRING, DEFAULT_FRAME,
                        "Default sentence to use: task, goal, or challenge"),
                CoreStageParameter.optional("format", CoreStageParameterType.STRING, "",
                        "Overrides the default sentence entirely when non-empty"),
                CoreStageParameter.optional("permission", CoreStageParameterType.STRING, "",
                        "Only players holding this permission receive the announcement"),
                CoreStageParameter.optional("console", CoreStageParameterType.BOOLEAN, "true",
                        "Whether to echo the announcement to the console"));
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.global();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player achiever = player(context.currentTarget());
        if (achiever == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        String title = Texts.trim(arguments.getString("title"));
        if (title.isEmpty()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.codex.title_required");
        }
        String frame = frame(arguments);
        if (frame == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.codex.unknown_frame",
                    Map.of("frame", arguments.getString("frame")));
        }
        String sentence = sentence(arguments, frame, achiever, title);
        Component announcement = MiniMessages.parse(
                MiniMessages.withHoverText(sentence, arguments.getString("description")));
        String permission = Texts.trim(arguments.getString("permission"));
        int recipients = 0;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!permission.isEmpty() && !viewer.hasPermission(permission)) {
                continue;
            }
            viewer.sendMessage(announcement);
            recipients++;
        }
        if (arguments.getBoolean("console", true)) {
            Bukkit.getConsoleSender().sendMessage(announcement);
        }
        return CoreActionOutcome.success(Map.of(
                "player", achiever.getName(),
                "title", title,
                "frame", frame,
                "recipients", recipients));
    }

    private String sentence(CoreResolvedArguments arguments, String frame, Player achiever, String title) {
        Map<String, String> replacements = Map.of("player", achiever.getName(), "title", title);
        String override = Texts.trim(arguments.getString("format"));
        if (!override.isEmpty()) {
            return Texts.formatTemplate(override, replacements);
        }
        String template = plugin.messageService().messageOrFallback("broadcast.frame." + frame,
                FRAME_FALLBACKS.getOrDefault(frame, FRAME_FALLBACKS.get(DEFAULT_FRAME)));
        return Texts.formatTemplate(template, replacements);
    }

    private String frame(CoreResolvedArguments arguments) {
        String frame = Texts.lower(arguments.getString("frame", DEFAULT_FRAME));
        return switch (frame) {
            case "task", "goal", "challenge" -> frame;
            default -> null;
        };
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
