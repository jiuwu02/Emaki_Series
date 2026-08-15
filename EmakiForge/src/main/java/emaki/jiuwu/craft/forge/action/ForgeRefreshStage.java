package emaki.jiuwu.craft.forge.action;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
import emaki.jiuwu.craft.corelib.api.action.CoreTargetRequirement;
import emaki.jiuwu.craft.corelib.api.action.CoreStagePlanningContext;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;

public final class ForgeRefreshStage implements CoreActionStage {

    private static final long ONLINE_REFRESH_TIMEOUT_SECONDS = 20L;

    public enum Operation {

        HELD_ITEM("forge_refresh_held", "Re-renders the target's held forged item."),

        PLAYER_INVENTORY("forge_refresh_player", "Re-renders every forged item in the target's inventory."),

        ONLINE_PLAYERS("forge_refresh_all", "Re-renders forged items for every online player.");

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

    private final ForgeItemRefreshService refreshService;
    private final Operation operation;

    public ForgeRefreshStage(ForgeItemRefreshService refreshService, @NotNull Operation operation) {
        this.refreshService = refreshService;
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
        return "forge";
    }

    @Override
    public @NotNull List<CoreStageParameter> parameters() {
        return List.of();
    }

    @Override
    public @NotNull CoreTargetRequirement targetRequirement() {
        return operation == Operation.ONLINE_PLAYERS
                ? CoreTargetRequirement.NONE
                : CoreTargetRequirement.REQUIRED_ENTITY;
    }

    @Override
    public @NotNull CoreActionExecutionTarget executionTarget(@NotNull CoreStagePlanningContext context) {
        return operation == Operation.ONLINE_PLAYERS
                ? CoreActionExecutionTarget.global()
                : CoreActionExecutionTarget.contextEntity();
    }

    @Override
    public @NotNull CoreActionOutcome execute(@NotNull CoreStageContext context,
            @NotNull CoreResolvedArguments arguments) {
        if (refreshService == null) {
            return CoreActionOutcome.failure(CoreActionFailureKind.MISSING_CONTEXT,
                    "action.stage.forge.service_unavailable");
        }
        if (operation == Operation.ONLINE_PLAYERS) {
            return refreshOnlinePlayers();
        }
        Player target = player(context.currentTarget());
        if (target == null) {
            return CoreActionOutcome.skipped("action.stage.common.not_player");
        }
        return operation == Operation.PLAYER_INVENTORY
                ? refreshInventory(target)
                : refreshHeldItem(target);
    }

    private CoreActionOutcome refreshInventory(Player target) {
        refreshService.refreshPlayerInventory(target);
        return CoreActionOutcome.success(Map.of("player", target.getName()));
    }

    private CoreActionOutcome refreshHeldItem(Player target) {
        ItemStack original = target.getInventory().getItemInMainHand();
        if (original == null || original.getType().isAir()) {
            return CoreActionOutcome.skipped("action.stage.forge.empty_hand");
        }
        ItemStack refreshed = refreshService.refreshItem(original);
        boolean changed = refreshed != original;
        if (changed) {
            target.getInventory().setItemInMainHand(refreshed);
        }
        return CoreActionOutcome.success(Map.of("changed", changed));
    }

    private CoreActionOutcome refreshOnlinePlayers() {
        ForgeItemRefreshService.RefreshSummary summary;
        try {
            summary = refreshService.refreshOnlinePlayers()
                    .get(ONLINE_REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.forge.refresh_interrupted");
        } catch (TimeoutException exception) {
            return CoreActionOutcome.failure(CoreActionFailureKind.TIMEOUT,
                    "action.stage.forge.refresh_timeout");
        } catch (CompletionException | ExecutionException exception) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.forge.refresh_failed",
                    Map.of("error", String.valueOf(exception.getCause() == null
                            ? exception.getMessage() : exception.getCause().getMessage())));
        }
        Map<String, Object> data = Map.of(
                "players", summary.players(),
                "refreshed", summary.refreshed(),
                "skipped", summary.skipped(),
                "failed", summary.failed(),
                "stale", summary.stale());
        if (summary.stale()) {
            return CoreActionOutcome.failure(CoreActionFailureKind.REJECTED,
                    "action.stage.forge.generation_changed", data);
        }
        if (summary.failed() > 0) {
            return CoreActionOutcome.failure(CoreActionFailureKind.INTERNAL_ERROR,
                    "action.stage.forge.partial_refresh", data);
        }
        return CoreActionOutcome.success(data);
    }

    private static Player player(CoreActionSubject subject) {
        return subject != null && subject.entityOrNull() instanceof Player resolved ? resolved : null;
    }
}
