package emaki.jiuwu.craft.skills.script.builtin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
        float volume = (float) doubleArg(arguments, "volume", 1D);
        float pitch = (float) doubleArg(arguments, "pitch", 1D);
        return atLocation(context, arguments, "at", "caster", location -> {
            if (location.getWorld() != null) {
                location.getWorld().playSound(location, sound, volume, pitch);
            }
            return SkillActionResult.ok();
        });
    }
}
