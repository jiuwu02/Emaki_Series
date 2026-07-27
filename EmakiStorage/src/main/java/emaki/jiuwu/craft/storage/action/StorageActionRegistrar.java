package emaki.jiuwu.craft.storage.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionParameterType;
import emaki.jiuwu.craft.corelib.action.ActionRegistry;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.api.model.StorageResult;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;

/**
 * Registers the module's CoreLib actions so other modules can grant capacity or move items.
 *
 * <p>Registration is scoped to this plugin and a stable source string, which is what makes reload
 * and disable cleanup possible without leaking stale action instances.
 */
public final class StorageActionRegistrar {

    private static final String SOURCE = "emakistorage";
    private static final String CATEGORY = "storage";

    private final EmakiStoragePlugin plugin;

    public StorageActionRegistrar(EmakiStoragePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers every action.
     *
     * @param registry the CoreLib action registry, may be {@code null} when unavailable
     */
    public void register(ActionRegistry registry) {
        if (registry == null) {
            return;
        }
        List<Action> actions = List.of(
                new DepositAction(),
                new WithdrawAction(),
                new GrantSlotAction(),
                new UnlockSlotAction(),
                new SetStackLimitAction());
        for (Action action : actions) {
            ActionResult result = registry.register(plugin, SOURCE, action);
            if (result != null && !result.success()) {
                plugin.getLogger().warning("[storage] Failed to register action '" + action.id()
                        + "': " + result.errorMessage());
            }
        }
    }

    /** Shared plumbing for the five actions. */
    private abstract class StorageAction implements Action {

        private final String id;
        private final String description;
        private final List<ActionParameter> parameters;

        StorageAction(String id, String description, ActionParameter... parameters) {
            this.id = id;
            this.description = description;
            this.parameters = parameters == null ? List.of() : List.of(parameters);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String category() {
            return CATEGORY;
        }

        @Override
        public List<ActionParameter> parameters() {
            return parameters;
        }

        /** {@return the loaded storage of the acting player, or {@code null} when unavailable} */
        PlayerStorage storageOf(ActionContext context) {
            Player player = context.player();
            return player == null ? null : plugin.dataStore().cached(player.getUniqueId());
        }

        ActionResult unavailable() {
            return ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION,
                    "Storage data is not loaded for this player.");
        }

        ActionResult invalid(String message) {
            return ActionResult.failure(ActionErrorType.INVALID_ARGUMENT, message);
        }

        long parseLong(Map<String, String> arguments, String key, long fallback) {
            String raw = arguments.get(key);
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        ItemStack resolveItem(String token) {
            ItemSource source = ItemSourceUtil.parse(token);
            if (source == null) {
                return null;
            }
            return plugin.coreLib().itemSourceService().createItem(source, 1);
        }
    }

    /** Adds items to the acting player's storage. */
    private final class DepositAction extends StorageAction {

        DepositAction() {
            super("storage-deposit", "Store items into a player's warehouse.",
                    ActionParameter.required("item", ActionParameterType.STRING, "ItemSource token"),
                    ActionParameter.optional("amount", ActionParameterType.INTEGER, "1", "Units to store"));
        }

        @Override
        public ActionResult execute(ActionContext context, Map<String, String> arguments) {
            PlayerStorage storage = storageOf(context);
            if (storage == null) {
                return unavailable();
            }
            ItemStack template = resolveItem(arguments.get("item"));
            if (template == null) {
                return invalid("Unknown item source: " + arguments.get("item"));
            }
            long amount = parseLong(arguments, "amount", 1L);
            if (amount <= 0L) {
                return invalid("amount must be positive");
            }
            var capacity = plugin.capacityService().capacityOf(storage, context.player(),
                    plugin.storageGuiService().slotsPerPage());
            StorageResult result = plugin.transactionService().depositDirect(storage, context.player(),
                    capacity, template, amount, StorageOperationSource.ACTION);
            return result.applied()
                    ? ActionResult.ok(Map.of("stored", result.appliedAmount()))
                    : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION,
                            "Deposit rejected: " + result.reasonKey());
        }
    }

    /** Removes items from the acting player's storage and hands them over. */
    private final class WithdrawAction extends StorageAction {

        WithdrawAction() {
            super("storage-withdraw", "Withdraw items from a player's warehouse.",
                    ActionParameter.required("item", ActionParameterType.STRING, "ItemSource token"),
                    ActionParameter.optional("amount", ActionParameterType.INTEGER, "1", "Units to withdraw"));
        }

        @Override
        public ActionResult execute(ActionContext context, Map<String, String> arguments) {
            PlayerStorage storage = storageOf(context);
            if (storage == null) {
                return unavailable();
            }
            ItemStack template = resolveItem(arguments.get("item"));
            if (template == null) {
                return invalid("Unknown item source: " + arguments.get("item"));
            }
            long amount = parseLong(arguments, "amount", 1L);
            if (amount <= 0L) {
                return invalid("amount must be positive");
            }
            StorageResult result = plugin.transactionService().withdraw(storage, context.player(),
                    StorageKey.of(template), amount, StorageOperationSource.ACTION);
            return result.applied()
                    ? ActionResult.ok(Map.of("withdrawn", result.appliedAmount()))
                    : ActionResult.failure(ActionErrorType.EXECUTION_EXCEPTION,
                            "Withdrawal rejected: " + result.reasonKey());
        }
    }

    /** Grants or reclaims slots, used by level and codex rewards. */
    private final class GrantSlotAction extends StorageAction {

        GrantSlotAction() {
            super("storage-grant-slot", "Grant or reclaim warehouse slots.",
                    ActionParameter.required("amount", ActionParameterType.INTEGER,
                            "Slots to grant; negative reclaims"));
        }

        @Override
        public ActionResult execute(ActionContext context, Map<String, String> arguments) {
            PlayerStorage storage = storageOf(context);
            if (storage == null) {
                return unavailable();
            }
            long amount = parseLong(arguments, "amount", 0L);
            if (amount == 0L) {
                return invalid("amount must be non-zero");
            }
            int delta = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, amount));
            storage.grantedSlots(storage.grantedSlots() + delta);
            storage.markDirty();
            plugin.operationLog().record(StorageLogEntry.raw(storage.playerId(),
                    StorageOperationType.ADMIN_GIVE, null,
                    (delta >= 0 ? "+" : "") + delta + "slots", storage.grantedSlots(),
                    StorageOperationSource.ACTION, null));
            return ActionResult.ok(Map.of("granted_slots", storage.grantedSlots()));
        }
    }

    /** Adds purchased slots without charging, for reward flows. */
    private final class UnlockSlotAction extends StorageAction {

        UnlockSlotAction() {
            super("storage-unlock-slot", "Add purchased warehouse slots without charging.",
                    ActionParameter.required("amount", ActionParameterType.INTEGER, "Slots to unlock"));
        }

        @Override
        public ActionResult execute(ActionContext context, Map<String, String> arguments) {
            PlayerStorage storage = storageOf(context);
            if (storage == null) {
                return unavailable();
            }
            long amount = parseLong(arguments, "amount", 0L);
            if (amount <= 0L) {
                return invalid("amount must be positive");
            }
            int slots = (int) Math.min(Integer.MAX_VALUE, amount);
            storage.purchasedSlots(storage.purchasedSlots() + slots);
            storage.markDirty();
            plugin.operationLog().record(StorageLogEntry.raw(storage.playerId(),
                    StorageOperationType.UNLOCK, null, "+" + slots + "slots",
                    storage.purchasedSlots(), StorageOperationSource.ACTION, "cost=none"));
            return ActionResult.ok(Map.of("purchased_slots", storage.purchasedSlots()));
        }
    }

    /** Sets the player-level or per-slot stack ceiling. */
    private final class SetStackLimitAction extends StorageAction {

        SetStackLimitAction() {
            super("storage-set-stacklimit", "Set the warehouse per-slot stack ceiling.",
                    ActionParameter.required("limit", ActionParameterType.INTEGER,
                            "New ceiling; 0 inherits the next level"),
                    ActionParameter.optional("slot", ActionParameterType.INTEGER, "-1",
                            "Logical slot index, -1 for the player default"));
        }

        @Override
        public ActionResult execute(ActionContext context, Map<String, String> arguments) {
            PlayerStorage storage = storageOf(context);
            if (storage == null) {
                return unavailable();
            }
            long limit = parseLong(arguments, "limit", -1L);
            if (limit < 0L) {
                return invalid("limit must be zero or positive");
            }
            int slot = (int) parseLong(arguments, "slot", -1L);
            if (slot < 0) {
                storage.defaultStackLimit(limit);
                storage.markDirty();
                plugin.operationLog().record(StorageLogEntry.raw(storage.playerId(),
                        StorageOperationType.ADMIN_SET, null, "=" + limit, limit,
                        StorageOperationSource.ACTION, "field=default_stack_limit"));
                return ActionResult.ok(Map.of("default_stack_limit", limit));
            }
            StorageEntry entry = storage.entryAt(slot);
            if (entry == null) {
                return invalid("No entry at slot " + slot);
            }
            entry.stackLimit(limit);
            storage.markDirty();
            plugin.operationLog().record(StorageLogEntry.raw(storage.playerId(),
                    StorageOperationType.ADMIN_SET,
                    plugin.textIndexer().identifierOf(entry.key()), "=" + limit, limit,
                    StorageOperationSource.ACTION, "field=slot_stack_limit slot=" + slot));
            return ActionResult.ok(Map.of("slot", slot, "stack_limit", limit));
        }
    }
}
