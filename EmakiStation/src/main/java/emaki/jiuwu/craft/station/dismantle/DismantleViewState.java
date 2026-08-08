package emaki.jiuwu.craft.station.dismantle;

import java.util.List;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiSession;

/**
 * One viewer's state on the dismantle page.
 *
 * <p>The lifecycle mirrors {@code StationViewState}: this object outlives any individual
 * {@link GuiSession} so that the player's selected recipe and rolled outputs persist across
 * re-opens caused by navigation. The navigating flag and click-throttle follow the same contract
 * as in {@code StationViewState}.
 */
public final class DismantleViewState {

    private final Player viewer;
    private final DismantleStationDefinition station;

    private GuiSession guiSession;
    private DismantleRecipeDefinition selectedRecipe;
    private List<DismantleOutput> rolledOutputs = List.of();
    private int outputPage;
    private boolean processing;
    private boolean navigating;
    private long lastClickMs;

    /**
     * Creates a fresh state for one viewer at one station.
     *
     * @param viewer  the viewing player
     * @param station the station being viewed
     */
    public DismantleViewState(Player viewer, DismantleStationDefinition station) {
        this.viewer = viewer;
        this.station = station;
    }

    /** {@return the viewing player} */
    public Player viewer() {
        return viewer;
    }

    /** {@return the dismantle station being viewed} */
    public DismantleStationDefinition station() {
        return station;
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

    /** {@return the dismantle recipe being previewed, or {@code null}} */
    public DismantleRecipeDefinition selectedRecipe() {
        return selectedRecipe;
    }

    /**
     * Selects the recipe to dismantle. Clears any previously rolled outputs.
     *
     * @param recipe the recipe; {@code null} clears the selection
     */
    public void selectedRecipe(DismantleRecipeDefinition recipe) {
        this.selectedRecipe = recipe;
        this.rolledOutputs = List.of();
        this.outputPage = 0;
    }

    /** {@return the outputs from the last roll, or an empty list before the first roll} */
    public List<DismantleOutput> rolledOutputs() {
        return rolledOutputs;
    }

    /**
     * Stores the outputs from a completed roll.
     *
     * @param outputs the rolled outputs; {@code null} clears them
     */
    public void rolledOutputs(List<DismantleOutput> outputs) {
        this.rolledOutputs = outputs == null ? List.of() : List.copyOf(outputs);
        this.outputPage = 0;
    }

    /** {@return whether a roll has been performed for the current recipe selection} */
    public boolean hasRolled() {
        return !rolledOutputs.isEmpty();
    }

    /** {@return the output page number, zero-based} */
    public int outputPage() {
        return outputPage;
    }

    /**
     * Moves the output page, clamping to the available range.
     *
     * @param page       the requested page
     * @param totalPages how many pages exist
     */
    public void outputPage(int page, int totalPages) {
        int ceiling = Math.max(1, totalPages);
        this.outputPage = Math.clamp(page, 0, ceiling - 1);
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
}
