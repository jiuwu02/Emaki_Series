package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.gui.SoundParser;
import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;
import org.bukkit.Sound;

public final class PlaySoundOperation extends BaseOperation {

    public PlaySoundOperation() {
        super(
            "play_sound",
            "feedback",
            "Play a sound.",
            OperationParameter.required("sound", OperationParameterType.STRING, "Sound key"),
            OperationParameter.optional("volume", OperationParameterType.DOUBLE, "1", "Volume"),
            OperationParameter.optional("pitch", OperationParameterType.DOUBLE, "1", "Pitch")
        );
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        Sound sound = SoundParser.resolve(stringArg(arguments, "sound"));
        if (sound == null) {
            return OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "Unknown sound: " + stringArg(arguments, "sound"));
        }
        SoundParser.SoundDefinition definition = SoundParser.parse(
            Map.of(
                "sound", stringArg(arguments, "sound"),
                "volume", stringArg(arguments, "volume"),
                "pitch", stringArg(arguments, "pitch")
            )
        );
        context.player().playSound(context.player().getLocation(), sound, definition.volume(), definition.pitch());
        return OperationResult.ok();
    }
}
