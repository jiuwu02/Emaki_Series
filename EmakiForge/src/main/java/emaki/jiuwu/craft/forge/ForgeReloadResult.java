package emaki.jiuwu.craft.forge;

import emaki.jiuwu.craft.forge.loader.RecipeLoader;
import emaki.jiuwu.craft.forge.service.ForgeItemRefreshService;

public record ForgeReloadResult(long requestedGeneration,
                                long runtimeGeneration,
                                Outcome outcome,
                                int recipes,
                                int guiTemplates,
                                ForgeItemRefreshService.RefreshSummary refreshSummary,
                                RecipeLoader.RecipeLoadReport recipeReport,
                                String detail,
                                long durationNanos) {

    public enum Outcome {
        SUCCESS,
        WARNING,
        FAILED,
        FAILED_PRESERVED,
        FAILED_UNAVAILABLE,
        STALE,
        CLOSED
    }

    public ForgeReloadResult {
        outcome = outcome == null ? Outcome.FAILED_UNAVAILABLE : outcome;
        refreshSummary = refreshSummary == null ? ForgeItemRefreshService.RefreshSummary.empty() : refreshSummary;
        recipeReport = recipeReport == null ? RecipeLoader.RecipeLoadReport.empty(requestedGeneration) : recipeReport;
        detail = detail == null ? "" : detail;
        durationNanos = Math.max(0L, durationNanos);
    }

    public boolean installed() {
        return outcome == Outcome.SUCCESS || outcome == Outcome.WARNING || outcome == Outcome.FAILED;
    }

    public boolean successful() {
        return outcome == Outcome.SUCCESS || outcome == Outcome.WARNING;
    }

    public boolean warning() {
        return outcome == Outcome.WARNING;
    }

    public boolean stale() {
        return outcome == Outcome.STALE;
    }
}
