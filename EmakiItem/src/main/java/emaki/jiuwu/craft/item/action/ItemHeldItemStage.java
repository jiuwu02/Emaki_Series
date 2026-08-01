package emaki.jiuwu.craft.item.action.v2;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.v2.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

/**
 * Updates, re-renders or repairs the item in the target's main hand.
 *
 * <p>The v2 counterpart of {@code ItemHeldItemAction}. {@code item_update} reapplies the definition while
 * {@code item_rerender} only rebuilds display data, which is why they stay separate.</p>
 *
 * <p>Domain {@code CONTEXT_ENTITY}: reads and writes one player's main-hand slot.</p>
 */
public final class ItemHeldItemStage implements CoreActionStage {

    /** Which item mutation a stage instance performs. */
    public enum Operation {

        /** Reapply the item definition, picking up config changes. */
        UPDATE("item_update", "Reapplies the item definition to the target's held item."),

        /** Rebuild display data only. */
        RERENDER("item_rerender", "Re-renders the target's held item."),

        /** Repair by a number of damage points. */
        REPAIR_AMOUNT("item_repair_amount", "Repairs the target's held item by an amount."),

        /** Add damage points. */
        DAMAGE("item_damage", "Damages the target's held item by an amount."),

        /** Set the damage value directly. */
        SET_DAMAGE("item_set_damage", "Sets the damage value on the target's held item."),

        /** Set the remaining durability, the inverse of damage. */
        SET_DURABILITY("item_set_durability", "Sets the remaining durability on the target's held item.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        /** {@return the pipeline stage id} */
        public String id() {
            return id;
        }
    }

    private final EmakiItemPlugin plugin;
    private final Operation operation;

    /**
     * Creates a stage.
     *
     * @param plugin owning plugin, source of the item services
     * @param operation which mutation this instance performs
     */
    public ItemHeldItemStage(@NotNull EmakiItemPlugin plugin, @NotNull Operation operation) {
        this.plugin = plugin;
        this.operation = operation;
    }

    @Override
    public @NotNull String id() {
        return operation.id;
    }

    @Override
    public @NotNull String description() {
        return operation.description;
    }

    @Override
    public @NotNull String category() {
        return "item";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return switch (operation) {
            case UPDATE, RERENDER -> List.of();
            case REPAIR_AMOUNT, DAMAGE -> List.of(CoreStageParameter.required("amount",
                    CoreStageParameterType.INTEGER, "Damage points"));
            case SET_DAMAGE, SET_DURABILITY -> List.of(CoreStageParameter.required("value",
                    CoreStageParameterType.INTEGER, "Target damage or durability value"));
        };
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (plugin.repairService() == null) {
            // Shares the builtin item stages' key: the message is generic enough to fit either owner, and
            // adding a near-duplicate would leave two strings to keep in sync.
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.v2.stage.item.service_unavailable");
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.v2.stage.common.not_player");
        }
        ItemStack original = target.getInventory().getItemInMainHand();
        if (original == null || original.getType().isAir()) {
            return CoreActionOutcome.skipped("action.v2.stage.item.empty_hand");
        }
        ItemStack updated = apply(original, arguments);
        if (updated == null || updated.getType().isAir()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.v2.stage.item.no_result");
        }
        boolean changed = updated != original;
        if (changed) {
            target.getInventory().setItemInMainHand(updated);
        }
        return CoreActionOutcome.success(Map.of(
                "changed", changed,
                "damage", plugin.repairService().currentDamage(updated),
                "max_damage", plugin.repairService().maxDamage(updated)));
    }

    private ItemStack apply(ItemStack original, CoreResolvedArguments arguments) {
        return switch (operation) {
            case UPDATE -> plugin.updateService() == null ? null : plugin.updateService().forceUpdate(original);
            case RERENDER -> plugin.coreLib() == null || plugin.coreLib().itemAssemblyService() == null
                    ? null
                    : plugin.coreLib().itemAssemblyService().rebuild(original);
            case REPAIR_AMOUNT -> repair(original, arguments.getInt("amount", 0));
            case DAMAGE -> damage(original, arguments.getInt("amount", 0));
            case SET_DAMAGE -> setDamage(original, arguments.getInt("value", 0));
            case SET_DURABILITY -> setDurability(original, arguments.getInt("value", 0));
        };
    }

    private ItemStack repair(ItemStack itemStack, int amount) {
        plugin.repairService().applyRepair(itemStack, Math.max(0, amount));
        return itemStack;
    }

    private ItemStack damage(ItemStack itemStack, int amount) {
        return setDamage(itemStack, plugin.repairService().currentDamage(itemStack) + Math.max(0, amount));
    }

    private ItemStack setDurability(ItemStack itemStack, int durability) {
        int maxDamage = plugin.repairService().maxDamage(itemStack);
        if (maxDamage <= 0) {
            return itemStack;
        }
        return setDamage(itemStack, maxDamage - Math.max(0, durability));
    }

    /**
     * Writes a clamped damage value.
     *
     * <p>Clearing the disabled flag below maximum damage is carried over from v1: an item that was marked
     * broken has to become usable again once it is no longer at full damage, otherwise repairing it leaves it
     * inert.</p>
     */
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

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
