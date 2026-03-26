package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationErrorType;
import emaki.jiuwu.craft.corelib.operation.OperationParameter;
import emaki.jiuwu.craft.corelib.operation.OperationParameterType;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;
import org.bukkit.potion.PotionEffectType;

public final class RemovePotionEffectOperation extends BaseOperation {

    public RemovePotionEffectOperation() {
        super("remove_potion_effect", "player", "Remove potion effect.", OperationParameter.required("type", OperationParameterType.STRING, "Effect type"));
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
        context.player().removePotionEffect(type);
        return OperationResult.ok();
    }
}
