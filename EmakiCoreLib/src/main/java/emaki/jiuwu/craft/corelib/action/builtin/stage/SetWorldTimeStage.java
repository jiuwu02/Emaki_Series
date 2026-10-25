package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import java.util.Map;

public final class SetWorldTimeStage extends BaseStage {

    public SetWorldTimeStage() {
        super("set_world_time", "world", "设置世界时间。",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("time", CoreStageParameterType.STRING,
                        "时间，支持 tick 数或 day/night"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        World world;
        try {
            world = context.origin().getWorld();
        } catch (IllegalStateException exception) {
            return CoreActionOutcome.skipped("action.stage.set_world_time.no_world");
        }
        if (world == null) {
            return CoreActionOutcome.skipped("action.stage.set_world_time.no_world");
        }
        String raw = Texts.lower(Texts.trim(arguments.getString("time")));
        long ticks;
        switch (raw) {
            case "day" -> ticks = 1000L;
            case "night" -> ticks = 13000L;
            case "noon" -> ticks = 6000L;
            case "midnight" -> ticks = 18000L;
            default -> {
                try {
                    ticks = Long.parseLong(raw);
                } catch (NumberFormatException exception) {
                    return CoreActionOutcome.failure(
                            CoreActionFailureKind.INVALID_CONFIG,
                            "action.stage.set_world_time.invalid_time",
                            Map.of("time", raw));
                }
            }
        }
        long target = Math.max(0L, ticks % 24000L);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "execute in " + world.getKey() + " run time set " + target);
        return CoreActionOutcome.success();
    }
}
