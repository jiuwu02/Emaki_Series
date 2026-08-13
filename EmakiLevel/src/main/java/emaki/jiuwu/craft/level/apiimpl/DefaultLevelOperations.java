package emaki.jiuwu.craft.level.apiimpl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.api.LevelOperationResult;
import emaki.jiuwu.craft.level.api.LevelOperations;
import emaki.jiuwu.craft.level.api.LevelUpCause;
import emaki.jiuwu.craft.level.model.LevelFailureReason;

/** Default synchronous owner-thread operation adapter. */
public final class DefaultLevelOperations implements LevelOperations {

    private final EmakiLevelPlugin plugin;

    public DefaultLevelOperations(EmakiLevelPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public EmakiResult<LevelOperationResult> addExp(UUID uuid,
            String typeId,
            double amount,
            String reason) {
        return addExp(uuid, typeId, amount, reason, false);
    }

    @Override
    public EmakiResult<LevelOperationResult> addExp(UUID uuid,
            String typeId,
            double amount,
            String reason,
            boolean silent) {
        EmakiResult<LevelOperationResult> input = validateAmount(typeId, amount, false);
        if (input != null) {
            return input;
        }
        return execute(uuid, () -> plugin.levelService().addExp(uuid, typeId, amount, reason, null, silent));
    }

    @Override
    public EmakiResult<LevelOperationResult> removeExp(UUID uuid,
            String typeId,
            double amount,
            String reason) {
        EmakiResult<LevelOperationResult> input = validateAmount(typeId, amount, false);
        if (input != null) {
            return input;
        }
        return execute(uuid, () -> plugin.levelService().removeExp(uuid, typeId, amount, reason));
    }

    @Override
    public EmakiResult<LevelOperationResult> setExp(UUID uuid,
            String typeId,
            double amount,
            String reason) {
        EmakiResult<LevelOperationResult> input = validateAmount(typeId, amount, true);
        if (input != null) {
            return input;
        }
        return execute(uuid, () -> plugin.levelService().setExp(uuid, typeId, amount, reason));
    }

    @Override
    public EmakiResult<LevelOperationResult> addLevel(UUID uuid,
            String typeId,
            int amount,
            String reason) {
        EmakiResult<LevelOperationResult> input = validateLevelAmount(typeId, amount, false);
        if (input != null) {
            return input;
        }
        return execute(uuid, () -> plugin.levelService().addLevel(uuid, typeId, amount, reason));
    }

    @Override
    public EmakiResult<LevelOperationResult> removeLevel(UUID uuid,
            String typeId,
            int amount,
            String reason) {
        EmakiResult<LevelOperationResult> input = validateLevelAmount(typeId, amount, false);
        if (input != null) {
            return input;
        }
        return execute(uuid, () -> plugin.levelService().removeLevel(uuid, typeId, amount, reason));
    }

    @Override
    public EmakiResult<LevelOperationResult> setLevel(UUID uuid,
            String typeId,
            int level,
            String reason) {
        EmakiResult<LevelOperationResult> input = validateLevelAmount(typeId, level, true);
        if (input != null) {
            return input;
        }
        return execute(uuid, () -> plugin.levelService().setLevel(uuid, typeId, level, reason));
    }

    @Override
    public EmakiResult<LevelOperationResult> levelUp(UUID uuid, String typeId, LevelUpCause cause) {
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("level.type_id_required");
        }
        LevelUpCause resolvedCause = cause == null ? LevelUpCause.API : cause;
        return execute(uuid, () -> plugin.levelService().levelUp(uuid, typeId, resolvedCause));
    }

    @Override
    public EmakiResult<LevelOperationResult> reset(UUID uuid, String typeId) {
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("level.type_id_required");
        }
        return execute(uuid, () -> plugin.levelService().reset(uuid, typeId));
    }

    @Override
    public EmakiResult<Unit> syncPlayer(Player player) {
        EmakiResult<Player> ownership = ownedPlayer(player);
        if (ownership.isFailure()) {
            return ownership.retypeFailure();
        }
        if (plugin.dataStore().cached(player.getUniqueId()) == null) {
            return EmakiResult.notFound("level.player_data_not_found");
        }
        try {
            plugin.levelService().syncPlayer(player);
            return EmakiResult.ok();
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("level.player_sync_failed");
        }
    }

    @Override
    public EmakiResult<Unit> openGui(Player player, String typeId) {
        EmakiResult<Player> validation = validateGui(player, typeId);
        if (validation.isFailure()) {
            return validation.retypeFailure();
        }
        try {
            return plugin.levelGuiService().open(player, typeId)
                    ? EmakiResult.ok()
                    : EmakiResult.rejected("level.gui_open_failed");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("level.gui_open_failed");
        }
    }

    @Override
    public EmakiResult<Unit> openTopGui(Player player, String typeId) {
        EmakiResult<Player> validation = validateGui(player, typeId);
        if (validation.isFailure()) {
            return validation.retypeFailure();
        }
        try {
            return plugin.levelTopGuiService().open(player, typeId)
                    ? EmakiResult.ok()
                    : EmakiResult.rejected("level.top_gui_open_failed");
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("level.top_gui_open_failed");
        }
    }

    private EmakiResult<LevelOperationResult> execute(UUID uuid, Supplier<LevelOperationResult> operation) {
        EmakiResult<Player> ownership = ownedPlayer(uuid);
        if (ownership.isFailure()) {
            return ownership.retypeFailure();
        }
        try {
            return map(operation.get());
        } catch (RuntimeException exception) {
            return EmakiResult.internalError("level.operation_failed");
        }
    }

    private EmakiResult<Player> validateGui(Player player, String typeId) {
        EmakiResult<Player> ownership = ownedPlayer(player);
        if (ownership.isFailure()) {
            return ownership;
        }
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("level.type_id_required");
        }
        if (plugin.typeRegistry().type(typeId).isEmpty()) {
            return EmakiResult.notFound("level.type_not_found");
        }
        if (!plugin.appConfig().guiEnabled()) {
            return EmakiResult.rejected("level.gui_disabled");
        }
        return ownership;
    }

    private EmakiResult<Player> ownedPlayer(UUID uuid) {
        if (uuid == null) {
            return EmakiResult.invalidInput("level.player_uuid_required");
        }
        return ownedPlayer(Bukkit.getPlayer(uuid));
    }

    /**
     * {@return the validated owned player, or a failure describing why the call cannot proceed}
     *
     * <p>Every operation in this class funnels through here, which is why the readiness check lives
     * here: all of them resolve a level type against the loaded type table and write through
     * {@code levelService}, so running one mid-reload would apply a change against data that is about to
     * be replaced.</p>
     *
     * @param player the target player
     */
    private EmakiResult<Player> ownedPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (plugin == null || plugin.scheduling() == null || !plugin.scheduling().ownsEntity(player)) {
            return EmakiResult.wrongThread();
        }
        if (!plugin.contentReady()) {
            return EmakiResult.unavailable();
        }
        return EmakiResult.success(player);
    }

    private static EmakiResult<LevelOperationResult> validateAmount(String typeId,
            double amount,
            boolean allowZero) {
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("level.type_id_required");
        }
        if (!Double.isFinite(amount) || (allowZero ? amount < 0D : amount <= 0D)) {
            return EmakiResult.invalidInput("level.amount_invalid");
        }
        return null;
    }

    private static EmakiResult<LevelOperationResult> validateLevelAmount(String typeId,
            int amount,
            boolean allowZero) {
        if (Texts.isBlank(typeId)) {
            return EmakiResult.invalidInput("level.type_id_required");
        }
        if (allowZero ? amount < 0 : amount <= 0) {
            return EmakiResult.invalidInput("level.amount_invalid");
        }
        return null;
    }

    private static EmakiResult<LevelOperationResult> map(LevelOperationResult result) {
        if (result == null) {
            return EmakiResult.internalError("level.operation_no_result");
        }
        if (result.success()) {
            return EmakiResult.success(result);
        }
        FailureKind kind = switch (result.reason()) {
            case LevelFailureReason.TYPE_NOT_FOUND, LevelFailureReason.PLAYER_DATA_UNAVAILABLE -> FailureKind.NOT_FOUND;
            case LevelFailureReason.PLAYER_NOT_FOUND -> FailureKind.TARGET_OFFLINE;
            case LevelFailureReason.INVALID_AMOUNT -> FailureKind.INVALID_INPUT;
            case LevelFailureReason.EVENT_CANCELLED -> FailureKind.CANCELLED;
            case LevelFailureReason.COST_COMPENSATION_FAILED -> FailureKind.INTERNAL_ERROR;
            default -> FailureKind.REJECTED;
        };
        Map<String, Object> placeholders = new LinkedHashMap<>(result.data());
        placeholders.put("type_id", result.typeId());
        return EmakiResult.failure(kind, "level." + result.reason(), placeholders);
    }
}
