package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Sound;

import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.gui.SoundParser;
import emaki.jiuwu.craft.skills.script.SkillScriptContext;

public final class SoundSkillAction extends AbstractSkillScriptAction {

    public SoundSkillAction() {
        super("sound", "feedback", "Play skill sound.",
                ActionParameter.required("sound", ActionParameterType.STRING, "Sound"),
                ActionParameter.optional("volume", ActionParameterType.DOUBLE, "1", "Volume"),
                ActionParameter.optional("pitch", ActionParameterType.DOUBLE, "1", "Pitch"),
                ActionParameter.optional("at", ActionParameterType.STRING, "caster", "Location"));
    }

    @Override
    public CompletableFuture<ActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Sound sound = SoundParser.resolve(arg(arguments, "sound", ""));
        if (sound == null) {
            return completed(ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown sound: " + arg(arguments, "sound", "")));
        }
        Location location = locationTarget(context, arguments);
        if (location == null || location.getWorld() == null) {
            return completed(ActionResult.ok());
        }
        location.getWorld().playSound(location, sound, (float) doubleArg(arguments, "volume", 1D), (float) doubleArg(arguments, "pitch", 1D));
        return completed(ActionResult.ok());
    }
}
