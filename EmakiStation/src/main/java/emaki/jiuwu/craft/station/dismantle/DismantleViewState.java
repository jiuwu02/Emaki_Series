package emaki.jiuwu.craft.station.dismantle;

import java.util.List;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiSession;

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

    public DismantleViewState(Player viewer, DismantleStationDefinition station) {
        this.viewer = viewer;
        this.station = station;
    }

    public Player viewer() {
        return viewer;
    }

    public DismantleStationDefinition station() {
        return station;
    }

    public GuiSession guiSession() {
        return guiSession;
    }

    public void attach(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    public DismantleRecipeDefinition selectedRecipe() {
        return selectedRecipe;
    }

    public void selectedRecipe(DismantleRecipeDefinition recipe) {
        this.selectedRecipe = recipe;
        this.rolledOutputs = List.of();
        this.outputPage = 0;
    }

    public List<DismantleOutput> rolledOutputs() {
        return rolledOutputs;
    }

    public void rolledOutputs(List<DismantleOutput> outputs) {
        this.rolledOutputs = outputs == null ? List.of() : List.copyOf(outputs);
        this.outputPage = 0;
    }

    public boolean hasRolled() {
        return !rolledOutputs.isEmpty();
    }

    public int outputPage() {
        return outputPage;
    }

    public void outputPage(int page, int totalPages) {
        int ceiling = Math.max(1, totalPages);
        this.outputPage = Math.clamp(page, 0, ceiling - 1);
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
}
