package emaki.jiuwu.craft.station.gui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.station.api.model.MaterialChannel;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.definition.StationDefinition;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;
import emaki.jiuwu.craft.station.recipe.RecipeMatch;

/**
 * Per-viewer state for one open station window.
 *
 * <p><strong>The input slots hold the player's own items.</strong> They are stored here rather than in the
 * Bukkit inventory alone so the teardown path can always hand them back: on close, on disconnect, and on
 * plugin disable. They are deliberately not persisted, which means a hard crash loses them — the GUI text
 * says the input area is not storage.
 *
 * <p>Not thread-safe; every access happens on the viewer's owner thread.
 */
public final class StationGuiSession {

    private final Player viewer;
    private final StationDefinition station;
    private final Map<Integer, ItemStack> inputs = new LinkedHashMap<>();

    private GuiSession guiSession;
    private MaterialChannel channel;
    private OutputRouting outputRouting;
    private RecipeMatch currentMatch = RecipeMatch.none();
    private RecipeDefinition selectedRecipe;
    private int alternativeIndex;
    private long batch = 1L;
    private int materialPage;
    private boolean processing;
    private String blockReason = "";

    /**
     * Creates a session.
     *
     * @param viewer        the viewing player
     * @param station       the station being viewed
     * @param storageUsable whether the warehouse is reachable right now
     */
    public StationGuiSession(Player viewer, StationDefinition station, boolean storageUsable) {
        this.viewer = viewer;
        this.station = station;
        this.channel = station.startingChannel(storageUsable);
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

    /** {@return the CoreLib session backing this window, or {@code null} before it opens} */
    public GuiSession guiSession() {
        return guiSession;
    }

    /**
     * Attaches the CoreLib session once the window is open.
     *
     * @param guiSession the backing session
     */
    public void attach(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    /** {@return the input slot contents keyed by inventory slot; a live view} */
    public Map<Integer, ItemStack> inputs() {
        return inputs;
    }

    /** {@return the active material channel} */
    public MaterialChannel channel() {
        return channel;
    }

    /**
     * Switches the active material channel.
     *
     * @param channel the channel to use
     */
    public void channel(MaterialChannel channel) {
        this.channel = channel == null ? MaterialChannel.BACKPACK : channel;
    }

    /** {@return where finished outputs go} */
    public OutputRouting outputRouting() {
        return outputRouting;
    }

    /**
     * Changes where finished outputs go.
     *
     * @param outputRouting the routing to use
     */
    public void outputRouting(OutputRouting outputRouting) {
        this.outputRouting = outputRouting == null ? station.outputRouting() : outputRouting;
    }

    /** {@return the most recent match result} */
    public RecipeMatch currentMatch() {
        return currentMatch;
    }

    /**
     * Records a new match result, resetting the alternative cursor when the option set changed.
     *
     * @param match the new result
     */
    public void currentMatch(RecipeMatch match) {
        RecipeMatch resolved = match == null ? RecipeMatch.none() : match;
        boolean sameOptions = sameAlternatives(this.currentMatch, resolved);
        this.currentMatch = resolved;
        if (!sameOptions) {
            this.alternativeIndex = 0;
        }
        this.selectedRecipe = resolveSelected();
    }

    /** {@return the recipe the viewer is acting on, or {@code null} when nothing matched} */
    public RecipeDefinition selectedRecipe() {
        return selectedRecipe;
    }

    /**
     * Selects a recipe explicitly, which is how the warehouse channel picks one without inputs.
     *
     * @param recipe the recipe to select
     */
    public void selectedRecipe(RecipeDefinition recipe) {
        this.selectedRecipe = recipe;
    }

    /** Advances to the next matching recipe, wrapping around. */
    public void cycleAlternative() {
        List<RecipeDefinition> alternatives = currentMatch.alternatives();
        if (alternatives.size() <= 1) {
            return;
        }
        alternativeIndex = (alternativeIndex + 1) % alternatives.size();
        selectedRecipe = alternatives.get(alternativeIndex);
    }

    /** {@return the requested batch multiplier; always at least 1} */
    public long batch() {
        return Math.max(1L, batch);
    }

    /**
     * Sets the requested batch multiplier.
     *
     * @param batch the multiplier; values below 1 are clamped
     */
    public void batch(long batch) {
        this.batch = Math.max(1L, batch);
    }

    /** {@return the current material-list page} */
    public int materialPage() {
        return materialPage;
    }

    /**
     * Sets the material-list page.
     *
     * @param materialPage the page index; negatives are clamped to zero
     */
    public void materialPage(int materialPage) {
        this.materialPage = Math.max(0, materialPage);
    }

    /** {@return whether an operation is in flight, during which clicks are ignored} */
    public boolean processing() {
        return processing;
    }

    /**
     * Marks whether an operation is in flight.
     *
     * @param processing whether to block further clicks
     */
    public void processing(boolean processing) {
        this.processing = processing;
    }

    /** {@return why submission is currently blocked, or an empty string when it is not} */
    public String blockReason() {
        return blockReason;
    }

    /**
     * Records why submission is blocked.
     *
     * @param blockReason the reason text; {@code null} becomes an empty string
     */
    public void blockReason(String blockReason) {
        this.blockReason = blockReason == null ? "" : blockReason;
    }

    /** {@return every non-empty input stack, for handing items back} */
    public List<ItemStack> drainInputs() {
        List<ItemStack> held = new java.util.ArrayList<>();
        for (ItemStack stack : inputs.values()) {
            if (stack != null && !stack.getType().isAir()) {
                held.add(stack);
            }
        }
        inputs.clear();
        return held;
    }

    private RecipeDefinition resolveSelected() {
        List<RecipeDefinition> alternatives = currentMatch.alternatives();
        if (alternatives.isEmpty()) {
            return null;
        }
        int index = Math.clamp(alternativeIndex, 0, alternatives.size() - 1);
        return alternatives.get(index);
    }

    private static boolean sameAlternatives(RecipeMatch left, RecipeMatch right) {
        if (left.alternatives().size() != right.alternatives().size()) {
            return false;
        }
        for (int index = 0; index < left.alternatives().size(); index++) {
            if (!left.alternatives().get(index).id().equals(right.alternatives().get(index).id())) {
                return false;
            }
        }
        return true;
    }
}
