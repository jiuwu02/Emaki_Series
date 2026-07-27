package emaki.jiuwu.craft.corelib.action.builtin;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionParsers;
import emaki.jiuwu.craft.corelib.action.ActionResult;

public final class RepairItemAction extends BaseAction {

    public RepairItemAction() {
        super(
                "repairitem",
                "item",
                "Repair a damageable item in a player inventory slot.",
                ActionParameter.optional("slot", ActionParameterType.STRING, "mainhand", "Inventory slot"),
                ActionParameter.optional("amount", ActionParameterType.INTEGER, "0", "Damage points to repair; <= 0 repairs fully")
        );
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        ActionResult playerCheck = requirePlayerResult(context);
        if (!playerCheck.success()) {
            return playerCheck;
        }
        ActionInventorySlot slot = ActionInventorySlot.parse(arguments.get("slot"), "mainhand");
        if (slot == null) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unsupported repairitem slot: " + arguments.get("slot"));
        }
        Player player = context.player();
        ItemStack itemStack = slot.get(player.getInventory());
        if (itemStack == null || itemStack.getType().isAir()) {
            return ActionResult.skipped("No item present in slot '" + slot.id() + "'.");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return ActionResult.skipped("Item in slot '" + slot.id() + "' is not damageable.");
        }
        int before = damageable.getDamage();
        if (before <= 0) {
            return ActionResult.skipped("Item in slot '" + slot.id() + "' is already fully repaired.");
        }
        int amount = ActionParsers.parseInt(arguments.get("amount"), 0);
        int after = amount <= 0 ? 0 : Math.max(0, before - amount);
        damageable.setDamage(after);
        itemStack.setItemMeta(meta);
        slot.set(player.getInventory(), itemStack);
        return ActionResult.ok(Map.of(
                "slot", slot.id(),
                "damage_before", before,
                "damage_after", after,
                "repaired", before - after
        ));
    }
}
