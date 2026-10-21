package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.text.Texts;

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
                    return emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome.failure(
                            emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind.INVALID_CONFIG,
                            "action.stage.set_world_time.invalid_time",
                            java.util.Map.of("time", raw));
                }
            }
        }
        long target = Math.max(0L, ticks % 24000L);
        if (unifiedPlatform()) {
            // 26.x 起 World#setTime 不再产生可观察效果, 改派发原版 time set 到该世界维度
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "execute in " + world.getKey() + " run time set " + target);
        } else {
            world.setTime(target);
        }
        return emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome.success();
    }

    private static boolean unifiedPlatform() {
        try {
            String version = org.bukkit.Bukkit.getMinecraftVersion();
            if (version == null || version.isBlank()) {
                return false;
            }
            int dot = version.indexOf('.');
            String head = dot < 0 ? version : version.substring(0, dot);
            return Integer.parseInt(head) >= 26;
        } catch (Throwable unavailable) {
            return false;
        }
    }
}
