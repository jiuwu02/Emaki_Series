package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Sound;

import emaki.jiuwu.craft.skills.api.SkillActionErrorType;
import emaki.jiuwu.craft.skills.api.SkillActionParameter;
import emaki.jiuwu.craft.skills.api.SkillActionParameterType;
import emaki.jiuwu.craft.skills.api.SkillActionResult;
import emaki.jiuwu.craft.corelib.gui.SoundParser;
import emaki.jiuwu.craft.skills.api.SkillScriptContext;

public final class SoundSkillAction extends AbstractSkillScriptAction {

    public SoundSkillAction() {
        super("sound", "feedback", "Play skill sound.",
                SkillActionParameter.required("sound", SkillActionParameterType.STRING, "Sound"),
                SkillActionParameter.optional("volume", SkillActionParameterType.DOUBLE, "1", "Volume"),
                SkillActionParameter.optional("pitch", SkillActionParameterType.DOUBLE, "1", "Pitch"),
                SkillActionParameter.optional("at", SkillActionParameterType.STRING, "caster", "Location"));
    }

    @Override
    public CompletableFuture<SkillActionResult> execute(SkillScriptContext context, Map<String, String> arguments) {
        Sound sound = SoundParser.resolve(arg(arguments, "sound", ""));
        if (sound == null) {
            return completed(SkillActionResult.failure(SkillActionErrorType.INVALID_ARGUMENT, "Unknown sound: " + arg(arguments, "sound", "")));
        }
        Location location = locationTarget(context, arguments);
        if (location == null || location.getWorld() == null) {
            return completed(SkillActionResult.ok());
        }
        location.getWorld().playSound(location, sound, (float) doubleArg(arguments, "volume", 1D), (float) doubleArg(arguments, "pitch", 1D));
        return completed(SkillActionResult.ok());
    }
}
