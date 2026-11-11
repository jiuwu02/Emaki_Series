package emaki.jiuwu.craft.forge.service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.corelib.execution.ThreadOwnership;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiCloseContext;
import emaki.jiuwu.craft.corelib.gui.GuiDragContext;
import emaki.jiuwu.craft.corelib.gui.GuiDebugSupport;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.api.event.ForgeCompletedEvent;
import emaki.jiuwu.craft.forge.api.event.ForgeStartEvent;
import emaki.jiuwu.craft.forge.model.ForgeResult;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;
import emaki.jiuwu.craft.forge.model.RecipeMatch;
import emaki.jiuwu.craft.corelib.debug.FailureRecorder;

final class ForgeGuiInteractionControllerDelegate {

    private final ForgeGuiInteractionController ownerRef;

    ForgeGuiInteractionControllerDelegate(ForgeGuiInteractionController ownerRef) {
        this.ownerRef = ownerRef;
    }

    void handleBlueprintClick(GuiClickContext click, ForgeGuiSession state, int slot) {
        ownerRef.handleMappedSlotClick(
                click,
                state,
                slot,
                "blueprint_inputs",
                state.blueprintItems(),
                itemStack -> ownerRef.stateSupport.acceptsBlueprint(
                        state,
                        itemStack,
                        state.runtimeSnapshot().itemIdentifierService().identifyItem(itemStack))
        );
    }

    void fireForgeCompleted(Player player, Recipe recipe, ForgeResult result, boolean success) {
        if (player == null || recipe == null || ownerRef.threadOwnership == null || !ownerRef.threadOwnership.isEntityOwned(player)) {
            return;
        }
        Bukkit.getPluginManager().callEvent(new ForgeCompletedEvent(
                player,
                recipe.id(),
                success,
                result == null ? null : result.resultItem(),
                result == null ? null : result.quality(),
                result == null ? 1D : result.multiplier()));
    }

    void handleShutdownClosureFailure(ForgeGuiSession state, String reason) {
        if (state == null) {
            return;
        }
        if (state.processing()) {
            ownerRef.recordSettlementFailure(state, reason + "; 被跟踪的处理完成仍保留结算所有权");
            return;
        }
        ownerRef.abandonRetiredSession(state, reason);
    }

    void abandonRetiredSession(ForgeGuiSession state, String reason) {
        if (state == null || state.settlementCommitted()) {
            return;
        }
        ownerRef.recordSettlementFailure(state, state.processing()
                ? reason + "; 处理完成仍持有未解决结算"
                : reason + "; 未解决物品仍为所有者线程重试保留");
    }

    void runTerminalSettlement(ForgeGuiSession state, Runnable settlement, String failureReason) {
        boolean committed = false;
        try {
            if (settlement != null) {
                settlement.run();
            }
            if (state == null || !state.commitSettlement()) {
                throw new IllegalStateException("无法提交锻造结算预留.");
            }
            committed = true;
        } catch (Throwable throwable) {
            if (state != null) {
                state.releaseSettlement();
            }
            try {
                ownerRef.recordSettlementFailure(state, failureReason + ": " + Texts.toStringSafe(throwable.getMessage()));
            } catch (Throwable ignored) {
                FailureRecorder.recordIgnoredFailure("EmakiForge", "ForgeGuiInteractionController.java#1", ignored);
            }
        } finally {
            if (state != null) {
                state.setProcessing(false);
                if (committed) {
                    ownerRef.stateManager.remove(state);
                }
            }
        }
    }

    void settleCommittedDelivery(ForgeGuiSession state, Recipe activeRecipe) {
        if (state == null) {
            return;
        }
        if (activeRecipe != null) {
            ownerRef.stateSupport.returnUnusedInputs(state, activeRecipe);
        }
        state.clearStoredItems();
    }

    void settleInputs(ForgeGuiSession state, Recipe activeRecipe, ForgeResult result) {
        if (result != null && result.success() && activeRecipe != null) {
            ownerRef.stateSupport.returnUnusedInputs(state, activeRecipe);
            state.clearStoredItems();
            return;
        }
        ownerRef.stateSupport.returnItems(state);
    }

    void returnFailedAttempt(ForgeGuiSession state, String errorKey, Map<String, ?> replacements) {
        try {
            state.runtimeSnapshot().messageService().send(
                    state.player(),
                    errorKey,
                    replacements == null ? Map.of() : replacements);
        } catch (Throwable throwable) {
            ownerRef.plugin.getLogger().warning("[gui] 锻造失败消息派发失败: "
                    + Texts.toStringSafe(throwable.getMessage()));
        }
        ownerRef.stateSupport.returnItems(state);
    }

    String materialKey(ForgeGuiSession state, ItemStack itemStack, ItemSourceRef source) {
        if (state == null) {
            return "";
        }
        return ownerRef.stateSupport.resolveMaterialKey(state, itemStack, source);
    }

    boolean ensureCurrentGeneration(ForgeGuiSession state) {
        if (state != null && state.shutdownRetiring()) {
            return false;
        }
        if (state != null && ownerRef.plugin.isGenerationActive(state.runtimeGeneration())) {
            return true;
        }
        ownerRef.plugin.runtimeMetrics().recordGuiStale();
        if (state != null && state.player() != null) {
            state.runtimeSnapshot().messageService().send(state.player(), "forge.error.runtime.stale_session");
            state.player().closeInventory();
        }
        return false;
    }

    void debug(Player player, String langKey, Map<String, ?> replacements) {
        try {
            GuiDebugSupport.log(ownerRef.plugin, player, langKey, replacements == null ? Map.of() : replacements);
        } catch (Throwable ignored) {
            FailureRecorder.recordIgnoredFailure("EmakiForge", "ForgeGuiInteractionController.java#2", ignored);
        }
    }

    Map<String, Object> replacements(Object... entries) {
        return GuiDebugSupport.replacements(entries);
    }

    String describe(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "air";
        }
        return itemStack.getType().getKey() + "x" + itemStack.getAmount();
    }

}
