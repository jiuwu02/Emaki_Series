package emaki.jiuwu.craft.cooking.api;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;

/**
 * State-changing cooking operations.
 *
 * <p>Reached through {@code EmakiCookingApi.operations()}.
 *
 * <h2>Deliberately small</h2>
 * EmakiCooking drives its stations from block interaction listeners and per-station tick loops, not from
 * a callable "run this recipe" entry point. Its reward delivery call returns {@code void}, completes on
 * an async chain, and swallows its own exceptions, so wrapping it would mean reporting success for work
 * that may silently fail. Rather than offer a dishonest result, this layer exposes only operations whose
 * outcome can be stated truthfully. Nutrition writes live on
 * {@link CookingNutrition}.
 *
 * <h2>Threading</h2>
 * Both methods send messages to a player or read their held item and must be called on the target
 * player's owner thread.
 */
@ApiStatus.NonExtendable
public interface CookingOperations {

    /**
     * Sends the recipient a breakdown of the nutrition data carried by the player's held item.
     *
     * <p>This is a presentation helper: it renders through EmakiCooking's own message templates and
     * language files rather than returning structured data.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param recipient who receives the output; may differ from the inspected player
     * @param player    the player whose held item is inspected
     * @return success when the report was sent, or a failure when the player is missing or the subsystem
     *         is unavailable
     */
    @NotNull
    EmakiResult<Unit> inspectHeldItem(@Nullable CommandSender recipient, @Nullable Player player);

    /**
     * Sends the recipient a breakdown of the cooking station the player is looking at.
     *
     * <p><strong>Thread:</strong> the target player's owner thread.
     *
     * @param recipient who receives the output
     * @param player    the player whose target block is inspected
     * @return success when the report was sent, or a failure when the player is missing or the subsystem
     *         is unavailable
     */
    @NotNull
    EmakiResult<Unit> inspectTargetStation(@Nullable CommandSender recipient, @Nullable Player player);
}
