package emaki.jiuwu.craft.item.action;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.action.CoreActionContext;
import emaki.jiuwu.craft.corelib.api.action.CoreActionErrorType;
import emaki.jiuwu.craft.corelib.api.action.CoreActionItemTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionResult;

final class ItemActionTargetResolver {

    private static final String[] COMPATIBILITY_KEYS = {
            "item", "itemStack", "item_stack", "resultItem", "result_item", "targetItem", "target_item"
    };

    Resolution resolve(CoreActionContext context, Map<String, String> arguments) {
        CoreActionContext safeContext = context == null
                ? CoreActionContext.create(null, null, "default", false)
                : context;
        Map<String, String> safeArguments = arguments == null ? Map.of() : arguments;

        if (safeArguments.containsKey("slot")) {
            String rawSlot = safeArguments.get("slot");
            if (rawSlot == null || rawSlot.trim().isEmpty()) {
                return Resolution.failure(CoreActionErrorType.INVALID_ARGUMENT,
                        "Explicit component action slot cannot be blank.");
            }
            ItemInventorySlot slot = ItemInventorySlot.parse(rawSlot);
            if (slot == null) {
                return Resolution.failure(CoreActionErrorType.INVALID_ARGUMENT,
                        "Unsupported component action slot: " + rawSlot);
            }
            Player player = safeContext.player();
            if (player == null) {
                return Resolution.failure(CoreActionErrorType.INVALID_STATE,
                        "Explicit component action slot requires a player context.");
            }
            return Resolution.success(new InventoryTarget(player, slot));
        }

        CoreActionItemTarget standardTarget = standardTarget(safeContext);
        if (standardTarget != null) {
            return Resolution.success(new HolderTarget(standardTarget));
        }

        for (String key : COMPATIBILITY_KEYS) {
            ItemStack shared = itemStack(safeContext.sharedValue(key));
            if (shared != null) {
                return Resolution.success(new RawContextTarget(safeContext, key, shared));
            }
            ItemStack attribute = itemStack(safeContext.attribute(key));
            if (attribute != null) {
                return Resolution.success(new RawContextTarget(safeContext, key, attribute));
            }
        }

        Player player = safeContext.player();
        if (player == null) {
            return Resolution.failure(CoreActionErrorType.INVALID_STATE,
                    "Component action requires a player or item target context.");
        }
        return Resolution.success(new InventoryTarget(player, ItemInventorySlot.parse("mainhand")));
    }

    private CoreActionItemTarget standardTarget(CoreActionContext context) {
        Object shared = context.sharedValue(CoreActionItemTarget.ATTRIBUTE_KEY);
        if (shared instanceof CoreActionItemTarget target) {
            return target;
        }
        Object attribute = context.attribute(CoreActionItemTarget.ATTRIBUTE_KEY);
        return attribute instanceof CoreActionItemTarget target ? target : null;
    }

    private ItemStack itemStack(Object value) {
        return value instanceof ItemStack itemStack ? itemStack : null;
    }

    interface Target {
        String id();

        ItemStack itemStack();

        void commit(ItemStack itemStack);
    }

    record Resolution(Target target, CoreActionResult failure) {

        static Resolution success(Target target) {
            return new Resolution(target, null);
        }

        static Resolution failure(CoreActionErrorType errorType, String message) {
            return new Resolution(null, CoreActionResult.failure(errorType, message));
        }

        boolean resolved() {
            return target != null;
        }
    }

    private record InventoryTarget(Player player, ItemInventorySlot slot) implements Target {

        @Override
        public String id() {
            return slot.id();
        }

        @Override
        public ItemStack itemStack() {
            return slot.get(player.getInventory());
        }

        @Override
        public void commit(ItemStack itemStack) {
            slot.set(player.getInventory(), itemStack);
        }
    }

    private record HolderTarget(CoreActionItemTarget holder) implements Target {

        @Override
        public String id() {
            return "context:" + CoreActionItemTarget.ATTRIBUTE_KEY;
        }

        @Override
        public ItemStack itemStack() {
            return holder.itemStack();
        }

        @Override
        public void commit(ItemStack itemStack) {
            holder.setItemStack(itemStack);
        }
    }

    private record RawContextTarget(CoreActionContext context, String key, ItemStack original) implements Target {

        @Override
        public String id() {
            return "context:" + key;
        }

        @Override
        public ItemStack itemStack() {
            return original.clone();
        }

        @Override
        public void commit(ItemStack itemStack) {
            ItemStack updated = itemStack.clone();
            original.copyDataFrom(updated, _ -> true);
            original.setAmount(updated.getAmount());
            context.sharedState().put(key, updated);
        }
    }
}
