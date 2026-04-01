package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class GivePotionEffectAction extends BaseAction {

    public GivePotionEffectAction() {
        super(
                "givepotioneffect",
                "player",
                "Give potion effect.",
                ActionParameter.required("type", ActionParameterType.STRING, "Effect type"),
                ActionParameter.required("level", ActionParameterType.INTEGER, "Effect level"),
                ActionParameter.required("duration", ActionParameterType.TIME, "Duration"),
                ActionParameter.optional("ambient", ActionParameterType.BOOLEAN, "false", "Ambient"),
                ActionParameter.optional("particles", ActionParameterType.BOOLEAN, "true", "Particles"),
                ActionParameter.optional("icon", ActionParameterType.BOOLEAN, "true", "Icon")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        PotionEffectType type = PotionEffectType.getByName(arguments.get("type").toUpperCase());
        if (type == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown potion effect: " + arguments.get("type"));
        }
        int amplifier = Math.max(0, ActionParsers.parseInt(arguments.get("level"), 1) - 1);
        int duration = (int) ActionParsers.parseTicks(arguments.get("duration"));
        boolean ambient = Boolean.TRUE.equals(ActionParsers.parseBoolean(arguments.get("ambient")));
        boolean particles = !Boolean.FALSE.equals(ActionParsers.parseBoolean(arguments.get("particles")));
        boolean icon = !Boolean.FALSE.equals(ActionParsers.parseBoolean(arguments.get("icon")));
        context.player().addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        return ActionResult.ok();
    }
}
