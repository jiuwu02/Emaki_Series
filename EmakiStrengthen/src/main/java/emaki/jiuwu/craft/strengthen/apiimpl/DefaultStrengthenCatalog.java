package emaki.jiuwu.craft.strengthen.apiimpl;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.StrengthenCatalog;
import emaki.jiuwu.craft.strengthen.api.model.AttemptContext;
import emaki.jiuwu.craft.strengthen.api.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenRecipe;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;
import emaki.jiuwu.craft.strengthen.service.StrengthenAttemptService;

/** Read-only API adapter backed by the recipe loader and attempt service. */
public final class DefaultStrengthenCatalog implements StrengthenCatalog {

    private final EmakiStrengthenPlugin plugin;

    public DefaultStrengthenCatalog(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull EmakiResult<StrengthenState> readState(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        StrengthenAttemptService service = plugin.attemptService();
        if (service == null) {
            return EmakiResult.unavailable();
        }
        try {
            return EmakiResult.success(service.readState(itemStack));
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.error.state_read_failed");
        }
    }

    @Override
    public @NotNull List<StrengthenRecipe> recipes() {
        return plugin.recipeLoader() == null ? List.of() : plugin.recipeLoader().ordered();
    }

    @Override
    public @NotNull Optional<StrengthenRecipe> recipe(@Nullable String recipeId) {
        if (Texts.isBlank(recipeId) || plugin.recipeLoader() == null || plugin.recipeResolver() == null) {
            return Optional.empty();
        }
        String resolvedId = plugin.recipeResolver().resolveRecipeId(recipeId);
        return Texts.isBlank(resolvedId)
                ? Optional.empty()
                : Optional.ofNullable(plugin.recipeLoader().get(resolvedId));
    }

    @Override
    public @NotNull EmakiResult<AttemptPreview> preview(@Nullable Player player,
            @Nullable AttemptContext context) {
        EmakiResult<AttemptPreview> validation = validatePlayerContext(player, context);
        if (validation != null) {
            return validation;
        }
        try {
            return EmakiResult.success(plugin.attemptService().preview(player, context));
        } catch (RuntimeException | LinkageError exception) {
            return EmakiResult.internalError("strengthen.error.preview_failed");
        }
    }

    @Override
    public @NotNull EmakiResult<Double> successRate(@Nullable Player player,
            @Nullable AttemptContext context) {
        EmakiResult<AttemptPreview> previewResult = preview(player, context);
        if (previewResult instanceof EmakiResult.Failure<AttemptPreview>) {
            return previewResult.retypeFailure();
        }
        AttemptPreview preview = previewResult.orElse(null);
        if (preview == null) {
            return EmakiResult.internalError("strengthen.error.preview_failed");
        }
        if (!preview.eligible()) {
            String reasonKey = Texts.isBlank(preview.errorKey())
                    ? "strengthen.error.not_eligible" : preview.errorKey();
            FailureKind kind = "strengthen.error.no_recipe".equals(reasonKey)
                    ? FailureKind.NOT_FOUND : FailureKind.REJECTED;
            return EmakiResult.failure(kind, reasonKey);
        }
        return EmakiResult.success(preview.successRate());
    }

    private EmakiResult<AttemptPreview> validatePlayerContext(Player player, AttemptContext context) {
        if (player == null) {
            return EmakiResult.invalidInput("strengthen.error.no_player");
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        if (context == null || context.targetItem() == null) {
            return EmakiResult.invalidInput("strengthen.error.no_target");
        }
        if (plugin.attemptService() == null) {
            return EmakiResult.unavailable();
        }
        if (plugin.threadOwnership() == null || !plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        return null;
    }
}
