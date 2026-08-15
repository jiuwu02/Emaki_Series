package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.gui.SoundParser;

public final class PlaySoundStage extends BaseStage {

    public PlaySoundStage() {
        super("play_sound", "feedback", "Plays a sound to the target.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.required("sound", CoreStageParameterType.SOUND, "Sound key"),
                CoreStageParameter.optional("volume", CoreStageParameterType.DOUBLE, "1", "Volume"),
                CoreStageParameter.optional("pitch", CoreStageParameterType.DOUBLE, "1", "Pitch"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        String key = arguments.getString("sound");
        Sound sound = SoundParser.resolve(key);
        if (sound == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.play_sound.unknown_sound", Map.of("sound", key));
        }
        SoundParser.SoundDefinition definition = SoundParser.parse(Map.of(
                "sound", key,
                "volume", arguments.getString("volume", "1"),
                "pitch", arguments.getString("pitch", "1")));
        target.playSound(target.getLocation(), sound, definition.volume(), definition.pitch());
        return CoreActionOutcome.success();
    }
}
