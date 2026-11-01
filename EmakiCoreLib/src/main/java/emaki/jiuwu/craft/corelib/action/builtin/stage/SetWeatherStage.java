package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;

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

public final class SetWeatherStage extends BaseStage {

    public SetWeatherStage() {
        super("set_weather", "world", "设置世界天气。",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("weather", CoreStageParameterType.STRING,
                        "天气：clear / rain / thunder"),
                CoreStageParameter.optional("duration", CoreStageParameterType.INTEGER, "0",
                        "持续秒数，0 表示无限"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        World world;
        try {
            world = context.origin().getWorld();
        } catch (IllegalStateException exception) {
            return CoreActionOutcome.skipped("action.stage.set_weather.no_world");
        }
        if (world == null) {
            return CoreActionOutcome.skipped("action.stage.set_weather.no_world");
        }
        int duration = Math.max(0, arguments.getInt("duration", 0)) * 20;
        switch (Texts.lower(Texts.trim(arguments.getString("weather")))) {
            case "clear" -> world.setStorm(false);
            case "rain" -> {
                world.setStorm(true);
                world.setThundering(false);
            }
            case "thunder" -> {
                world.setStorm(true);
                world.setThundering(true);
            }
            default -> {
                return CoreActionOutcome.failure(
                        CoreActionFailureKind.INVALID_CONFIG,
                        "action.stage.set_weather.unknown_weather",
                        Map.of("weather", arguments.getString("weather")));
            }
        }
        if (duration > 0) {
            world.setWeatherDuration(duration);
        }
        return CoreActionOutcome.success();
    }
}
