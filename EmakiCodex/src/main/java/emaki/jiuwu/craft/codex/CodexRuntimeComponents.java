package emaki.jiuwu.craft.codex;

import java.util.Map;

import emaki.jiuwu.craft.codex.advancement.AdvancementJsonBuilder;
import emaki.jiuwu.craft.codex.advancement.AdvancementPlatform;
import emaki.jiuwu.craft.codex.advancement.AdvancementRegistrar;
import emaki.jiuwu.craft.codex.advancement.AdvancementService;
import emaki.jiuwu.craft.codex.advancement.loader.AdvancementPageLoader;
import emaki.jiuwu.craft.codex.advancement.packet.AdvancementPacketGateway;
import emaki.jiuwu.craft.codex.config.AppConfig;
import emaki.jiuwu.craft.codex.recipe.RecipeCollector;
import emaki.jiuwu.craft.codex.recipe.RecipeIndex;
import emaki.jiuwu.craft.codex.recipe.RecipeVisibilityService;
import emaki.jiuwu.craft.codex.recipe.loader.ManualRecipeLoader;
import emaki.jiuwu.craft.codex.recipe.sync.RecipeSyncGateway;
import emaki.jiuwu.craft.codex.store.PlayerUnlockStore;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.runtime.RuntimeComponents;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;

/**
 * Immutable bundle of every runtime service produced by {@link CodexLifecycleCoordinator}
 * during initialization and handed to {@link EmakiCodexPlugin}.
 */
record CodexRuntimeComponents(YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        PlayerUnlockStore unlockStore,
        ManualRecipeLoader manualRecipeLoader,
        AdvancementPageLoader advancementPageLoader,
        RecipeIndex recipeIndex,
        RecipeCollector recipeCollector,
        RecipeVisibilityService recipeVisibilityService,
        RecipeSyncGateway recipeSyncGateway,
        AdvancementPlatform advancementPlatform,
        AdvancementJsonBuilder advancementJsonBuilder,
        AdvancementRegistrar advancementRegistrar,
        AdvancementService advancementService,
        AdvancementPacketGateway advancementPacketGateway) implements RuntimeComponents {

    @Override
    public Map<Class<?>, Object> services() {
        return RuntimeComponents.services(
                RuntimeComponents.component(YamlConfigLoader.class, appConfigLoader),
                RuntimeComponents.component(LanguageLoader.class, languageLoader),
                RuntimeComponents.component(MessageService.class, messageService),
                RuntimeComponents.component(BootstrapService.class, bootstrapService),
                RuntimeComponents.component(PlayerUnlockStore.class, unlockStore),
                RuntimeComponents.component(ManualRecipeLoader.class, manualRecipeLoader),
                RuntimeComponents.component(AdvancementPageLoader.class, advancementPageLoader),
                RuntimeComponents.component(RecipeIndex.class, recipeIndex),
                RuntimeComponents.component(RecipeCollector.class, recipeCollector),
                RuntimeComponents.component(RecipeVisibilityService.class, recipeVisibilityService),
                RuntimeComponents.component(RecipeSyncGateway.class, recipeSyncGateway),
                RuntimeComponents.component(AdvancementPlatform.class, advancementPlatform),
                RuntimeComponents.component(AdvancementJsonBuilder.class, advancementJsonBuilder),
                RuntimeComponents.component(AdvancementRegistrar.class, advancementRegistrar),
                RuntimeComponents.component(AdvancementService.class, advancementService),
                RuntimeComponents.component(AdvancementPacketGateway.class, advancementPacketGateway)
        );
    }
}
