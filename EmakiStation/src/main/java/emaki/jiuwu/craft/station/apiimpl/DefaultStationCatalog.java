package emaki.jiuwu.craft.station.apiimpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.station.EmakiStationPlugin;
import emaki.jiuwu.craft.station.api.StationCatalog;
import emaki.jiuwu.craft.station.api.model.QueueSnapshot;
import emaki.jiuwu.craft.station.api.model.RecipeView;
import emaki.jiuwu.craft.station.api.model.StationView;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.definition.StationRegistry;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

final class DefaultStationCatalog implements StationCatalog {

    private final EmakiStationPlugin plugin;

    DefaultStationCatalog(EmakiStationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull List<StationView> stations() {
        StationRegistry registry = plugin.registry();
        List<StationView> views = new ArrayList<>();
        for (StationDefinition station : registry.stations()) {
            views.add(station.toView(List.copyOf(registry.recipeIdsOf(station.id()))));
        }
        return List.copyOf(views);
    }

    @Override
    public @NotNull Optional<StationView> station(@Nullable String stationId) {
        StationRegistry registry = plugin.registry();
        StationDefinition station = registry.station(stationId);
        if (station == null) {
            return Optional.empty();
        }
        return Optional.of(station.toView(List.copyOf(registry.recipeIdsOf(station.id()))));
    }

    @Override
    public @NotNull List<RecipeView> recipes() {
        List<RecipeView> views = new ArrayList<>();
        for (RecipeDefinition recipe : plugin.registry().recipes()) {
            views.add(recipe.toView());
        }
        return List.copyOf(views);
    }

    @Override
    public @NotNull Optional<RecipeView> recipe(@Nullable String recipeId) {
        RecipeDefinition recipe = plugin.registry().recipe(recipeId);
        return recipe == null ? Optional.empty() : Optional.of(recipe.toView());
    }

    @Override
    public @NotNull List<RecipeView> recipesOf(@Nullable String stationId) {
        List<RecipeView> views = new ArrayList<>();
        for (RecipeDefinition recipe : plugin.registry().recipesOf(stationId)) {
            views.add(recipe.toView());
        }
        return List.copyOf(views);
    }

    @Override
    public @NotNull CompletableFuture<EmakiResult<QueueSnapshot>> queueSnapshotAsync(
            @Nullable UUID playerId, @Nullable String stationId) {
        if (playerId == null || stationId == null) {
            return CompletableFuture.completedFuture(EmakiResult.invalidInput("station.snapshot_bad_request"));
        }
        StationDefinition station = plugin.registry().station(stationId);
        if (station == null) {
            return CompletableFuture.completedFuture(EmakiResult.notFound("station.unknown_station"));
        }
        return plugin.queueService().loadAsync(playerId).thenApply(queues -> {
            if (queues == null) {
                return EmakiResult.internalError("station.queue_load_failed");
            }
            return EmakiResult.success(plugin.queueService().snapshot(playerId, station, queues));
        });
    }
}
