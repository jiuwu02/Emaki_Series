package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Locale;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffectType;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class RemovePotionEffectAction extends BaseAction {

    public RemovePotionEffectAction() {
        super("removepotioneffect", "player", "Remove potion effect.", ActionParameter.required("type", ActionParameterType.STRING, "Effect type"));
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        PotionEffectType type = resolveEffectType(arguments.get("type"));
        if (type == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unknown potion effect: " + arguments.get("type"));
        }
        context.player().removePotionEffect(type);
        return ActionResult.ok();
    }

    private PotionEffectType resolveEffectType(String rawType) {
        String normalized = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.isBlank()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        return key == null ? null : Registry.EFFECT.get(key);
    }
}
