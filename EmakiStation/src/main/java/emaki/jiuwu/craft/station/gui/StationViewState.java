package emaki.jiuwu.craft.station.gui;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.material.MergedMaterialChannel;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

/**
 * One viewer's position inside a station's three pages.
 *
 * <h2>Why this outlives the GUI session</h2>
 * {@code GuiService.open()} closes whatever window the viewer already had, which fires the previous handler's
 * {@code onClose}. Opening the material preview therefore destroys the catalog's session, and returning from
 * the preview destroys the preview's. Anything stored on a {@code GuiSession} is gone by the time the viewer
 * comes back.
 *
 * <p>So the page numbers live here instead, in a map keyed by player id that the GUI service owns. The catalog
 * page survives a preview round trip for the simple reason that nothing on that trip touches it.
 *
 * <h2>The navigating flag</h2>
 * {@code onClose} cannot tell "the viewer closed the window" from "we are opening a sibling page" — both arrive
 * as the same event. {@link #beginNavigation()} is set immediately before opening a sibling and consumed by the
 * close handler, which then leaves this state alone. A close without that flag is a real close and discards it.
 *
 * <p>Not thread-safe. Every access happens on the viewer's owner thread.
 */
public final class StationViewState {

    /** Which of the three pages the viewer is currently looking at. */
    public enum Page {

        /** The recipe catalog. */
        CATALOG,

        /** The material preview for one recipe. */
        PREVIEW,

        /** The craft queue. */
        QUEUE
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

    /**
     * Creates a fresh state for one viewer at one station.
     *
     * @param viewer  the viewing player
     * @param station the station being viewed
     */
    public StationViewState(Player viewer, StationDefinition station) {
        this.viewer = viewer;
        this.station = station;
        this.outputRouting = station.outputRouting();
    }

    /** {@return the viewing player} */
    public Player viewer() {
        return viewer;
    }

    /** {@return the station being viewed} */
    public StationDefinition station() {
        return station;
    }

    /** {@return which page is open} */
    public Page page() {
        return page;
    }

    /**
     * Records which page is now open.
     *
     * @param page the page
     */
    public void page(Page page) {
        this.page = page == null ? Page.CATALOG : page;
    }

    /** {@return the CoreLib session backing the open window, or {@code null}} */
    public GuiSession guiSession() {
        return guiSession;
    }

    /**
     * Binds the CoreLib session for the window that was just opened.
     *
     * @param guiSession the session
     */
    public void attach(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    /** {@return the catalog page number, zero-based} */
    public int catalogPage() {
        return catalogPage;
    }

    /**
     * Moves the catalog page, clamping to the available range.
     *
     * @param page       the requested page
     * @param totalPages how many pages exist
     */
    public void catalogPage(int page, int totalPages) {
        this.catalogPage = clampPage(page, totalPages);
    }

    /** {@return the material page number, zero-based} */
    public int materialPage() {
        return materialPage;
    }

    /**
     * Moves the material page, clamping to the available range.
     *
     * @param page       the requested page
     * @param totalPages how many pages exist
     */
    public void materialPage(int page, int totalPages) {
        this.materialPage = clampPage(page, totalPages);
    }

    /** {@return the queue page number, zero-based} */
    public int queuePage() {
        return queuePage;
    }

    /**
     * Moves the queue page, clamping to the available range.
     *
     * @param page       the requested page
     * @param totalPages how many pages exist
     */
    public void queuePage(int page, int totalPages) {
        this.queuePage = clampPage(page, totalPages);
    }

    /** {@return the current batch multiplier} */
    public long batch() {
        return batch;
    }

    /**
     * Sets the batch multiplier.
     *
     * @param batch the multiplier; values below one become one
     */
    public void batch(long batch) {
        this.batch = Math.max(1L, batch);
    }

    /** {@return where finished outputs go} */
    public OutputRouting outputRouting() {
        return outputRouting;
    }

    /**
     * Sets the output destination.
     *
     * @param outputRouting the destination; {@code null} is ignored
     */
    public void outputRouting(OutputRouting outputRouting) {
        if (outputRouting != null) {
            this.outputRouting = outputRouting;
        }
    }

    /** {@return the recipe being previewed, or {@code null}} */
    public RecipeDefinition selectedRecipe() {
        return selectedRecipe;
    }

    /**
     * Selects the recipe to preview, resetting the material page.
     *
     * @param recipe the recipe; {@code null} clears the selection
     */
    public void selectedRecipe(RecipeDefinition recipe) {
        this.selectedRecipe = recipe;
        this.materialPage = 0;
        this.batch = 1L;
    }

    /** {@return the cached material snapshot} */
    public MergedMaterialChannel.Availability availability() {
        return availability;
    }

    /**
     * Caches a material snapshot and stamps it.
     *
     * @param availability the snapshot; {@code null} becomes empty
     * @param nowMs        the current wall-clock time
     */
    public void availability(MergedMaterialChannel.Availability availability, long nowMs) {
        this.availability = availability == null
                ? MergedMaterialChannel.Availability.empty()
                : availability;
        this.availabilityAtMs = nowMs;
    }

    /**
     * Tests whether the cached snapshot is old enough to re-read.
     *
     * @param nowMs      the current wall-clock time
     * @param maxAgeMs   how long a snapshot stays usable
     * @return whether a refresh is due
     */
    public boolean availabilityStale(long nowMs, long maxAgeMs) {
        return availabilityAtMs <= 0L || nowMs - availabilityAtMs >= maxAgeMs;
    }

    /** {@return why the current selection cannot be submitted, or an empty string} */
    public String blockReason() {
        return blockReason;
    }

    /**
     * Records why the current selection cannot be submitted.
     *
     * @param blockReason the reason key; {@code null} becomes an empty string
     */
    public void blockReason(String blockReason) {
        this.blockReason = blockReason == null ? "" : blockReason;
    }

    /** {@return whether an asynchronous operation is in flight for this viewer} */
    public boolean processing() {
        return processing;
    }

    /**
     * Marks an asynchronous operation as started or finished.
     *
     * @param processing whether an operation is in flight
     */
    public void processing(boolean processing) {
        this.processing = processing;
    }

    /**
     * Declares that the next close event is a page change, not a real close.
     *
     * <p>Call immediately before opening a sibling page.
     */
    public void beginNavigation() {
        this.navigating = true;
    }

    /**
     * Reads and clears the navigation flag.
     *
     * @return whether the close being handled is a page change
     */
    public boolean consumeNavigation() {
        boolean wasNavigating = navigating;
        navigating = false;
        return wasNavigating;
    }

    /**
     * Applies the click throttle.
     *
     * @param nowMs      the current wall-clock time
     * @param throttleMs the minimum gap between accepted clicks
     * @return whether this click should be accepted
     */
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
