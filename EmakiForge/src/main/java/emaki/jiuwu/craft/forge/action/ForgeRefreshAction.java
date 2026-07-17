package emaki.jiuwu.craft.forge.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.action.Action;
import emaki.jiuwu.craft.corelib.action.ActionContext;
import emaki.jiuwu.craft.corelib.action.ActionErrorType;
import emaki.jiuwu.craft.corelib.action.ActionExecutionTarget;
import emaki.jiuwu.craft.corelib.action.ActionParameter;
import emaki.jiuwu.craft.corelib.action.ActionPlanningContext;
import emaki.jiuwu.craft.corelib.action.ActionResult;
import emaki.jiuwu.craft.corelib.async.FoliaSchedulerAdapter;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;

public final class ForgeRefreshAction implements Action {

    enum Operation {
        HELD_ITEM,
        PLAYER_INVENTORY,
        ONLINE_PLAYERS
    }

    private final EmakiForgePlugin plugin;
    private final String id;
    private final Operation operation;

    ForgeRefreshAction(EmakiForgePlugin plugin, String id, Operation operation) {
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
        return switch (operation) {
            case HELD_ITEM -> "Refresh the action player's held forged item through EmakiForge refresh service.";
            case PLAYER_INVENTORY -> "Refresh the action player's forged inventory items through EmakiForge refresh service.";
            case ONLINE_PLAYERS -> "Refresh forged items for all online players through EmakiForge refresh service.";
        };
    }

    @Override
    public String category() {
        return "emakiforge";
    }

    @Override
    public List<ActionParameter> parameters() {
        return List.of();
    }

    @Override
    public ActionExecutionTarget executionTarget(ActionPlanningContext context) {
        return operation == Operation.ONLINE_PLAYERS
                ? ActionExecutionTarget.global()
                : Action.super.executionTarget(context);
    }

    @Override
    public CompletionStage<ActionResult> executeAsync(ActionContext context, Map<String, String> arguments) {
        if (operation != Operation.ONLINE_PLAYERS) {
            return Action.super.executeAsync(context, arguments);
        }
        if (plugin == null || plugin.itemRefreshService() == null) {
            return CompletableFuture.completedFuture(ActionResult.failure(
                    ActionErrorType.INVALID_STATE,
                    "EmakiForge refresh service is not ready."));
        }
        return refreshOnlinePlayersAsync();
    }

    @Override
    public ActionResult execute(ActionContext context, Map<String, String> arguments) {
        if (plugin == null || plugin.itemRefreshService() == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "EmakiForge refresh service is not ready.");
        }
        if (operation == Operation.ONLINE_PLAYERS) {
            return ActionResult.failure(
                    ActionErrorType.INVALID_STATE,
                    "Online player refresh requires asynchronous action execution.");
        }
        Player player = context == null ? null : context.player();
        if (player == null) {
            return ActionResult.failure(ActionErrorType.INVALID_STATE, "Action '" + id + "' requires a player context.");
        }
        if (operation == Operation.PLAYER_INVENTORY) {
            plugin.itemRefreshService().refreshPlayerInventory(player);
            return ActionResult.ok(Map.of("player", player.getName()));
        }
        ItemStack original = player.getInventory().getItemInMainHand();
        if (original == null || original.getType().isAir()) {
            return ActionResult.skipped("Player is not holding an item.");
        }
        ItemStack refreshed = plugin.itemRefreshService().refreshItem(original);
        boolean changed = refreshed != original;
        if (changed) {
            player.getInventory().setItemInMainHand(refreshed);
        }
        return ActionResult.ok(Map.of("changed", changed));
    }

    private CompletionStage<ActionResult> refreshOnlinePlayersAsync() {
        List<UUID> playerIds = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .toList();
        if (playerIds.isEmpty()) {
            return CompletableFuture.completedFuture(ActionResult.ok(Map.of("players", 0, "refreshed", 0)));
        }
        List<CompletableFuture<Boolean>> refreshes = new ArrayList<>(playerIds.size());
        for (UUID playerId : playerIds) {
            CompletableFuture<Boolean> refresh = new CompletableFuture<>();
            refreshes.add(refresh);
            Player owner = Bukkit.getPlayer(playerId);
            if (owner == null || !owner.isOnline()) {
                refresh.complete(false);
                continue;
            }
            try {
                FoliaSchedulerAdapter.runEntityTask(plugin, owner, () -> {
                    try {
                        if (!owner.isOnline() || !owner.getUniqueId().equals(playerId)) {
                            refresh.complete(false);
                            return;
                        }
                        plugin.itemRefreshService().refreshPlayerInventory(owner);
                        refresh.complete(true);
                    } catch (RuntimeException exception) {
                        refresh.completeExceptionally(exception);
                    }
                });
            } catch (RuntimeException exception) {
                refresh.completeExceptionally(exception);
            }
        }
        CompletableFuture<?>[] stages = refreshes.stream()
                .map(stage -> stage.exceptionally(throwable -> false))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(stages).thenApply(ignored -> {
            long refreshed = refreshes.stream()
                    .filter(stage -> Boolean.TRUE.equals(stage.exceptionally(throwable -> false).join()))
                    .count();
            return ActionResult.ok(Map.of("players", playerIds.size(), "refreshed", refreshed));
        });
    }
}
