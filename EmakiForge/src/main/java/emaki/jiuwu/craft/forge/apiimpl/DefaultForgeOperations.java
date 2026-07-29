package emaki.jiuwu.craft.forge.apiimpl;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.contract.Unit;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.api.ForgeOperations;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

/**
 * {@link ForgeOperations} 的运行时实现。
 *
 * <p>所有写操作都先校验线程归属再委托，避免在非归属线程上改动 GUI 与背包。runtime 侧统一用
 * {@code boolean} 表达结果，这里按「入参非法 / 未就绪 / 业务拒绝」拆成不同的 {@link FailureKind}。
 */
public final class DefaultForgeOperations implements ForgeOperations {

    private final EmakiForgePlugin plugin;

    public DefaultForgeOperations(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<Unit> openForgeGui(@Nullable Player player, @Nullable String recipeId) {
        if (player == null) {
            return EmakiResult.invalidInput("forge.error.no_player");
        }
        if (Texts.isBlank(recipeId)) {
            return EmakiResult.invalidInput("forge.error.no_recipe_id");
        }
        ForgeGuiService guiService = plugin.forgeGuiService();
        ForgeService forgeService = plugin.forgeService();
        if (guiService == null || forgeService == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        Recipe recipe = null;
        String normalized = Texts.lower(recipeId);
        for (Recipe candidate : forgeService.sortedRecipes()) {
            if (normalized.equals(Texts.lower(candidate.id()))) {
                recipe = candidate;
                break;
            }
        }
        if (recipe == null) {
            return EmakiResult.notFound("forge.error.recipe_not_found");
        }
        return guiService.openForgeGui(player, recipe)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "forge.error.gui_open_failed");
    }

    @Override
    public @NotNull EmakiResult<Unit> openForgeGui(@Nullable Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("forge.error.no_player");
        }
        ForgeGuiService guiService = plugin.forgeGuiService();
        if (guiService == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        return guiService.openGeneralForgeGui(player)
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "forge.error.gui_open_failed");
    }

    @Override
    public @NotNull EmakiResult<Unit> openRecipeBook(@Nullable Player player, int page) {
        if (player == null) {
            return EmakiResult.invalidInput("forge.error.no_player");
        }
        RecipeBookGuiService bookService = plugin.recipeBookGuiService();
        if (bookService == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        return bookService.openRecipeBook(player, Math.max(0, page))
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "forge.error.gui_open_failed");
    }

    @Override
    public boolean viewingRecipeBook(@Nullable Player player) {
        RecipeBookGuiService bookService = plugin.recipeBookGuiService();
        return player != null && bookService != null && bookService.isRecipeBookInventory(player);
    }

    @Override
    public @NotNull EmakiResult<ItemStack> refreshItem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("forge.error.no_item");
        }
        ForgeItemRefreshService refreshService = plugin.itemRefreshService();
        if (refreshService == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        ItemStack refreshed = refreshService.refreshItem(itemStack);
        if (refreshed == null) {
            return EmakiResult.internalError("forge.error.refresh_failed");
        }
        return refreshed == itemStack
                ? EmakiResult.partial(refreshed, "forge.refresh.not_applicable")
                : EmakiResult.success(refreshed);
    }

    @Override
    public @NotNull EmakiResult<Unit> refreshPlayer(@Nullable Player player) {
        if (player == null) {
            return EmakiResult.invalidInput("forge.error.no_player");
        }
        ForgeItemRefreshService refreshService = plugin.itemRefreshService();
        if (refreshService == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        refreshService.refreshPlayerInventory(player);
        return EmakiResult.ok();
    }
}
