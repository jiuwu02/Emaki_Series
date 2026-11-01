package emaki.jiuwu.craft.station.dismantle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import emaki.jiuwu.craft.corelib.api.action.ActionResult;
import emaki.jiuwu.craft.corelib.economy.EconomyManager;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.station.api.model.OutputRouting;
import emaki.jiuwu.craft.station.api.model.PendingOutput;
import emaki.jiuwu.craft.station.gui.AmountDisplay;
import emaki.jiuwu.craft.station.gui.StationSlotType;
import emaki.jiuwu.craft.station.material.OutputDelivery;
import emaki.jiuwu.craft.station.recipe.RecipeCost;

public final class DismantleGuiInteractionController {

    private final DismantleService dismantleService;
    private final OutputDelivery outputDelivery;
    private final EconomyManager economyManager;
    private final MessageService messageService;

    public DismantleGuiInteractionController(DismantleService dismantleService,
            OutputDelivery outputDelivery,
            EconomyManager economyManager,
            MessageService messageService) {
        this.dismantleService = dismantleService;
        this.outputDelivery = outputDelivery;
        this.economyManager = economyManager;
        this.messageService = messageService;
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
        Player player = state.viewer();
        if (recipe.hasPermission() && !player.hasPermission(recipe.permission())) {
            messageService.send(player, "station.dismantle_no_permission");
            return;
        }
        if (!affordable(player, recipe.cost())) {
            RecipeCost cost = recipe.cost();
            double balance = economyManager == null ? 0.0D
                    : economyManager.getBalance(player, cost.providerId(), "");
            messageService.send(player, "station.dismantle_insufficient_currency", Map.of(
                    "amount", AmountDisplay.precise(cost.amount()),
                    "balance", AmountDisplay.precise((long) balance)));
            return;
        }
        ConsumedInput consumed = consumeInput(state, recipe);
        if (consumed == null) {

            state.selectedRecipe(null);
            redrawFn.run();
            return;
        }
        if (recipe.cost().charges() && !withdraw(player, recipe.cost())) {
            restoreInput(player, consumed);
            redrawFn.run();
            return;
        }
        List<DismantleOutput> outputs = dismantleService.roll(recipe);
        state.rolledOutputs(outputs);
        redrawFn.run();
    }

    private boolean affordable(Player player, RecipeCost cost) {
        if (!cost.charges()) {
            return true;
        }
        if (economyManager == null) {
            return false;
        }
        return economyManager.getBalance(player, cost.providerId(), "") >= (double) cost.amount();
    }

    private boolean withdraw(Player player, RecipeCost cost) {
        ActionResult removal = economyManager.remove(player, cost.providerId(), "", (double) cost.amount());
        if (removal != null && removal.success()) {
            return true;
        }
        messageService.send(player, "station.dismantle_charge_failed");
        return false;
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

    private ConsumedInput consumeInput(DismantleViewState state, DismantleRecipeDefinition recipe) {
        Player player = state.viewer();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (!dismantleService.accepts(recipe, item, player)) {
                continue;
            }
            ItemStack snapshot = item.clone();
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                inv.setItem(i, null);
            }
            return new ConsumedInput(i, snapshot);
        }
        return null;
    }

    private void restoreInput(Player player, ConsumedInput consumed) {
        player.getInventory().setItem(consumed.slot(), consumed.snapshot());
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

    private record ConsumedInput(int slot, ItemStack snapshot) {
    }
}
