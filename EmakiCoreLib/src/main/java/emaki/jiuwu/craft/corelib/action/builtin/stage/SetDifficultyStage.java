package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Locale;

import org.bukkit.Difficulty;
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

public final class SetDifficultyStage extends BaseStage {

    public SetDifficultyStage() {
        super("set_difficulty", "world", "设置世界难度。",
                CoreTargetRequirement.NONE, CoreActionExecutionDomain.SERVER_GLOBAL,
                CoreStageParameter.required("difficulty", CoreStageParameterType.STRING,
                        "难度：peaceful / easy / normal / hard"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        World world;
        try {
            world = context.origin().getWorld();
        } catch (IllegalStateException exception) {
            return CoreActionOutcome.skipped("action.stage.set_difficulty.no_world");
        }
        if (world == null) {
            return CoreActionOutcome.skipped("action.stage.set_difficulty.no_world");
        }
        Difficulty difficulty;
        try {
            difficulty = Difficulty.valueOf(
                    Texts.lower(Texts.trim(arguments.getString("difficulty")))
                            .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return CoreActionOutcome.failure(
                    CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.set_difficulty.unknown_difficulty",
                    Map.of("difficulty", arguments.getString("difficulty")));
        }
        world.setDifficulty(difficulty);
        return CoreActionOutcome.success();
    }
}
