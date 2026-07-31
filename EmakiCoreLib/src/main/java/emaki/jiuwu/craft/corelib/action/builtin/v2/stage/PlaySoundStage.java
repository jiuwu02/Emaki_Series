package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.gui.SoundParser;

/**
 * Plays a sound at the target's own position.
 *
 * <p>{@code sound} is typed {@code SOUND} instead of the v1 {@code STRING}, so both a Bukkit {@code Sound}
 * name and a {@code namespace:key} form stay accepted while the type name states what the argument is.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads the player's position and sends the sound to that player.</p>
 */
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
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        String key = arguments.getString("sound");
        Sound sound = SoundParser.resolve(key);
        if (sound == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.play_sound.unknown_sound", Map.of("sound", key));
        }
        SoundParser.SoundDefinition definition = SoundParser.parse(Map.of(
                "sound", key,
                "volume", arguments.getString("volume", "1"),
                "pitch", arguments.getString("pitch", "1")));
        target.playSound(target.getLocation(), sound, definition.volume(), definition.pitch());
        return CoreActionOutcome.success();
    }
}
