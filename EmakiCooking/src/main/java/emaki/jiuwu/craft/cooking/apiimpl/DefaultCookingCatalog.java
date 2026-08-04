package emaki.jiuwu.craft.cooking.apiimpl;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.CookingCatalog;
import emaki.jiuwu.craft.cooking.api.model.CookingProgress;
import emaki.jiuwu.craft.cooking.api.model.CookingRecipeView;
import emaki.jiuwu.craft.cooking.api.model.CookingStationType;
import emaki.jiuwu.craft.cooking.api.model.CookingStationView;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingStationTracker;

/** Runtime-backed cooking catalog. */
public final class DefaultCookingCatalog implements CookingCatalog {

    private final EmakiCookingPlugin plugin;

    public DefaultCookingCatalog(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<CookingRecipeView> recipes(CookingStationType stationType) {
        if (!ready() || stationType == null) {
            return List.of();
        }
        return documents(stationType).stream()
                .filter(document -> document != null && Texts.isNotBlank(document.id()))
                .sorted(Comparator.comparing(RecipeDocument::id))
                .map(DefaultCookingCatalog::toRecipeView)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<CookingRecipeView> recipe(CookingStationType stationType, String recipeId) {
        if (!ready() || stationType == null || Texts.isBlank(recipeId)) {
            return Optional.empty();
        }
        RecipeDocument document = document(stationType, Texts.lower(recipeId));
        return document == null ? Optional.empty() : toRecipeView(document);
    }

    @Override
    public EmakiResult<CookingRecipeView> matchRecipe(CookingStationType stationType,
            ItemStack input,
            Player player) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (stationType == null) {
            return EmakiResult.invalidInput("cooking.input.station_type_missing");
        }
        if (input == null || input.getType().isAir()) {
            return EmakiResult.invalidInput("cooking.input.item_missing");
        }
        if (player != null && !plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        if (stationType == CookingStationType.WOK
                || stationType == CookingStationType.FERMENTATION_BARREL) {
            return EmakiResult.rejected("cooking.recipe.single_input_match_unsupported");
        }
        ItemSourceRef source = plugin.coreItemSourceService().identifyItem(input);
        if (source == null) {
            return EmakiResult.notFound("cooking.input.source_not_found");
        }
        String shorthand = ItemSourceUtil.toShorthand(source);
        CookingRecipeService service = plugin.recipeService();
        RecipeDocument matched = switch (stationType) {
            case CHOPPING_BOARD -> service.findChoppingBoardRecipe(shorthand, player);
            case GRINDER -> service.findGrinderRecipe(shorthand, player);
            case STEAMER -> service.findSteamerRecipe(shorthand, player);
            case OVEN -> service.findOvenRecipe(shorthand, player);
            case JUICER -> service.findJuicerRecipe(shorthand, player);
            case WOK, FERMENTATION_BARREL -> null;
        };
        if (matched == null) {
            return EmakiResult.notFound("cooking.recipe.match_not_found");
        }
        return toRecipeView(matched)
                .<EmakiResult<CookingRecipeView>>map(EmakiResult::success)
                .orElseGet(() -> EmakiResult.internalError("cooking.recipe.station_type_invalid"));
    }

    @Override
    public EmakiResult<CookingStationView> stationAt(Location location) {
        if (!ready()) {
            return EmakiResult.unavailable();
        }
        if (location == null || location.getWorld() == null) {
            return EmakiResult.invalidInput("cooking.input.location_missing");
        }
        if (!plugin.threadOwnership().isLocationOwned(location)) {
            return EmakiResult.wrongThread();
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(location.getBlock());
        if (coordinates == null) {
            return EmakiResult.invalidInput("cooking.input.location_invalid");
        }
        Optional<StationSnapshot> snapshot = firstSnapshot(coordinates);
        if (snapshot.isEmpty()) {
            return EmakiResult.notFound("cooking.station_not_found");
        }
        return toStationView(snapshot.get())
                .<EmakiResult<CookingStationView>>map(EmakiResult::success)
                .orElseGet(() -> EmakiResult.internalError("cooking.station_type_invalid"));
    }

    @Override
    public Optional<Location> recentStation(UUID playerId) {
        CookingStationTracker tracker = plugin == null || !plugin.publicApiReady()
                ? null
                : plugin.stationTracker();
        if (tracker == null || playerId == null) {
            return Optional.empty();
        }
        return tracker.recent(playerId)
                .map(CookingStationTracker.RecentStation::coordinates)
                .map(coordinates -> coordinates.location(0.5D, 0.5D, 0.5D))
                .filter(location -> location != null && location.getWorld() != null);
    }

    private Collection<RecipeDocument> documents(CookingStationType type) {
        return switch (type) {
            case CHOPPING_BOARD -> plugin.choppingBoardRecipeLoader().all().values();
            case WOK -> plugin.wokRecipeLoader().all().values();
            case GRINDER -> plugin.grinderRecipeLoader().all().values();
            case STEAMER -> plugin.steamerRecipeLoader().all().values();
            case OVEN -> plugin.ovenRecipeLoader().all().values();
            case JUICER -> plugin.juicerRecipeLoader().all().values();
            case FERMENTATION_BARREL -> plugin.fermentationBarrelRecipeLoader().all().values();
        };
    }

    private RecipeDocument document(CookingStationType type, String recipeId) {
        return switch (type) {
            case CHOPPING_BOARD -> plugin.choppingBoardRecipeLoader().get(recipeId);
            case WOK -> plugin.wokRecipeLoader().get(recipeId);
            case GRINDER -> plugin.grinderRecipeLoader().get(recipeId);
            case STEAMER -> plugin.steamerRecipeLoader().get(recipeId);
            case OVEN -> plugin.ovenRecipeLoader().get(recipeId);
            case JUICER -> plugin.juicerRecipeLoader().get(recipeId);
            case FERMENTATION_BARREL -> plugin.fermentationBarrelRecipeLoader().get(recipeId);
        };
    }

    private Optional<StationSnapshot> firstSnapshot(StationCoordinates coordinates) {
        Optional<StationSnapshot> found;
        if (plugin.choppingBoardRuntimeService() != null
                && (found = plugin.choppingBoardRuntimeService().snapshotAt(coordinates)).isPresent()) {
            return found;
        }
        if (plugin.wokRuntimeService() != null
                && (found = plugin.wokRuntimeService().snapshotAt(coordinates)).isPresent()) {
            return found;
        }
        if (plugin.grinderRuntimeService() != null
                && (found = plugin.grinderRuntimeService().snapshotAt(coordinates)).isPresent()) {
            return found;
        }
        if (plugin.steamerRuntimeService() != null
                && (found = plugin.steamerRuntimeService().snapshotAt(coordinates)).isPresent()) {
            return found;
        }
        if (plugin.ovenRuntimeService() != null
                && (found = plugin.ovenRuntimeService().snapshotAt(coordinates)).isPresent()) {
            return found;
        }
        if (plugin.juicerRuntimeService() != null
                && (found = plugin.juicerRuntimeService().snapshotAt(coordinates)).isPresent()) {
            return found;
        }
        if (plugin.fermentationBarrelRuntimeService() != null
                && (found = plugin.fermentationBarrelRuntimeService().snapshotAt(coordinates)).isPresent()) {
            return found;
        }
        return Optional.empty();
    }

    static Optional<CookingRecipeView> toRecipeView(RecipeDocument document) {
        if (document == null || document.stationType() == null) {
            return Optional.empty();
        }
        return CookingStationType.fromConfigKey(document.stationType().folderName())
                .map(type -> new CookingRecipeView(Texts.lower(document.id()), document.displayName(), type));
    }

    static Optional<CookingStationView> toStationView(StationSnapshot snapshot) {
        if (snapshot == null || snapshot.stationType() == null) {
            return Optional.empty();
        }
        return CookingStationType.fromConfigKey(snapshot.stationType().folderName()).map(type -> {
            CookingProgress progress = new CookingProgress(snapshot.progressCurrent(),
                    snapshot.progressTarget(), snapshot.completed());
            String fluidName = Texts.isBlank(snapshot.fluidName()) ? null : snapshot.fluidName();
            return new CookingStationView(type,
                    Texts.isBlank(snapshot.recipeId()) ? null : Texts.lower(snapshot.recipeId()),
                    Texts.isBlank(snapshot.recipeName()) ? null : snapshot.recipeName(),
                    progress,
                    snapshot.burning(),
                    tracksHeat(type) ? OptionalInt.of(snapshot.heat()) : OptionalInt.empty(),
                    tracksMoisture(type) ? OptionalInt.of(snapshot.moisture()) : OptionalInt.empty(),
                    tracksSteam(type) ? OptionalInt.of(snapshot.steam()) : OptionalInt.empty(),
                    snapshot.ingredientCount(),
                    fluidName,
                    fluidName == null ? OptionalInt.empty() : OptionalInt.of(snapshot.fluidAmountMl()));
        });
    }

    private boolean ready() {
        return plugin != null
                && plugin.isEnabled()
                && plugin.publicApiReady()
                && plugin.threadOwnership() != null
                && plugin.coreItemSourceService() != null
                && plugin.recipeService() != null
                && plugin.choppingBoardRecipeLoader() != null
                && plugin.wokRecipeLoader() != null
                && plugin.grinderRecipeLoader() != null
                && plugin.steamerRecipeLoader() != null
                && plugin.ovenRecipeLoader() != null
                && plugin.juicerRecipeLoader() != null
                && plugin.fermentationBarrelRecipeLoader() != null;
    }

    private static boolean tracksHeat(CookingStationType type) {
        return type == CookingStationType.WOK || type == CookingStationType.OVEN;
    }

    private static boolean tracksMoisture(CookingStationType type) {
        return type == CookingStationType.OVEN || type == CookingStationType.FERMENTATION_BARREL;
    }

    private static boolean tracksSteam(CookingStationType type) {
        return type == CookingStationType.STEAMER;
    }
}
