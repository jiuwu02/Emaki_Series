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

    /** Previews cross-module YAML references that would be changed. */
    @NotNull
    EmakiResult<MigrationPreview> preview(@Nullable String oldId, @Nullable String newId);

    /**
     * Applies a migration through the runtime service.
     *
     * @param oldId old item id
     * @param newId target item id
     * @param replaceReferences whether configured references are rewritten
     * @param keepAlias whether an old-to-new alias is retained
     */
    @NotNull
    EmakiResult<MigrationOutcome> apply(@Nullable String oldId,
                                        @Nullable String newId,
                                        boolean replaceReferences,
                                        boolean keepAlias);

    /** Refreshes/migrates one player's inventory on that player's entity-owner thread. */
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
