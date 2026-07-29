package emaki.jiuwu.craft.cooking.apiimpl;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.cooking.EmakiCookingPlugin;
import emaki.jiuwu.craft.cooking.api.CookingCatalog;
import emaki.jiuwu.craft.cooking.api.model.CookingProgress;
import emaki.jiuwu.craft.cooking.api.model.CookingRecipeView;
import emaki.jiuwu.craft.cooking.api.model.CookingStationType;
import emaki.jiuwu.craft.cooking.api.model.CookingStationView;
import emaki.jiuwu.craft.cooking.model.RecipeDocument;
import emaki.jiuwu.craft.cooking.model.StationCoordinates;
import emaki.jiuwu.craft.cooking.model.StationSnapshot;
import emaki.jiuwu.craft.cooking.model.StationType;
import emaki.jiuwu.craft.cooking.service.CookingRecipeService;
import emaki.jiuwu.craft.cooking.service.CookingStationTracker;

/**
 * {@link CookingCatalog} 的运行时实现。
 *
 * <p>站点快照的映射是本类的重点：runtime 用同一个宽结构承载 7 种站点，各站点只填自己关心的字段，
 * 其余留 0。这里按站点类型判定哪些读数真正有意义，只把有意义的包成 {@link OptionalInt}，
 * 避免第三方把「砧板的热度 0」当成真实读数。
 */
public final class DefaultCookingCatalog implements CookingCatalog {

    private final EmakiCookingPlugin plugin;

    public DefaultCookingCatalog(EmakiCookingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull Optional<CookingRecipeView> findRecipe(@Nullable CookingStationType stationType,
            @Nullable String inputSource,
            @Nullable Player player) {
        CookingRecipeService recipeService = plugin.recipeService();
        if (stationType == null || Texts.isBlank(inputSource) || recipeService == null) {
            return Optional.empty();
        }
        RecipeDocument document = switch (stationType) {
            case CHOPPING_BOARD -> recipeService.findChoppingBoardRecipe(inputSource, player);
            case GRINDER -> recipeService.findGrinderRecipe(inputSource, player);
            case STEAMER -> recipeService.findSteamerRecipe(inputSource, player);
            case OVEN -> recipeService.findOvenRecipe(inputSource, player);
            case JUICER -> recipeService.findJuicerRecipe(inputSource, player);
            case WOK, FERMENTATION_BARREL -> null;
        };
        return Optional.ofNullable(document).map(DefaultCookingCatalog::toRecipeView);
    }

    @Override
    public @NotNull List<CookingRecipeView> wokRecipes() {
        CookingRecipeService recipeService = plugin.recipeService();
        if (recipeService == null) {
            return List.of();
        }
        return recipeService.wokRecipes().stream().map(DefaultCookingCatalog::toRecipeView).toList();
    }

    @Override
    public @NotNull Optional<CookingStationView> stationAt(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        StationCoordinates coordinates = StationCoordinates.fromBlock(location.getBlock());
        if (coordinates == null) {
            return Optional.empty();
        }
        return firstSnapshot(coordinates).map(DefaultCookingCatalog::toStationView);
    }

    @Override
    public @NotNull Optional<RecentStation> recentStation(@Nullable UUID playerId) {
        CookingStationTracker tracker = plugin.stationTracker();
        if (tracker == null || playerId == null) {
            return Optional.empty();
        }
        return tracker.recent(playerId).flatMap(recent -> {
            Optional<CookingStationType> type = CookingStationType.fromConfigKey(recent.type().folderName());
            Block block = recent.coordinates().block();
            return type.isEmpty() || block == null
                    ? Optional.empty()
                    : Optional.of(new RecentStation(type.get(), block.getLocation()));
        });
    }

    /**
     * 依次向 7 个站点服务询问该坐标的快照，返回第一个命中的。
     *
     * <p>runtime 没有「按坐标查任意站点」的统一入口，只有每类站点各自的 {@code snapshotAt}，
     * 因此这里逐个探测。各服务对不属于自己的坐标返回空 Optional，代价只是一次 map 查找。
     *
     * @param coordinates 站点坐标
     * @return 命中的快照
     */
    private Optional<StationSnapshot> firstSnapshot(StationCoordinates coordinates) {
        if (plugin.choppingBoardRuntimeService() != null) {
            Optional<StationSnapshot> found = plugin.choppingBoardRuntimeService().snapshotAt(coordinates);
            if (found.isPresent()) {
                return found;
            }
        }
        if (plugin.wokRuntimeService() != null) {
            Optional<StationSnapshot> found = plugin.wokRuntimeService().snapshotAt(coordinates);
            if (found.isPresent()) {
                return found;
            }
        }
        if (plugin.grinderRuntimeService() != null) {
            Optional<StationSnapshot> found = plugin.grinderRuntimeService().snapshotAt(coordinates);
            if (found.isPresent()) {
                return found;
            }
        }
        if (plugin.steamerRuntimeService() != null) {
            Optional<StationSnapshot> found = plugin.steamerRuntimeService().snapshotAt(coordinates);
            if (found.isPresent()) {
                return found;
            }
        }
        if (plugin.ovenRuntimeService() != null) {
            Optional<StationSnapshot> found = plugin.ovenRuntimeService().snapshotAt(coordinates);
            if (found.isPresent()) {
                return found;
            }
        }
        if (plugin.juicerRuntimeService() != null) {
            Optional<StationSnapshot> found = plugin.juicerRuntimeService().snapshotAt(coordinates);
            if (found.isPresent()) {
                return found;
            }
        }
        if (plugin.fermentationBarrelRuntimeService() != null) {
            Optional<StationSnapshot> found = plugin.fermentationBarrelRuntimeService().snapshotAt(coordinates);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /**
     * 把 runtime 配方文档映射为只读视图，丢弃其携带的 YAML 句柄。
     *
     * @param document runtime 配方文档
     * @return 只读视图
     */
    static CookingRecipeView toRecipeView(RecipeDocument document) {
        CookingStationType type = CookingStationType.fromConfigKey(document.stationType().folderName())
                .orElse(CookingStationType.WOK);
        return new CookingRecipeView(Texts.lower(document.id()), document.displayName(), type);
    }

    /**
     * 把 runtime 站点快照映射为按站点类型裁剪过的只读视图。
     *
     * @param snapshot runtime 快照
     * @return 只读视图
     */
    static CookingStationView toStationView(StationSnapshot snapshot) {
        StationType runtimeType = snapshot.stationType();
        CookingStationType type = CookingStationType.fromConfigKey(runtimeType.folderName())
                .orElse(CookingStationType.WOK);
        CookingProgress progress = new CookingProgress(snapshot.progressCurrent(),
                snapshot.progressTarget(),
                snapshot.completed());
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
    }

    /**
     * @param type 站点类型
     * @return 该站点是否跟踪热度
     */
    private static boolean tracksHeat(CookingStationType type) {
        return type == CookingStationType.WOK || type == CookingStationType.OVEN;
    }

    /**
     * @param type 站点类型
     * @return 该站点是否跟踪湿度
     */
    private static boolean tracksMoisture(CookingStationType type) {
        return type == CookingStationType.OVEN || type == CookingStationType.FERMENTATION_BARREL;
    }

    /**
     * @param type 站点类型
     * @return 该站点是否跟踪蒸汽
     */
    private static boolean tracksSteam(CookingStationType type) {
        return type == CookingStationType.STEAMER;
    }
}
