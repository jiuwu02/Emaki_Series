package emaki.jiuwu.craft.item.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.item.api.model.MigrationOutcome;
import emaki.jiuwu.craft.item.api.model.MigrationPreview;

/**
 * Administrative item-id migration operations.
 *
 * <p>Preview/apply perform synchronous filesystem work and must be called from a non-tick worker chosen by
 * the caller. Checked {@code IOException}s never cross this API: they map to {@code INTERNAL_ERROR}; if
 * some files were already committed, apply returns {@link EmakiResult.Partial} with the actual outcome.
 * Inventory migration requires entity ownership. {@link #migrateAllOnline()} only processes players owned
 * by the current thread and returns partial when Folia ownership prevents a complete synchronous batch.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
public interface ItemMigration {

    /**
     * Previews cross-module YAML references that would be changed, writing nothing.
     *
     * <p>Both ids are normalised before use: trimmed, lower-cased with {@link java.util.Locale#ROOT},
     * and spaces replaced with underscores. A blank id on either side, or two ids that normalise to the
     * same value, is {@code INVALID_INPUT}. Unlike {@link #apply}, preview does not require the target id
     * to already exist.
     *
     * <p>{@code UNAVAILABLE} when the migration service or the item/alias loaders are not installed, and
     * {@code INTERNAL_ERROR} when scanning fails with an I/O or runtime exception.
     *
     * @param oldId old item id
     * @param newId target item id
     * @return the preview of files and reference counts that would change
     */
    @NotNull
    EmakiResult<MigrationPreview> preview(@Nullable String oldId, @Nullable String newId);

    /**
     * Applies a migration through the runtime service, rewriting files on disk.
     *
     * <p>Ids are normalised and validated as in {@link #preview}, and additionally the normalised
     * {@code newId} must already be a loaded item definition, otherwise the result is {@code NOT_FOUND}.
     *
     * <p>When the runtime reports that some files were already committed before failing, the result is
     * {@link EmakiResult.Partial} carrying the real outcome so callers can see what landed; treat that as
     * "disk state changed" rather than a clean failure. A wholesale I/O or runtime exception maps to
     * {@code INTERNAL_ERROR}, in which case the extent of any partial write is not reported through this
     * API.
     *
     * @param oldId old item id
     * @param newId target item id
     * @param replaceReferences whether configured references are rewritten
     * @param keepAlias whether an old-to-new alias is retained
     * @return the committed outcome, a partial outcome, or a classified failure
     */
    @NotNull
    EmakiResult<MigrationOutcome> apply(@Nullable String oldId,
                                        @Nullable String newId,
                                        boolean replaceReferences,
                                        boolean keepAlias);

    /**
     * Refreshes/migrates one player's inventory on that player's entity-owner thread.
     *
     * <p>{@code INVALID_INPUT} for a {@code null} player, {@code TARGET_OFFLINE} for an offline player,
     * {@code UNAVAILABLE} without the migration service, and {@code WRONG_THREAD} when the calling thread
     * does not own the player &mdash; the call never silently reschedules itself.
     *
     * @param player the player whose inventory is migrated
     * @return the number of changed item stacks, or a classified failure
     */
    @NotNull
    EmakiResult<Integer> migrateInventory(@Nullable Player player);

    /**
     * Migrates every online inventory owned by the current thread.
     *
     * @return success when all online players were owned; partial with the changed count when some were not
     */
    @NotNull
    EmakiResult<Integer> migrateAllOnline();
}
