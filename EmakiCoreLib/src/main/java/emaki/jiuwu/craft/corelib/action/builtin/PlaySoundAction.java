package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.Sound;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.gui.SoundParser;

public final class PlaySoundAction extends BaseAction {

    public PlaySoundAction() {
        super(
                "playsound",
                "feedback",
                "Play a sound.",
                ActionParameter.required("sound", ActionParameterType.STRING, "Sound key"),
                ActionParameter.optional("volume", ActionParameterType.DOUBLE, "1", "Volume"),
                ActionParameter.optional("pitch", ActionParameterType.DOUBLE, "1", "Pitch")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        Sound sound = SoundParser.resolve(stringArg(arguments, "sound"));
        if (sound == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown sound: " + stringArg(arguments, "sound"));
        }
        SoundParser.SoundDefinition definition = SoundParser.parse(
                Map.of(
                        "sound", stringArg(arguments, "sound"),
                        "volume", stringArg(arguments, "volume"),
                        "pitch", stringArg(arguments, "pitch")
                )
        );
        context.player().playSound(context.player().getLocation(), sound, definition.volume(), definition.pitch());
        return ActionResult.ok();
    }
}
