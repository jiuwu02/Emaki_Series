package emaki.jiuwu.craft.corelib.action.builtin.stage;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.action.builtin.BaseStage;
import emaki.jiuwu.craft.corelib.action.builtin.StageSupport;
import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionDomain;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;

public final class DamageItemStage extends BaseStage {

    public DamageItemStage() {
        super("damage_item", "item", "Adds durability damage to an item in one of the target's slots.",
                CoreTargetRequirement.REQUIRED_ENTITY, CoreActionExecutionDomain.CONTEXT_ENTITY,
                CoreStageParameter.optional("slot", CoreStageParameterType.STRING, "mainhand",
                        "Inventory slot"),
                CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1",
                        "Damage points to add"),
                CoreStageParameter.optional("delete_item", CoreStageParameterType.BOOLEAN, "false",
                        "Remove the item when damage reaches max durability"));
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        Player target = StageSupport.player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        StageSupport.Slot slot = StageSupport.slot(arguments.getString("slot"), "mainhand");
        if (slot == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.item.unknown_slot", Map.of("slot", arguments.getString("slot")));
        }
        ItemStack itemStack = slot.get(target.getInventory());
        if (StageSupport.isEmpty(itemStack)) {
            return CoreActionOutcome.skipped("action.stage.item.slot_empty");
        }
        int maxDurability = itemStack.getType().getMaxDurability();
        if (maxDurability <= 0) {
            return CoreActionOutcome.skipped("action.stage.item.no_durability");
        }
        ItemMeta meta = itemStack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return CoreActionOutcome.skipped("action.stage.item.not_damageable");
        }
        int amount = Math.max(0, arguments.getInt("amount", 1));
        if (amount <= 0) {
            return CoreActionOutcome.skipped("action.stage.item.zero_damage");
        }
        int before = damageable.getDamage();
        int requested = before + amount;
        if (requested >= maxDurability && arguments.getBoolean("delete_item", false)) {
            slot.clear(target.getInventory());
            return CoreActionOutcome.success(Map.of(
                    "slot", slot.id(),
                    "damage_before", before,
                    "damage_after", maxDurability,
                    "deleted", true));
        }
        int after = Math.min(Math.max(0, maxDurability - 1), requested);
        damageable.setDamage(after);
        itemStack.setItemMeta(meta);
        slot.set(target.getInventory(), itemStack);
        return CoreActionOutcome.success(Map.of(
                "slot", slot.id(),
                "damage_before", before,
                "damage_after", after,
                "deleted", false));
    }
}
