package emaki.jiuwu.craft.forge.apiimpl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import emaki.jiuwu.craft.corelib.api.contract.EmakiResult;
import emaki.jiuwu.craft.corelib.api.contract.FailureKind;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.api.ForgeCatalog;
import emaki.jiuwu.craft.forge.api.model.ForgeBlueprintView;
import emaki.jiuwu.craft.forge.api.model.ForgeInputs;
import emaki.jiuwu.craft.forge.api.model.ForgeMaterialView;
import emaki.jiuwu.craft.forge.api.model.ForgeRecipeView;
import emaki.jiuwu.craft.forge.api.model.ForgeValidation;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;
import emaki.jiuwu.craft.forge.model.ValidationResult;
import emaki.jiuwu.craft.forge.service.ForgeService;

public final class DefaultForgeCatalog implements ForgeCatalog {

    private final EmakiForgePlugin plugin;

    public DefaultForgeCatalog(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull List<ForgeRecipeView> recipes() {
        ForgeService service = plugin.forgeService();
        if (service == null) {
            return List.of();
        }
        return service.sortedRecipes().stream()
                .map(DefaultForgeCatalog::toRecipeView)
                .toList();
    }

    @Override
    public @NotNull Optional<ForgeRecipeView> recipe(@Nullable String recipeId) {
        if (Texts.isBlank(recipeId)) {
            return Optional.empty();
        }
        ForgeService service = plugin.forgeService();
        if (service == null) {
            return Optional.empty();
        }
        String normalized = Texts.lower(recipeId);
        return service.sortedRecipes().stream()
                .filter(recipe -> normalized.equals(Texts.lower(recipe.id())))
                .findFirst()
                .map(DefaultForgeCatalog::toRecipeView);
    }

    @Override
    public @NotNull Optional<ForgeMaterialView> materialById(@Nullable String materialId) {
        if (Texts.isBlank(materialId)) {
            return Optional.empty();
        }
        ForgeService service = plugin.forgeService();
        if (service == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(service.findMaterialById(materialId))
                .map(DefaultForgeCatalog::toMaterialView);
    }

    @Override
    public @NotNull Optional<ForgeMaterialView> materialByItem(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return Optional.empty();
        }
        ForgeService service = plugin.forgeService();
        if (service == null || plugin.itemIdentifierService() == null) {
            return Optional.empty();
        }
        ItemSourceRef source = plugin.itemIdentifierService().identifySource(itemStack);
        if (source == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(service.findMaterialBySource(source))
                .map(DefaultForgeCatalog::toMaterialView);
    }

    @Override
    public @NotNull EmakiResult<ForgeRecipeView> matchRecipe(@Nullable Player player, @Nullable ForgeInputs inputs) {
        if (player == null) {
            return EmakiResult.invalidInput("forge.error.no_player");
        }
        if (inputs == null) {
            return EmakiResult.invalidInput("forge.error.no_inputs");
        }
        ForgeService service = plugin.forgeService();
        if (service == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        RecipeMatch match = service.findMatchingRecipe(player, toGuiItems(inputs));
        if (match == null) {
            return EmakiResult.internalError("forge.error.match_failed");
        }
        if (match.recipe() == null) {
            String reasonKey = Texts.isBlank(match.errorKey()) ? "forge.error.no_recipe" : match.errorKey();
            return EmakiResult.failure(FailureKind.REJECTED, reasonKey, safePlaceholders(match.replacements()));
        }
        return EmakiResult.success(toRecipeView(match.recipe()));
    }

    @Override
    public @NotNull EmakiResult<ForgeValidation> validate(@Nullable Player player,
            @Nullable String recipeId,
            @Nullable ForgeInputs inputs) {
        if (player == null) {
            return EmakiResult.invalidInput("forge.error.no_player");
        }
        if (Texts.isBlank(recipeId)) {
            return EmakiResult.invalidInput("forge.error.no_recipe_id");
        }
        if (inputs == null) {
            return EmakiResult.invalidInput("forge.error.no_inputs");
        }
        ForgeService service = plugin.forgeService();
        if (service == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        Recipe recipe = findRecipe(service, recipeId);
        if (recipe == null) {
            return EmakiResult.notFound("forge.error.recipe_not_found");
        }
        ValidationResult result = service.canForge(player, recipe, toGuiItems(inputs));
        if (result == null) {
            return EmakiResult.internalError("forge.error.validate_failed");
        }
        if (result.success()) {
            return EmakiResult.success(ForgeValidation.pass());
        }
        String reasonKey = Texts.isBlank(result.errorKey()) ? "forge.error.validation_failed" : result.errorKey();
        return EmakiResult.success(ForgeValidation.deny(reasonKey, safePlaceholders(result.replacements())));
    }

    @Override
    public @NotNull EmakiResult<ItemStack> previewResult(@Nullable Player player,
            @Nullable String recipeId,
            @Nullable ForgeInputs inputs) {
        if (player == null) {
            return EmakiResult.invalidInput("forge.error.no_player");
        }
        if (Texts.isBlank(recipeId)) {
            return EmakiResult.invalidInput("forge.error.no_recipe_id");
        }
        if (inputs == null) {
            return EmakiResult.invalidInput("forge.error.no_inputs");
        }
        ForgeService service = plugin.forgeService();
        if (service == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        Recipe recipe = findRecipe(service, recipeId);
        if (recipe == null) {
            return EmakiResult.notFound("forge.error.recipe_not_found");
        }
        if (plugin.playerDataStore() == null
                || !plugin.playerDataStore().isSessionWritable(player.getUniqueId())) {
            return EmakiResult.failure(FailureKind.UNAVAILABLE, "forge.error.player_data_not_ready");
        }
        GuiItems guiItems = toGuiItems(inputs);
        ValidationResult validation = service.canForge(player, recipe, guiItems);
        if (validation == null) {
            return EmakiResult.internalError("forge.error.validate_failed");
        }
        if (!validation.success()) {
            String reasonKey = Texts.isBlank(validation.errorKey())
                    ? "forge.error.validation_failed"
                    : validation.errorKey();
            return EmakiResult.failure(FailureKind.REJECTED, reasonKey,
                    safePlaceholders(validation.replacements()));
        }
        ItemStack preview = service.previewResultItem(
                player,
                recipe,
                guiItems,
                ThreadLocalRandom.current().nextLong(),
                System.currentTimeMillis());
        return preview == null
                ? EmakiResult.internalError("forge.error.item_create")
                : EmakiResult.success(preview);
    }

    @Override
    public @NotNull EmakiResult<Integer> mastery(@Nullable Player player, @Nullable String recipeId) {
        if (player == null) {
            return EmakiResult.invalidInput("forge.error.no_player");
        }
        if (Texts.isBlank(recipeId)) {
            return EmakiResult.invalidInput("forge.error.no_recipe_id");
        }
        ForgeService service = plugin.forgeService();
        if (service == null || plugin.playerDataStore() == null || !plugin.isRuntimeReady()) {
            return EmakiResult.unavailable();
        }
        if (!plugin.threadOwnership().isEntityOwned(player)) {
            return EmakiResult.wrongThread();
        }
        if (!player.isOnline()) {
            return EmakiResult.targetOffline();
        }
        Recipe recipe = findRecipe(service, recipeId);
        if (recipe == null) {
            return EmakiResult.notFound("forge.error.recipe_not_found");
        }
        if (!plugin.playerDataStore().isSessionWritable(player.getUniqueId())) {
            return EmakiResult.failure(FailureKind.UNAVAILABLE, "forge.error.player_data_not_ready");
        }
        return EmakiResult.success(service.mastery(player.getUniqueId(), recipe.id()));
    }

    @Override
    public boolean accepting() {
        ForgeService service = plugin.forgeService();
        return service != null && plugin.isRuntimeReady() && service.isAccepting();
    }

    static Recipe findRecipe(ForgeService service, String recipeId) {
        String normalized = Texts.lower(recipeId);
        for (Recipe recipe : service.sortedRecipes()) {
            if (normalized.equals(Texts.lower(recipe.id()))) {
                return recipe;
            }
        }
        return null;
    }

    static GuiItems toGuiItems(ForgeInputs inputs) {
        return new GuiItems(inputs.targetItem(),
                inputs.blueprints(),
                inputs.requiredMaterials(),
                inputs.optionalMaterials());
    }

    static ForgeRecipeView toRecipeView(Recipe recipe) {
        return new ForgeRecipeView(Texts.lower(recipe.id()),
                recipe.displayName(),
                recipe.successRate(),
                recipe.requiredMaterials().stream().map(DefaultForgeCatalog::toMaterialView).toList(),
                recipe.optionalMaterials().stream().map(DefaultForgeCatalog::toMaterialView).toList(),
                recipe.requiresTargetInput(),
                recipe.forgeCapacity(),
                recipe.optionalMaterialLimit(),
                Texts.toStringSafe(recipe.permission()),
                recipe.blueprintRequirements().stream().map(DefaultForgeCatalog::toBlueprintView).toList(),
                recipe.hasFailureMechanism());
    }

    static ForgeBlueprintView toBlueprintView(BlueprintRequirement blueprint) {
        return new ForgeBlueprintView(Texts.toStringSafe(ItemSourceUtil.toShorthand(blueprint.source())),
                blueprint.amount());
    }

    static ForgeMaterialView toMaterialView(ForgeMaterial material) {
        return new ForgeMaterialView(Texts.lower(material.id()),
                Texts.toStringSafe(material.item()),
                material.amount(),
                material.capacityCost(),
                material.optional(),
                material.statContributions(),
                material.attributeContributions(),
                material.skillIds());
    }

    static Map<String, Object> safePlaceholders(Map<String, Object> replacements) {
        return replacements == null ? Map.of() : Map.copyOf(replacements);
    }
}
