package emaki.jiuwu.craft.corelib.action.builtin.v2.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.v2.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.v2.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;

/**
 * Repairs a damageable item in one of the target's inventory slots.
 *
 * <p>{@code amount} of zero or less repairs the item fully, as in v1.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's inventory.</p>
 */
public final class RepairItemStage extends BaseStage {

    public RepairItemStage() {
        super("repair_item", "item", "Repairs a damageable item in one of the target's slots.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "mainhand",
                        "Inventory slot"),
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "0",
                        "Damage points to repair, 0 or less repairs fully"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        StageSupport.Slot slot = StageSupport.slot(arguments.getString("slot"), "mainhand");
        if (slot == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.v2.stage.item.unknown_slot", Map.of("slot", arguments.getString("slot")));
        }
        ItemStack itemStack = slot.get(target.getInventory());
        if (StageSupport.isEmpty(itemStack)) {
            return CoreActionOutcome.skipped("action.v2.stage.item.slot_empty");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return CoreActionOutcome.skipped("action.v2.stage.item.not_damageable");
        }
        int before = damageable.getDamage();
        if (before <= 0) {
            return CoreActionOutcome.skipped("action.v2.stage.item.already_repaired");
        }
        int amount = arguments.getInt("amount", 0);
        int after = amount <= 0 ? 0 : Math.max(0, before - amount);
        damageable.setDamage(after);
        itemStack.setItemMeta(meta);
        slot.set(target.getInventory(), itemStack);
        return CoreActionOutcome.success(Map.of(
                "slot", slot.id(),
                "damage_before", before,
                "damage_after", after,
                "repaired", before - after));
    }
}
