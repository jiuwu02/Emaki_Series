package emaki.jiuwu.craft.item.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemHeldItemAction implements Action {

    enum Operation {
        UPDATE,
        RERENDER,
        REPAIR_AMOUNT,
        DAMAGE,
        SET_DAMAGE,
        SET_DURABILITY
    }

    private final EmakiItemPlugin plugin;
    private final String id;
    private final Operation operation;

    ItemHeldItemAction(EmakiItemPlugin plugin, String id, Operation operation) {
        this.plugin = plugin;
        this.id = id;
        this.operation = operation;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String description() {
        return "Modify the action player's held EmakiItem through EmakiItem services.";
    }

    @Override
    public String category() {
        return "emakiitem";
    }

    @Override
    public List<ActionParameter> parameters() {
        return switch (operation) {
            case UPDATE, RERENDER -> List.of();
            case REPAIR_AMOUNT, DAMAGE -> List.of(ActionParameter.required("amount", ActionParameterType.INTEGER, "Damage amount."));
            case SET_DAMAGE, SET_DURABILITY -> List.of(ActionParameter.required("value", ActionParameterType.INTEGER, "Target damage or durability value."));
        };
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiItem action requires a player context.");
        }
        ItemStack original = player.getInventory().getItemInMainHand();
        if (original == null || original.getType().isAir()) {
            return ActionResult.skipped("Player is not holding an item.");
        }
        ItemStack updated = switch (operation) {
            case UPDATE -> plugin.updateService().forceUpdate(original);
            case RERENDER -> plugin.coreLib().itemAssemblyService().rebuild(original);
            case REPAIR_AMOUNT -> repair(original, intArgument(arguments, "amount", 0));
            case DAMAGE -> damage(original, intArgument(arguments, "amount", 0));
            case SET_DAMAGE -> setDamage(original, intArgument(arguments, "value", 0));
            case SET_DURABILITY -> setDurability(original, intArgument(arguments, "value", 0));
        };
        if (updated == null || updated.getType().isAir()) {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION, "EmakiItem action returned no item.");
        }
        boolean changed = updated != original;
        if (changed) {
            player.getInventory().setItemInMainHand(updated);
        }
        return ActionResult.ok(Map.of("changed", changed, "damage", plugin.repairService().currentDamage(updated), "max_damage", plugin.repairService().maxDamage(updated)));
    }

    private ItemStack repair(ItemStack itemStack, int amount) {
        plugin.repairService().applyRepair(itemStack, Math.max(0, amount));
        return itemStack;
    }

    private ItemStack damage(ItemStack itemStack, int amount) {
        int current = plugin.repairService().currentDamage(itemStack);
        return setDamage(itemStack, current + Math.max(0, amount));
    }

    private ItemStack setDurability(ItemStack itemStack, int durability) {
        int maxDamage = plugin.repairService().maxDamage(itemStack);
        if (maxDamage <= 0) {
            return itemStack;
        }
        return setDamage(itemStack, maxDamage - Math.max(0, durability));
    }

    private ItemStack setDamage(ItemStack itemStack, int damage) {
        ItemMeta meta = itemStack == null ? null : itemStack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return itemStack;
        }
        int maxDamage = plugin.repairService().maxDamage(itemStack);
        int clamped = maxDamage <= 0 ? Math.max(0, damage) : Numbers.clamp(damage, 0, maxDamage);
        damageable.setDamage(clamped);
        itemStack.setItemMeta(meta);
        if (maxDamage <= 0 || clamped < maxDamage) {
            plugin.repairService().clearDisabled(itemStack);
        }
        return itemStack;
    }

    private int intArgument(Map<String, String> arguments, String key, int fallback) {
        String value = arguments == null ? null : arguments.get(key);
        return Texts.isBlank(value) ? fallback : Numbers.tryParseInt(value, fallback);
    }
}
