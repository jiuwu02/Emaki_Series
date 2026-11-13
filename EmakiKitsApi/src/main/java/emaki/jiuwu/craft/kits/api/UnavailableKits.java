package emaki.jiuwu.craft.kits.api;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.kits.api.model.KitClaimView;
import emaki.jiuwu.craft.kits.api.model.KitView;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Layers returned when EmakiKits is not installed.
 *
 * <p>Queries answer empty and operations report
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE}, so callers never need a
 * null check on {@code EmakiKitsApi.catalog()} or {@code operations()}. Availability must be
 * checked with {@link EmakiKitsApi#status()} rather than inferred from an empty answer.
 */
final class UnavailableKits implements KitCatalog, KitOperations, KitExtensions {

    static final KitCatalog CATALOG = new UnavailableKits();
    static final KitOperations OPERATIONS = (KitOperations) CATALOG;
    static final KitExtensions EXTENSIONS = (KitExtensions) CATALOG;

    private UnavailableKits() {
    }

    @Override
    public @NotNull Set<String> kitIds() {
        return Set.of();
    }

    @Override
    public @NotNull Optional<KitView> kit(@Nullable String kitId) {
        return Optional.empty();
    }

    @Override
    public boolean withinTimeWindow(@Nullable String kitId) {
        return false;
    }

    @Override
    public long playedSeconds(@Nullable Player player) {
        return 0L;
    }

    @Override
    public @NotNull EmakiResult<KitClaimView> claim(@Nullable Player player, @Nullable String kitId) {
        return EmakiResult.unavailable();
    }

    @Override
    public @NotNull EmakiResult<KitClaimView> claim(@Nullable Player player, @Nullable String kitId,
                                                    @Nullable List<String> selections) {
        return EmakiResult.unavailable();
    }

    @Override
    public @NotNull EmakiResult<Unit> buy(@Nullable Player player, @Nullable String kitId) {
        return EmakiResult.unavailable();
    }

    @Override
    public @NotNull EmakiResult<KitClaimView> redeem(@Nullable Player player, @Nullable String kitId,
                                                     @Nullable String code) {
        return EmakiResult.unavailable();
    }

    @Override
    public @NotNull EmakiResult<Unit> grantPurchase(@Nullable Player player, @Nullable String kitId) {
        return EmakiResult.unavailable();
    }
}
