package emaki.jiuwu.craft.station.dismantle;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.gui.StationSlotType;
import emaki.jiuwu.craft.station.material.OutputDelivery;

/**
 * Routes all slot-clicks on the dismantle page.
 *
 * <p>The caller is responsible for cancelling the event <em>before</em> delegating here. This class
 * only decides what the click means and mutates the {@link DismantleViewState} accordingly.
 *
 * <p>Interaction lifecycle:
 * <ol>
 *   <li>Player opens the dismantle page with an item in hand (matched by
 *       {@link DismantleService#canDismantle}).</li>
 *   <li>Player clicks {@code DISMANTLE_CONFIRM}: the item is consumed and
 *       {@link DismantleService#dismantle} is called to roll the loot pool.  The results are stored
 *       in {@link DismantleViewState#rolledOutputs()} and the GUI redraws.</li>
 *   <li>Player clicks {@code DISMANTLE_CONFIRM} again: the rolled outputs are delivered and the
 *       state is cleared.</li>
 *   <li>Player navigates pages with {@code PREV_PAGE} / {@code NEXT_PAGE}.</li>
 *   <li>Player navigates back to the catalog page with {@code BACK} or closes with
 *       {@code CLOSE}.</li>
 * </ol>
 */
public final class DismantleGuiInteractionController {

    private final DismantleService dismantleService;
    private final OutputDelivery outputDelivery;
    private final ItemSourceService itemSourceService;

    /**
     * Creates the controller.
     *
     * @param dismantleService  the service that performs the loot roll
     * @param outputDelivery    delivers rolled items to the player
     * @param itemSourceService CoreLib's item-source service, used to identify input items
     */
    public DismantleGuiInteractionController(DismantleService dismantleService,
            OutputDelivery outputDelivery,
            ItemSourceService itemSourceService) {
        this.dismantleService = dismantleService;
        this.outputDelivery = outputDelivery;
        this.itemSourceService = itemSourceService;
    }

    /**
     * Handles one dismantle-page click.
     *
     * @param state     the viewer's dismantle state
     * @param slotType  the normalised slot type / key of the clicked slot
     * @param isRight   whether the click was a right-click
     * @param slot      the resolved slot, used for page-size calculation
     * @param redrawFn  called after any state mutation that requires a redraw
     * @param openCatalogFn called when the player requests the catalog page
     */
    public void onClick(DismantleViewState state,
            String slotType,
            boolean isRight,
            GuiTemplate.ResolvedSlot slot,
            Runnable redrawFn,
            Runnable openCatalogFn) {
        if (state.processing()) {
            return;
        }
        switch (slotType) {
            case StationSlotType.DISMANTLE_CONFIRM -> onConfirm(state, redrawFn);
            case StationSlotType.PREV_PAGE -> movePage(state, -1, redrawFn);
            case StationSlotType.NEXT_PAGE -> movePage(state, 1, redrawFn);
            case StationSlotType.BACK -> openCatalogFn.run();
            case StationSlotType.CLOSE -> state.viewer().closeInventory();
            default -> {
                // Decorative or unrecognised slot; nothing to do.
            }
        }
    }

    /**
     * Handles a click on the confirm / claim button.
     *
     * <p>First click: consume the input item from the player's inventory and roll the loot pool.
     * Second click (after a roll): deliver the rolled outputs and clear the state.
     *
     * @param state    the viewer's dismantle state
     * @param redrawFn called after state mutation
     */
    private void onConfirm(DismantleViewState state, Runnable redrawFn) {
        if (state.hasRolled()) {
            claimOutputs(state, redrawFn);
        } else {
            performDismantle(state, redrawFn);
        }
    }

    /**
     * Removes the matched input item from the player's cursor or top-inventory slot and rolls loot.
     *
     * @param state    the viewer's dismantle state
     * @param redrawFn called after state mutation
     */
    private void performDismantle(DismantleViewState state, Runnable redrawFn) {
        DismantleRecipeDefinition recipe = state.selectedRecipe();
        if (recipe == null) {
            return;
        }
        // Consume one input item from the player's main inventory.
        boolean consumed = consumeInput(state, recipe);
        if (!consumed) {
            // Player no longer has the item; clear selection and redraw.
            state.selectedRecipe(null);
            redrawFn.run();
            return;
        }
        List<DismantleOutput> outputs = dismantleService.roll(recipe);
        state.rolledOutputs(outputs);
        redrawFn.run();
    }

    /**
     * Delivers the rolled outputs to the player and clears the rolled state.
     *
     * @param state    the viewer's dismantle state
     * @param redrawFn called after state mutation
     */
    private void claimOutputs(DismantleViewState state, Runnable redrawFn) {
        List<DismantleOutput> outputs = state.rolledOutputs();
        if (outputs.isEmpty()) {
            state.rolledOutputs(null);
            redrawFn.run();
            return;
        }
        // Convert DismantleOutput → PendingOutput for the delivery API.
        List<PendingOutput> pending = new ArrayList<>();
        for (DismantleOutput output : outputs) {
            pending.add(new PendingOutput(output.source(), output.amount()));
        }
        state.processing(true);
        // Deliver to player using the station's default output routing.
        OutputRouting routing = state.station().outputRouting();
        outputDelivery.deliverAsync(state.viewer(), pending, routing)
                .whenComplete((result, error) -> {
                    state.rolledOutputs(null);
                    state.processing(false);
                    redrawFn.run();
                });
    }

    /**
     * Tries to remove one unit of the recipe's input item from the player's inventory.
     *
     * @param state  the viewer's dismantle state
     * @param recipe the recipe being dismantled
     * @return {@code true} when the item was found and consumed
     */
    private boolean consumeInput(DismantleViewState state, DismantleRecipeDefinition recipe) {
        Player player = state.viewer();
        PlayerInventory inv = player.getInventory();
        ItemSourceRef target = recipe.inputSource();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemSourceRef identified = itemSourceService == null ? null
                    : itemSourceService.identifyItem(item);
            if (!target.equals(identified)) {
                continue;
            }
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                inv.setItem(i, null);
            }
            return true;
        }
        return false;
    }

    /**
     * Moves the output page by {@code delta} and redraws.
     *
     * @param state    the viewer's dismantle state
     * @param delta    the page delta (+1 or -1)
     * @param redrawFn called after state mutation
     */
    private void movePage(DismantleViewState state, int delta, Runnable redrawFn) {
        GuiSession guiSession = state.guiSession();
        if (guiSession == null) {
            return;
        }
        DismantleRecipeDefinition recipe = state.selectedRecipe();
        if (recipe == null) {
            return;
        }
        int listSize = state.hasRolled()
                ? state.rolledOutputs().size()
                : recipe.pool().size();
        int pageSize = Math.max(1, GuiPagination.pageSize(
                guiSession.template(), StationSlotType.DISMANTLE_OUTPUT_LIST));
        state.outputPage(state.outputPage() + delta,
                GuiPagination.totalPages(listSize, pageSize));
        redrawFn.run();
    }
}
