package emaki.jiuwu.craft.station.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.QueueSnapshot;
import emaki.jiuwu.craft.station.api.model.RecipeView;
import emaki.jiuwu.craft.station.api.model.StationView;
import emaki.jiuwu.craft.station.api.model.SubmitOutcome;

/**
 * Stable no-op layers returned while no EmakiStation bridge is installed.
 *
 * <p>These exist so {@link EmakiStationApi} never returns {@code null} and callers never have to
 * distinguish "plugin missing" from "empty answer" by catching an exception: every query returns an
 * empty value and every operation returns
 * {@link emaki.jiuwu.craft.corelib.api.contract.FailureKind#UNAVAILABLE}.
 */
final class UnavailableStation {

    static final StationCatalog CATALOG = new UnavailableCatalog();
    static final StationOperations OPERATIONS = new UnavailableOperations();
    static final StationExtensions EXTENSIONS = new UnavailableExtensions();

    private UnavailableStation() {
    }

    private static final class UnavailableCatalog implements StationCatalog {

        @Override
        public @NotNull List<StationView> stations() {
            return List.of();
        }

        @Override
        public @NotNull Optional<StationView> station(@Nullable String stationId) {
            return Optional.empty();
        }

        @Override
        public @NotNull List<RecipeView> recipes() {
            return List.of();
        }

        @Override
        public @NotNull Optional<RecipeView> recipe(@Nullable String recipeId) {
            return Optional.empty();
        }

        @Override
        public @NotNull List<RecipeView> recipesOf(@Nullable String stationId) {
            return List.of();
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<QueueSnapshot>> queueSnapshotAsync(
                @Nullable UUID playerId, @Nullable String stationId) {
            return CompletableFuture.completedFuture(EmakiResult.unavailable());
        }
    }

    private static final class UnavailableOperations implements StationOperations {

        @Override
        public @NotNull CompletableFuture<EmakiResult<SubmitOutcome>> submitAsync(@Nullable UUID playerId,
                @Nullable String stationId, @Nullable String recipeId, long batch,
                @Nullable MaterialChannel channel) {
            return CompletableFuture.completedFuture(EmakiResult.unavailable());
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<Unit>> cancelAsync(@Nullable UUID playerId,
                @Nullable String stationId, int index) {
            return CompletableFuture.completedFuture(EmakiResult.unavailable());
        }

        @Override
        public @NotNull CompletableFuture<EmakiResult<Integer>> claimAsync(@Nullable UUID playerId) {
            return CompletableFuture.completedFuture(EmakiResult.unavailable());
        }

        @Override
        public @NotNull EmakiResult<Unit> openGui(@Nullable Player player, @Nullable String stationId) {
            return EmakiResult.unavailable();
        }
    }

    private static final class UnavailableExtensions implements StationExtensions {
    }
}
