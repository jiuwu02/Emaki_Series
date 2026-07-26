package emaki.jiuwu.craft.forge;

import emaki.jiuwu.craft.corelib.bootstrap.BootstrapService;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.forge.integration.ForgeAttributeBridge;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.yaml.YamlConfigLoader;
import emaki.jiuwu.craft.forge.config.AppConfig;
import emaki.jiuwu.craft.forge.loader.PlayerDataStore;
import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.ForgeGuiService;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;
import emaki.jiuwu.craft.forge.service.ForgeLookupIndex;
import emaki.jiuwu.craft.forge.service.ForgeService;
import emaki.jiuwu.craft.forge.service.ItemIdentifierService;
import emaki.jiuwu.craft.forge.service.RecipeBookGuiService;

public record ForgeRuntimeSnapshot(long generation,
        ForgeRuntimeStatus status,
        ForgeRuntimeComponents components,
        YamlConfigLoader<AppConfig> appConfigLoader,
        LanguageLoader languageLoader,
        MessageService messageService,
        BootstrapService bootstrapService,
        RecipeLoader recipeLoader,
        GuiTemplateLoader guiTemplateLoader,
        RecipeLoader.RecipeLoadReport recipeReport,
        ForgeLookupIndex.Snapshot lookupSnapshot,
        long installedAtNanos) {

    public ForgeRuntimeSnapshot {
        status = status == null ? ForgeRuntimeStatus.UNAVAILABLE : status;
        recipeReport = recipeReport == null ? RecipeLoader.RecipeLoadReport.empty(generation) : recipeReport;
        lookupSnapshot = lookupSnapshot == null ? ForgeLookupIndex.Snapshot.empty(generation) : lookupSnapshot;
        installedAtNanos = Math.max(0L, installedAtNanos);
    }

    public static ForgeRuntimeSnapshot starting(ForgeRuntimeComponents components,
            YamlConfigLoader<AppConfig> appConfigLoader,
            LanguageLoader languageLoader,
            MessageService messageService,
            BootstrapService bootstrapService,
            RecipeLoader recipeLoader,
            GuiTemplateLoader guiTemplateLoader) {
        return new ForgeRuntimeSnapshot(0L, ForgeRuntimeStatus.STARTING, components, appConfigLoader, languageLoader,
                messageService, bootstrapService, recipeLoader, guiTemplateLoader,
                RecipeLoader.RecipeLoadReport.empty(0L),
                ForgeLookupIndex.Snapshot.empty(0L), System.nanoTime());
    }

    static ForgeRuntimeSnapshot unavailable(long generation,
            ForgeReloadCandidate candidate,
            ForgeRuntimeStatus status) {
        return new ForgeRuntimeSnapshot(
                generation,
                status == null ? ForgeRuntimeStatus.UNAVAILABLE : status,
                null,
                candidate == null ? null : candidate.appConfigLoader(),
                candidate == null ? null : candidate.languageLoader(),
                candidate == null ? null : candidate.messageService(),
                candidate == null ? null : candidate.bootstrapService(),
                candidate == null ? null : candidate.recipeLoader(),
                candidate == null ? null : candidate.guiTemplateLoader(),
                candidate == null ? RecipeLoader.RecipeLoadReport.empty(generation) : candidate.recipeReport(),
                candidate == null ? ForgeLookupIndex.Snapshot.empty(generation) : candidate.lookupSnapshot(),
                System.nanoTime()
        );
    }

    static ForgeRuntimeSnapshot active(ForgeReloadCandidate candidate,
            ForgeRuntimeComponents components) {
        return new ForgeRuntimeSnapshot(
                candidate.generation(),
                ForgeRuntimeStatus.ACTIVE,
                components,
                candidate.appConfigLoader(),
                candidate.languageLoader(),
                candidate.messageService(),
                candidate.bootstrapService(),
                candidate.recipeLoader(),
                candidate.guiTemplateLoader(),
                candidate.recipeReport(),
                candidate.lookupSnapshot(),
                System.nanoTime()
        );
    }

    public ForgeRuntimeSnapshot withStatus(ForgeRuntimeStatus nextStatus) {
        return new ForgeRuntimeSnapshot(generation, nextStatus, components, appConfigLoader, languageLoader,
                messageService, bootstrapService, recipeLoader, guiTemplateLoader,
                recipeReport, lookupSnapshot, installedAtNanos);
    }

    public boolean available() {
        return status == ForgeRuntimeStatus.ACTIVE
                && components != null
                && appConfigLoader != null
                && languageLoader != null
                && messageService != null
                && bootstrapService != null
                && recipeLoader != null
                && guiTemplateLoader != null
                && !recipeReport.hasBlockingIssues()
                && lookupSnapshot.generation() == generation;
    }

    public ForgeGuiState guiState() {
        if (status == ForgeRuntimeStatus.CLOSING || status == ForgeRuntimeStatus.CLOSED) {
            return ForgeGuiState.CLOSED;
        }
        if (status == ForgeRuntimeStatus.STARTING || status == ForgeRuntimeStatus.RELOADING) {
            return ForgeGuiState.RELOADING;
        }
        if (status == ForgeRuntimeStatus.UNAVAILABLE) {
            return recipeReport.hasBlockingCapabilityIssues()
                    ? ForgeGuiState.CAPABILITY_MISSING
                    : ForgeGuiState.LOAD_FAILED;
        }
        if (recipeReport.hasBlockingCapabilityIssues()) {
            return ForgeGuiState.CAPABILITY_MISSING;
        }
        if (recipeReport.hasBlockingIssues()) {
            return ForgeGuiState.LOAD_FAILED;
        }
        if (recipeLoader == null || recipeLoader.all().isEmpty()) {
            return ForgeGuiState.NO_RECIPES;
        }
        if (lookupSnapshot == null || lookupSnapshot.generation() != generation) {
            return ForgeGuiState.INDEX_UNAVAILABLE;
        }
        return ForgeGuiState.READY;
    }

    public ExecutionDispatcher executionDispatcher() {
        return components == null ? null : components.executionDispatcher();
    }

    public ThreadOwnership threadOwnership() {
        return components == null ? null : components.threadOwnership();
    }

    public PlayerDataStore playerDataStore() {
        return components == null ? null : components.playerDataStore();
    }

    public GuiService guiService() {
        return components == null ? null : components.guiService();
    }

    public ItemIdentifierService itemIdentifierService() {
        return components == null ? null : components.itemIdentifierService();
    }

    public ForgeAttributeBridge pdcAttributeGateway() {
        return components == null ? null : components.pdcAttributeGateway();
    }

    public ForgeItemRefreshService itemRefreshService() {
        return components == null ? null : components.itemRefreshService();
    }

    public ForgeService forgeService() {
        return components == null ? null : components.forgeService();
    }

    public ForgeGuiService forgeGuiService() {
        return components == null ? null : components.forgeGuiService();
    }

    public RecipeBookGuiService recipeBookGuiService() {
        return components == null ? null : components.recipeBookGuiService();
    }
}
