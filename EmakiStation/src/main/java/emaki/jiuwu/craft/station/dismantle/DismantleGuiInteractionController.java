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

public final class DismantleGuiInteractionController {

    private final DismantleService dismantleService;
    private final OutputDelivery outputDelivery;
    private final ItemSourceService itemSourceService;

    public DismantleGuiInteractionController(DismantleService dismantleService,
            OutputDelivery outputDelivery,
            ItemSourceService itemSourceService) {
        this.dismantleService = dismantleService;
        this.outputDelivery = outputDelivery;
        this.itemSourceService = itemSourceService;
    }

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

            }
        }
    }

    private void onConfirm(DismantleViewState state, Runnable redrawFn) {
        if (state.hasRolled()) {
            claimOutputs(state, redrawFn);
        } else {
            performDismantle(state, redrawFn);
        }
    }

    private void performDismantle(DismantleViewState state, Runnable redrawFn) {
        DismantleRecipeDefinition recipe = state.selectedRecipe();
        if (recipe == null) {
            return;
        }

        boolean consumed = consumeInput(state, recipe);
        if (!consumed) {

            state.selectedRecipe(null);
            redrawFn.run();
            return;
        }
        List<DismantleOutput> outputs = dismantleService.roll(recipe);
        state.rolledOutputs(outputs);
        redrawFn.run();
    }

    private void claimOutputs(DismantleViewState state, Runnable redrawFn) {
        List<DismantleOutput> outputs = state.rolledOutputs();
        if (outputs.isEmpty()) {
            state.rolledOutputs(null);
            redrawFn.run();
            return;
        }

        List<PendingOutput> pending = new ArrayList<>();
        for (DismantleOutput output : outputs) {
            pending.add(new PendingOutput(output.source(), output.amount()));
        }
        state.processing(true);

        OutputRouting routing = state.station().outputRouting();
        outputDelivery.deliverAsync(state.viewer(), pending, routing)
                .whenComplete((result, error) -> {
                    state.rolledOutputs(null);
                    state.processing(false);
                    redrawFn.run();
                });
    }

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
