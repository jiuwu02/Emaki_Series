package emaki.jiuwu.craft.corelib.operation.builtin;

import emaki.jiuwu.craft.corelib.operation.OperationContext;
import emaki.jiuwu.craft.corelib.operation.OperationResult;
import java.util.Map;

public final class ClearPotionEffectsOperation extends BaseOperation {

    public ClearPotionEffectsOperation() {
        super("clear_potion_effects", "player", "Clear all potion effects.");
    }

    @Override
    public OperationResult execute(OperationContext context, Map<String, String> arguments) {
        OperationResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        context.player().getActivePotionEffects().forEach(effect -> context.player().removePotionEffect(effect.getType()));
        return OperationResult.ok();
    }
}
