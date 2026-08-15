package emaki.jiuwu.craft.storage.action;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import emaki.jiuwu.craft.corelib.api.action.CoreActionExecutionTarget;
import emaki.jiuwu.craft.corelib.api.action.CoreActionFailureKind;
import emaki.jiuwu.craft.corelib.api.action.CoreActionOutcome;
import emaki.jiuwu.craft.corelib.api.action.CoreActionStage;
import emaki.jiuwu.craft.corelib.api.action.CoreActionSubject;
import emaki.jiuwu.craft.corelib.api.action.CoreResolvedArguments;
import emaki.jiuwu.craft.corelib.api.action.CoreStageContext;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameter;
import emaki.jiuwu.craft.corelib.api.action.CoreStageParameterType;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.storage.EmakiStoragePlugin;
import emaki.jiuwu.craft.storage.log.StorageLogEntry;
import emaki.jiuwu.craft.storage.log.StorageOperationSource;
import emaki.jiuwu.craft.storage.log.StorageOperationType;
import emaki.jiuwu.craft.storage.model.PlayerStorage;
import emaki.jiuwu.craft.storage.model.StorageEntry;
import emaki.jiuwu.craft.storage.model.StorageKey;
import emaki.jiuwu.craft.storage.model.StorageResult;

public final class StorageStage implements CoreActionStage {

    public enum Operation {

        DEPOSIT("storage_deposit", "Stores items into the target's warehouse."),

        WITHDRAW("storage_withdraw", "Withdraws items from the target's warehouse."),

        GRANT_SLOT("storage_grant_slot", "Grants or reclaims warehouse slots for the target."),

        UNLOCK_SLOT("storage_unlock_slot", "Adds purchased warehouse slots without charging the target."),

        SET_STACK_LIMIT("storage_set_stacklimit", "Sets the target's warehouse stack ceiling.");

        private final String id;
        private final String description;

        Operation(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String id() {
            return id;
        }
    }

    private final EmakiStoragePlugin plugin;
    private final Operation operation;

    public StorageStage(@NotNull EmakiStoragePlugin plugin, @NotNull Operation operation) {
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
        return "storage";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return switch (operation) {
            case DEPOSIT -> List.of(
                    CoreStageParameter.required("item", CoreStageParameterType.STRING, "ItemSource token"),
                    CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1",
                            "Units to store"));
            case WITHDRAW -> List.of(
                    CoreStageParameter.required("item", CoreStageParameterType.STRING, "ItemSource token"),
                    CoreStageParameter.optional("amount", CoreStageParameterType.INTEGER, "1",
                            "Units to withdraw"));
            case GRANT_SLOT -> List.of(CoreStageParameter.required("amount",
                    CoreStageParameterType.INTEGER, "Slots to grant; negative reclaims"));
            case UNLOCK_SLOT -> List.of(CoreStageParameter.required("amount",
                    CoreStageParameterType.INTEGER, "Slots to unlock"));
            case SET_STACK_LIMIT -> List.of(
                    CoreStageParameter.required("limit", CoreStageParameterType.INTEGER,
                            "New ceiling; 0 inherits the next level"),
                    CoreStageParameter.optional("slot", CoreStageParameterType.INTEGER, "-1",
                            "Logical slot index, -1 for the player default"));
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
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        if (plugin.dataStore() == null) {
            return unavailable();
        }
        PlayerStorage storage = plugin.dataStore().cached(target.getUniqueId());
        if (storage == null) {
            return unavailable();
        }
        return switch (operation) {
            case DEPOSIT -> deposit(storage, target, arguments);
            case WITHDRAW -> withdraw(storage, target, arguments);
            case GRANT_SLOT -> grantSlot(storage, arguments);
            case UNLOCK_SLOT -> unlockSlot(storage, arguments);
            case SET_STACK_LIMIT -> setStackLimit(storage, arguments);
        };
    }

    private CoreActionOutcome deposit(PlayerStorage storage, Player target, CoreResolvedArguments arguments) {
        if (plugin.capacityService() == null || plugin.transactionService() == null
                || plugin.storageGuiService() == null) {
            return unavailable();
        }
        ItemStack template = resolveItem(arguments.getString("item"));
        if (template == null) {
            return unknownItem(arguments.getString("item"));
        }
        long amount = arguments.getInt("amount", 1);
        if (amount <= 0L) {
            return invalid("action.stage.storage.amount_must_be_positive");
        }
        var capacity = plugin.capacityService().capacityOf(storage, target,
                plugin.storageGuiService().slotsPerPage());
        StorageResult result = plugin.transactionService().depositDirect(storage, target,
                capacity, template, amount, StorageOperationSource.ACTION);
        return result.applied()
                ? CoreActionOutcome.success(Map.of("stored", result.appliedAmount()))
                : rejected(result.reasonKey());
    }

    private CoreActionOutcome withdraw(PlayerStorage storage, Player target, CoreResolvedArguments arguments) {
        if (plugin.transactionService() == null) {
            return unavailable();
        }
        ItemStack template = resolveItem(arguments.getString("item"));
        if (template == null) {
            return unknownItem(arguments.getString("item"));
        }
        long amount = arguments.getInt("amount", 1);
        if (amount <= 0L) {
            return invalid("action.stage.storage.amount_must_be_positive");
        }
        StorageResult result = plugin.transactionService().withdraw(storage, target,
                StorageKey.of(template), amount, StorageOperationSource.ACTION);
        return result.applied()
                ? CoreActionOutcome.success(Map.of("withdrawn", result.appliedAmount()))
                : rejected(result.reasonKey());
    }

    private CoreActionOutcome grantSlot(PlayerStorage storage, CoreResolvedArguments arguments) {
        int delta = arguments.getInt("amount", 0);
        if (delta == 0) {
            return invalid("action.stage.storage.amount_must_be_non_zero");
        }
        storage.grantedSlots(storage.grantedSlots() + delta);
        storage.markDirty();
        record(storage, StorageOperationType.ADMIN_GIVE, null,
                (delta >= 0 ? "+" : "") + delta + "slots", storage.grantedSlots(), null);
        return CoreActionOutcome.success(Map.of("granted_slots", storage.grantedSlots()));
    }

    private CoreActionOutcome unlockSlot(PlayerStorage storage, CoreResolvedArguments arguments) {
        int slots = arguments.getInt("amount", 0);
        if (slots <= 0) {
            return invalid("action.stage.storage.amount_must_be_positive");
        }
        storage.purchasedSlots(storage.purchasedSlots() + slots);
        storage.markDirty();
        record(storage, StorageOperationType.UNLOCK, null, "+" + slots + "slots",
                storage.purchasedSlots(), "cost=none");
        return CoreActionOutcome.success(Map.of("purchased_slots", storage.purchasedSlots()));
    }

    private CoreActionOutcome setStackLimit(PlayerStorage storage, CoreResolvedArguments arguments) {
        long limit = arguments.getInt("limit", -1);
        if (limit < 0L) {
            return invalid("action.stage.storage.limit_must_be_non_negative");
        }
        int slot = arguments.getInt("slot", -1);
        if (slot < 0) {
            storage.defaultStackLimit(limit);
            storage.markDirty();
            record(storage, StorageOperationType.ADMIN_SET, null, "=" + limit, limit,
                    "field=default_stack_limit");
            return CoreActionOutcome.success(Map.of("default_stack_limit", limit));
        }
        StorageEntry entry = storage.entryAt(slot);
        if (entry == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                    "action.stage.storage.no_entry_at_slot", Map.of("slot", slot));
        }
        entry.stackLimit(limit);
        storage.markDirty();
        record(storage, StorageOperationType.ADMIN_SET,
                plugin.textIndexer() == null ? null : plugin.textIndexer().identifierOf(entry.key()),
                "=" + limit, limit, "field=slot_stack_limit slot=" + slot);
        return CoreActionOutcome.success(Map.of("slot", slot, "stack_limit", limit));
    }

    private void record(PlayerStorage storage,
            StorageOperationType type,
            String identifier,
            String delta,
            long resulting,
            String detail) {
        if (plugin.operationLog() == null) {
            return;
        }
        plugin.operationLog().record(StorageLogEntry.raw(storage.playerId(), type, identifier, delta,
                resulting, StorageOperationSource.ACTION, detail));
    }

    private ItemStack resolveItem(String token) {
        ItemSourceRef source = ItemSourceUtil.parse(token);
        if (source == null || plugin.coreLib() == null || plugin.coreLib().itemSourceService() == null) {
            return null;
        }
        return plugin.coreLib().itemSourceService().createItem(source, 1);
    }

    private static CoreActionOutcome unavailable() {
        return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                "action.stage.storage.unavailable");
    }

    private static CoreActionOutcome unknownItem(String token) {
        return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG,
                "action.stage.storage.unknown_item", Map.of("item", String.valueOf(token)));
    }

    private static CoreActionOutcome invalid(String reasonKey) {
        return CoreActionOutcome.failure(CoreActionFailureKind.INVALID_CONFIG, reasonKey);
    }

    private static CoreActionOutcome rejected(String reasonKey) {
        return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                "action.stage.storage.rejected", Map.of("reason", String.valueOf(reasonKey)));
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
