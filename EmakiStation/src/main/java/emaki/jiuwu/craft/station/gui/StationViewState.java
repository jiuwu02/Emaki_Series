package emaki.jiuwu.craft.station.gui;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.material.MergedMaterialChannel;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

public final class StationViewState {

    public enum Page {

        CATALOG,

        PREVIEW,

        QUEUE,

        DISMANTLE
    }

    private final Player viewer;
    private final StationDefinition station;

    private Page page = Page.CATALOG;
    private GuiSession guiSession;
    private int catalogPage;
    private int materialPage;
    private int queuePage;
    private long batch = 1L;
    private OutputRouting outputRouting;
    private RecipeDefinition selectedRecipe;
    private MergedMaterialChannel.Availability availability = MergedMaterialChannel.Availability.empty();
    private long availabilityAtMs;
    private String blockReason = "";
    private boolean processing;
    private boolean navigating;
    private long lastClickMs;

    public StationViewState(Player viewer, StationDefinition station) {
        this.viewer = viewer;
        this.station = station;
        this.outputRouting = station.outputRouting();
    }

    public Player viewer() {
        return viewer;
    }

    public StationDefinition station() {
        return station;
    }

    public Page page() {
        return page;
    }

    public void page(Page page) {
        this.page = page == null ? Page.CATALOG : page;
    }

    public GuiSession guiSession() {
        return guiSession;
    }

    public void attach(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    public int catalogPage() {
        return catalogPage;
    }

    public void catalogPage(int page, int totalPages) {
        this.catalogPage = clampPage(page, totalPages);
    }

    public int materialPage() {
        return materialPage;
    }

    public void materialPage(int page, int totalPages) {
        this.materialPage = clampPage(page, totalPages);
    }

    public int queuePage() {
        return queuePage;
    }

    public void queuePage(int page, int totalPages) {
        this.queuePage = clampPage(page, totalPages);
    }

    public long batch() {
        return batch;
    }

    public void batch(long batch) {
        this.batch = Math.max(1L, batch);
    }

    public OutputRouting outputRouting() {
        return outputRouting;
    }

    public void outputRouting(OutputRouting outputRouting) {
        if (outputRouting != null) {
            this.outputRouting = outputRouting;
        }
    }

    public RecipeDefinition selectedRecipe() {
        return selectedRecipe;
    }

    public void selectedRecipe(RecipeDefinition recipe) {
        this.selectedRecipe = recipe;
        this.materialPage = 0;
        this.batch = 1L;
    }

    public MergedMaterialChannel.Availability availability() {
        return availability;
    }

    public void availability(MergedMaterialChannel.Availability availability, long nowMs) {
        this.availability = availability == null
                ? MergedMaterialChannel.Availability.empty()
                : availability;
        this.availabilityAtMs = nowMs;
    }

    public boolean availabilityStale(long nowMs, long maxAgeMs) {
        return availabilityAtMs <= 0L || nowMs - availabilityAtMs >= maxAgeMs;
    }

    public String blockReason() {
        return blockReason;
    }

    public void blockReason(String blockReason) {
        this.blockReason = blockReason == null ? "" : blockReason;
    }

    public boolean processing() {
        return processing;
    }

    public void processing(boolean processing) {
        this.processing = processing;
    }

    public void beginNavigation() {
        this.navigating = true;
    }

    public boolean consumeNavigation() {
        boolean wasNavigating = navigating;
        navigating = false;
        return wasNavigating;
    }

    public boolean acceptClick(long nowMs, long throttleMs) {
        if (throttleMs > 0L && nowMs - lastClickMs < throttleMs) {
            return false;
        }
        lastClickMs = nowMs;
        return true;
    }

    private static int clampPage(int page, int totalPages) {
        int ceiling = Math.max(1, totalPages);
        return Math.clamp(page, 0, ceiling - 1);
    }
}
