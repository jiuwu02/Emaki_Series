package emaki.jiuwu.craft.forge;

import java.util.Objects;

import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.ForgeLookupIndex;

record ForgeReloadCandidate(long generation,
                            YamlConfigLoader<AppConfig> appConfigLoader,
                            LanguageLoader languageLoader,
                            MessageService messageService,
                            BootstrapService bootstrapService,
                            RecipeLoader recipeLoader,
                            GuiTemplateLoader guiTemplateLoader,
                            RecipeLoader.RecipeLoadReport recipeReport,
                            ForgeLookupIndex.Snapshot lookupSnapshot,
                            long loadDurationNanos) {

    ForgeReloadCandidate {
        appConfigLoader = Objects.requireNonNull(appConfigLoader, "appConfigLoader");
        languageLoader = Objects.requireNonNull(languageLoader, "languageLoader");
        messageService = Objects.requireNonNull(messageService, "messageService");
        bootstrapService = Objects.requireNonNull(bootstrapService, "bootstrapService");
        recipeLoader = Objects.requireNonNull(recipeLoader, "recipeLoader");
        guiTemplateLoader = Objects.requireNonNull(guiTemplateLoader, "guiTemplateLoader");
        recipeReport = recipeReport == null ? RecipeLoader.RecipeLoadReport.empty(generation) : recipeReport;
        lookupSnapshot = lookupSnapshot == null ? ForgeLookupIndex.Snapshot.empty(generation) : lookupSnapshot;
        loadDurationNanos = Math.max(0L, loadDurationNanos);
    }

    boolean blocking() {
        return recipeReport.hasBlockingIssues();
    }
}
