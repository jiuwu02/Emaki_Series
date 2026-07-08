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

public final class DamageItemAction extends BaseAction {

    public DamageItemAction() {
        super(
                "damageitem",
                "item",
                "Damage a damageable item in a player inventory slot.",
                ActionParameter.optional("slot", ActionParameterType.STRING, "mainhand", "Inventory slot"),
                ActionParameter.optional("amount", ActionParameterType.INTEGER, "1", "Damage points to add"),
                ActionParameter.optional("delete_item", ActionParameterType.BOOLEAN, "false", "Remove item when damage reaches max durability")
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
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, "Unsupported damageitem slot: " + arguments.get("slot"));
        }
        Player player = context.player();
        ItemStack itemStack = slot.get(player.getInventory());
        if (itemStack == null || itemStack.getType().isAir()) {
            return ActionResult.skipped("No item present in slot '" + slot.id() + "'.");
        }
        int maxDurability = itemStack.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return ActionResult.skipped("Item in slot '" + slot.id() + "' does not have durability.");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return ActionResult.skipped("Item in slot '" + slot.id() + "' is not damageable.");
        }
        int amount = Math.max(0, ActionParsers.parseInt(arguments.get("amount"), 1));
        if (amount <= 0) {
            return ActionResult.skipped("Damage amount must be greater than zero.");
        }
        boolean deleteItem = Boolean.TRUE.equals(ActionParsers.parseBoolean(arguments.get("delete_item")));
        int before = damageable.getDamage();
        int requested = before + amount;
        boolean shouldDelete = requested >= maxDurability && deleteItem;
        if (shouldDelete) {
            slot.clear(player.getInventory());
            return ActionResult.ok(Map.of(
                    "slot", slot.id(),
                    "damage_before", before,
                    "damage_after", maxDurability,
                    "deleted", true
            ));
        }
        int after = Math.min(Math.max(0, maxDurability - 1), requested);
        damageable.setDamage(after);
        itemStack.setItemMeta(meta);
        slot.set(player.getInventory(), itemStack);
        return ActionResult.ok(Map.of(
                "slot", slot.id(),
                "damage_before", before,
                "damage_after", after,
                "deleted", false
        ));
    }
}
