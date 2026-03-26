package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationParsers;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class GivePotionEffectOperation extends BaseOperation {

    public GivePotionEffectOperation() {
        super(
            "give_potion_effect",
            "player",
            "Give potion effect.",
            OperationParameter.required("type", OperationParameterType.STRING, "Effect type"),
            OperationParameter.required("level", OperationParameterType.INTEGER, "Effect level"),
            OperationParameter.required("duration", OperationParameterType.TIME, "Duration"),
            OperationParameter.optional("ambient", OperationParameterType.BOOLEAN, "false", "Ambient"),
            OperationParameter.optional("particles", OperationParameterType.BOOLEAN, "true", "Particles"),
            OperationParameter.optional("icon", OperationParameterType.BOOLEAN, "true", "Icon")
        );
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        PotionEffectType type = PotionEffectType.getByName(arguments.get("type").toUpperCase());
        if (type == null) {
            return OperationResult.failure(OperationErrorType.INVALID_ARGUMENT, "Unknown potion effect: " + arguments.get("type"));
        }
        int amplifier = Math.max(0, OperationParsers.parseInt(arguments.get("level"), 1) - 1);
        int duration = (int) OperationParsers.parseTicks(arguments.get("duration"));
        boolean ambient = Boolean.TRUE.equals(OperationParsers.parseBoolean(arguments.get("ambient")));
        boolean particles = !Boolean.FALSE.equals(OperationParsers.parseBoolean(arguments.get("particles")));
        boolean icon = !Boolean.FALSE.equals(OperationParsers.parseBoolean(arguments.get("icon")));
        context.player().addPotionEffect(new PotionEffect(type, duration, amplifier, ambient, particles, icon));
        return OperationResult.ok();
    }
}
