package emaki.jiuwu.craft.codex.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.codex.api.model.AdvancementView;
import emaki.jiuwu.craft.codex.api.model.CodexPageView;
import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;

/**
 * Read-only queries against the advancements EmakiCodex has registered.
 *
 * <p>Reached through {@code EmakiCodexApi.catalog()}.
 *
 * <h2>Threading</h2>
 * Definition and page lookups may be called from any thread. {@link #completed(UUID, String)} reads a live
 * player's advancement progress and must run on that player's owner thread.
 */
@ApiStatus.NonExtendable
public interface CodexCatalog {

    /**
     * {@return every registered advancement, ordered by registration; empty when EmakiCodex is
     * unavailable}
     */
    @NotNull
    List<AdvancementView> advancements();

    /**
     * Looks up one advancement.
     *
     * <p>The id may be a bare local id (resolved against EmakiCodex's own namespace) or a fully qualified
     * {@code namespace:path} key. Ids that parse correctly but were never registered are treated as
     * unknown.
     *
     * @param advancementId the advancement id or key
     * @return the advancement when registered, otherwise an empty optional
     */
    @NotNull
    Optional<AdvancementView> advancement(@Nullable String advancementId);

    /** {@return every registered page id, sorted; empty when EmakiCodex is unavailable} */
    @NotNull
    List<String> pageIds();

    /**
     * Looks up one page and the advancements it contains.
     *
     * @param pageId the page id
     * @return the page when registered, otherwise an empty optional
     */
    @NotNull
    Optional<CodexPageView> page(@Nullable String pageId);

    /** {@return how many advancements are currently registered} */
    int count();

    /**
     * Checks whether a player has completed an advancement.
     *
     * <p>Reads the player's live Minecraft advancement progress rather than any EmakiCodex-side record, so
     * the answer also reflects progress awarded by other means.
     *
     * <p><strong>Thread:</strong> the player's owner thread. The player must be online.
     *
     * @param playerId      the player's unique id
     * @param advancementId the advancement id or key
     * @return whether the advancement is complete, or a failure when the player is offline, the id is
     *         unknown, or the call came from the wrong thread
     */
    @NotNull
    EmakiResult<Boolean> completed(@Nullable UUID playerId, @Nullable String advancementId);
}
