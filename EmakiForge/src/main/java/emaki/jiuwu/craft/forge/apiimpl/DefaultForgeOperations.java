package emaki.jiuwu.craft.forge.apiimpl;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
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
import emaki.jiuwu.craft.forge.api.event.ForgeCompletedEvent;
import emaki.jiuwu.craft.forge.api.event.ForgeStartEvent;
import emaki.jiuwu.craft.forge.api.model.ForgeInputs;
import emaki.jiuwu.craft.forge.api.model.ForgeOutcome;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.ValidationResult;
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
    public @NotNull CompletableFuture<EmakiResult<ForgeOutcome>> forgeAsync(@Nullable Player player,
            @Nullable String recipeId,
            @Nullable ForgeInputs inputs) {
        if (player == null) {
            return completed(EmakiResult.invalidInput("forge.error.no_player"));
        }
        if (Texts.isBlank(recipeId)) {
            return completed(EmakiResult.invalidInput("forge.error.no_recipe_id"));
        }
        if (inputs == null) {
            return completed(EmakiResult.invalidInput("forge.error.no_inputs"));
        }
        ForgeService service = plugin.forgeService();
        if (service == null || plugin.executionDispatcher() == null || !plugin.isRuntimeReady()) {
            return completed(EmakiResult.unavailable());
        }
        ForgeInputs snapshot = new ForgeInputs(
                inputs.targetItem(),
                inputs.blueprints(),
                inputs.requiredMaterials(),
                inputs.optionalMaterials());
        CompletableFuture<EmakiResult<ForgeOutcome>> completion = new CompletableFuture<>();
        Runnable begin = () -> beginProgrammaticForge(player, recipeId, snapshot, completion);
        if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(player)) {
            begin.run();
            return completion;
        }
        try {
            var scheduled = plugin.executionDispatcher().runEntity(
                    plugin,
                    player,
                    begin,
                    () -> completion.complete(EmakiResult.targetOffline()));
            if (scheduled == null) {
                completion.complete(EmakiResult.failure(
                        FailureKind.UNAVAILABLE,
                        "forge.error.owner_schedule_rejected"));
            }
        } catch (Throwable throwable) {
            completion.complete(EmakiResult.failure(
                    FailureKind.INTERNAL_ERROR,
                    "forge.error.owner_schedule_failed",
                    Map.of("reason", Texts.toStringSafe(throwable.getMessage()))));
        }
        return completion;
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
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
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
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
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
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        return bookService.openRecipeBook(player, Math.max(0, page))
                ? EmakiResult.ok()
                : EmakiResult.failure(FailureKind.REJECTED, "forge.error.gui_open_failed");
    }

    @Override
    public @NotNull EmakiResult<Boolean> viewingRecipeBook(@Nullable Player player) {
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
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        return EmakiResult.success(bookService.isRecipeBookInventory(player));
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
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        refreshService.refreshPlayerInventory(player);
        return EmakiResult.ok();
    }

    private void beginProgrammaticForge(Player player,
            String recipeId,
            ForgeInputs inputs,
            CompletableFuture<EmakiResult<ForgeOutcome>> completion) {
        if (player == null || !player.isOnline()) {
            completion.complete(EmakiResult.targetOffline());
            return;
        }
        ForgeService service = plugin.forgeService();
        long generation = plugin.runtimeGeneration();
        if (service == null || !plugin.isRuntimeReady() || !plugin.isGenerationActive(generation)) {
            completion.complete(EmakiResult.unavailable());
            return;
        }
        Recipe recipe = DefaultForgeCatalog.findRecipe(service, recipeId);
        if (recipe == null) {
            completion.complete(EmakiResult.notFound("forge.error.recipe_not_found"));
            return;
        }
        GuiItems guiItems = DefaultForgeCatalog.toGuiItems(inputs);
        ValidationResult validation = service.canForge(player, recipe, guiItems);
        if (validation == null) {
            completion.complete(EmakiResult.internalError("forge.error.validate_failed"));
            return;
        }
        if (!validation.success()) {
            String reasonKey = Texts.isBlank(validation.errorKey())
                    ? "forge.error.validation_failed"
                    : validation.errorKey();
            completion.complete(EmakiResult.failure(
                    FailureKind.REJECTED,
                    reasonKey,
                    DefaultForgeCatalog.safePlaceholders(validation.replacements())));
            return;
        }
        ForgeService.PreparedForge preparedForge = service.prepareForge(
                player,
                recipe,
                guiItems,
                ThreadLocalRandom.current().nextLong(),
                System.currentTimeMillis());
        if (preparedForge == null || preparedForge.request() == null) {
            completion.complete(EmakiResult.internalError("forge.error.item_create"));
            return;
        }
        if (plugin.playerDataStore() == null
                || !plugin.playerDataStore().isSessionWritable(player.getUniqueId())) {
            completion.complete(EmakiResult.failure(
                    FailureKind.UNAVAILABLE,
                    "forge.error.player_data_not_ready"));
            return;
        }
        boolean firstCraft = !plugin.playerDataStore().hasCrafted(player.getUniqueId(), recipe.id());
        ForgeStartEvent startEvent = new ForgeStartEvent(player, recipe.id(), firstCraft, recipe.successRate());
        Bukkit.getPluginManager().callEvent(startEvent);
        if (startEvent.isCancelled()) {
            completion.complete(EmakiResult.failure(
                    FailureKind.CANCELLED,
                    "forge.event.start_cancelled"));
            return;
        }
        try {
            service.executeForgeAsync(
                    player,
                    recipe,
                    guiItems,
                    preparedForge,
                    generation,
                    startEvent.getSuccessRate(),
                    null,
                    null,
                    null).whenComplete((result, throwable) -> completeProgrammaticForgeOnOwner(
                            player,
                            recipe,
                            result,
                            throwable,
                            completion));
        } catch (Throwable throwable) {
            completion.complete(internalFailure("forge.error.execution_failed", throwable));
        }
    }

    private void completeProgrammaticForgeOnOwner(Player player,
            Recipe recipe,
            ForgeResult result,
            Throwable throwable,
            CompletableFuture<EmakiResult<ForgeOutcome>> completion) {
        Runnable finish = () -> finishProgrammaticForge(player, recipe, result, throwable, completion);
        if (plugin.threadOwnership() != null && plugin.threadOwnership().isEntityOwned(player)) {
            finish.run();
            return;
        }
        try {
            var scheduled = plugin.executionDispatcher().runEntity(
                    plugin,
                    player,
                    finish,
                    () -> finishWithoutEvent(recipe, result, throwable, completion));
            if (scheduled == null) {
                finishWithoutEvent(recipe, result, throwable, completion);
            }
        } catch (Throwable schedulingFailure) {
            finishWithoutEvent(recipe, result, throwable, completion);
        }
    }

    private void finishProgrammaticForge(Player player,
            Recipe recipe,
            ForgeResult result,
            Throwable throwable,
            CompletableFuture<EmakiResult<ForgeOutcome>> completion) {
        EmakiResult<ForgeOutcome> mapped = mapResult(recipe, result, throwable);
        if (throwable == null && result != null && recipe != null) {
            Bukkit.getPluginManager().callEvent(new ForgeCompletedEvent(
                    player,
                    recipe.id(),
                    result.success(),
                    result.resultItem(),
                    result.quality(),
                    result.multiplier()));
        }
        completion.complete(mapped);
    }

    private void finishWithoutEvent(Recipe recipe,
            ForgeResult result,
            Throwable throwable,
            CompletableFuture<EmakiResult<ForgeOutcome>> completion) {
        completion.complete(mapResult(recipe, result, throwable));
    }

    private EmakiResult<ForgeOutcome> mapResult(Recipe recipe, ForgeResult result, Throwable throwable) {
        if (throwable != null) {
            return internalFailure("forge.error.execution_failed", throwable);
        }
        if (result == null) {
            return EmakiResult.internalError("forge.error.execution_missing_result");
        }
        if (result.success()) {
            if (recipe == null || result.resultItem() == null || result.resultItem().getType().isAir()) {
                return EmakiResult.internalError("forge.error.item_create");
            }
            return EmakiResult.success(new ForgeOutcome(
                    recipe.id(),
                    result.resultItem(),
                    Texts.toStringSafe(result.quality()),
                    result.multiplier()));
        }
        String reasonKey = Texts.isBlank(result.errorKey())
                ? "forge.error.action_failed"
                : result.errorKey();
        FailureKind kind = switch (reasonKey) {
            case "forge.error.runtime_unavailable" -> FailureKind.UNAVAILABLE;
            case "forge.error.item_create" -> FailureKind.INTERNAL_ERROR;
            default -> FailureKind.REJECTED;
        };
        return EmakiResult.failure(kind, reasonKey,
                DefaultForgeCatalog.safePlaceholders(result.replacements()));
    }

    private EmakiResult<ForgeOutcome> internalFailure(String reasonKey, Throwable throwable) {
        return EmakiResult.failure(
                FailureKind.INTERNAL_ERROR,
                reasonKey,
                Map.of("reason", Texts.toStringSafe(throwable == null ? null : throwable.getMessage())));
    }

    private static <T> CompletableFuture<EmakiResult<T>> completed(EmakiResult<T> result) {
        return CompletableFuture.completedFuture(result);
    }
}
