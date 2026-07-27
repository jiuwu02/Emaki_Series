package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.forge.ForgeRuntimeSnapshot;
import emaki.jiuwu.craft.forge.model.GuiItems;
import emaki.jiuwu.craft.forge.model.Recipe;

public final class ForgeGuiSession {

    private enum SettlementState {
        NONE,
        INPUT_SETTLING,
        RESULT_DELIVERY_RESERVED,
        RESULT_SETTLING,
        SETTLED
    }

    private record PendingReturn(ItemStack itemStack, boolean inventoryAttempted) {
    }

    private final Player player;
    private final UUID playerId;
    private final String templateId;
    private final ForgeRuntimeSnapshot runtimeSnapshot;
    private final long runtimeGeneration;
    private Recipe recipe;
    private Recipe previewRecipe;
    private GuiSession guiSession;
    private final Map<Integer, ItemStack> blueprintItems = new LinkedHashMap<>();
    private ItemStack targetItem;
    private final Map<Integer, ItemStack> requiredMaterialItems = new LinkedHashMap<>();
    private final Map<Integer, ItemStack> optionalMaterialItems = new LinkedHashMap<>();
    private int currentCapacity;
    private int maxCapacity;
    private final AtomicReference<SettlementState> settlementState = new AtomicReference<>(SettlementState.NONE);
    private final AtomicBoolean resultCommitted = new AtomicBoolean();
    private final List<PendingReturn> pendingReturns = new ArrayList<>();
    private boolean returnPlanPrepared;
    private volatile boolean processing;
    private volatile boolean forgeCompleted;
    private volatile boolean shutdownRetiring;
    private String previewFingerprint = "";
    private long previewSeed = ThreadLocalRandom.current().nextLong();
    private long previewForgedAt = System.currentTimeMillis();
    private ForgeService.PreparedForge preparedForge;

    public ForgeGuiSession(Player player, Recipe recipe, String templateId, ForgeRuntimeSnapshot runtimeSnapshot) {
        this.player = player;
        this.playerId = player == null ? null : player.getUniqueId();
        this.recipe = recipe;
        this.templateId = templateId;
        this.runtimeSnapshot = runtimeSnapshot;
        this.runtimeGeneration = runtimeSnapshot == null ? 0L : runtimeSnapshot.generation();
    }

    public Player player() {
        return player;
    }

    public UUID playerId() {
        return playerId;
    }

    public String templateId() {
        return templateId;
    }

    public ForgeRuntimeSnapshot runtimeSnapshot() {
        return runtimeSnapshot;
    }

    public long runtimeGeneration() {
        return runtimeGeneration;
    }

    public Recipe recipe() {
        return recipe;
    }

    public void setRecipe(Recipe recipe) {
        this.recipe = recipe;
    }

    public Recipe previewRecipe() {
        return previewRecipe;
    }

    public void setPreviewRecipe(Recipe previewRecipe) {
        this.previewRecipe = previewRecipe;
    }

    public GuiSession guiSession() {
        return guiSession;
    }

    public void setGuiSession(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    public Map<Integer, ItemStack> blueprintItems() {
        return blueprintItems;
    }

    public ItemStack targetItem() {
        return targetItem;
    }

    public void setTargetItem(ItemStack targetItem) {
        this.targetItem = targetItem;
    }

    public Map<Integer, ItemStack> requiredMaterialItems() {
        return requiredMaterialItems;
    }

    public Map<Integer, ItemStack> optionalMaterialItems() {
        return optionalMaterialItems;
    }

    public int currentCapacity() {
        return currentCapacity;
    }

    public void setCurrentCapacity(int currentCapacity) {
        this.currentCapacity = currentCapacity;
    }

    public int maxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public boolean processing() {
        return processing;
    }

    public void setProcessing(boolean processing) {
        this.processing = processing;
    }

    public boolean forgeCompleted() {
        return forgeCompleted;
    }

    public void setForgeCompleted(boolean forgeCompleted) {
        this.forgeCompleted = forgeCompleted;
    }

    public boolean claimSettlement() {
        return settlementState.compareAndSet(SettlementState.NONE, SettlementState.INPUT_SETTLING);
    }

    public boolean claimResultDelivery() {
        return settlementState.compareAndSet(SettlementState.NONE, SettlementState.RESULT_DELIVERY_RESERVED);
    }

    public void releaseResultDelivery() {
        if (!resultCommitted.get()) {
            settlementState.compareAndSet(SettlementState.RESULT_DELIVERY_RESERVED, SettlementState.NONE);
        }
    }

    public boolean claimResultDeliverySettlement() {
        return resultCommitted.get()
                && settlementState.compareAndSet(
                        SettlementState.RESULT_DELIVERY_RESERVED,
                        SettlementState.RESULT_SETTLING);
    }

    public boolean commitSettlement() {
        while (true) {
            SettlementState current = settlementState.get();
            if (current == SettlementState.SETTLED) {
                return true;
            }
            if (current != SettlementState.INPUT_SETTLING && current != SettlementState.RESULT_SETTLING) {
                return false;
            }
            if (settlementState.compareAndSet(current, SettlementState.SETTLED)) {
                return true;
            }
        }
    }

    public void releaseSettlement() {
        settlementState.compareAndSet(SettlementState.INPUT_SETTLING, SettlementState.NONE);
        settlementState.compareAndSet(SettlementState.RESULT_SETTLING, SettlementState.RESULT_DELIVERY_RESERVED);
    }

    public boolean settlementClaimed() {
        return settlementState.get() != SettlementState.NONE;
    }

    public boolean settlementCommitted() {
        return settlementState.get() == SettlementState.SETTLED;
    }

    public boolean resultDeliveryClaimed() {
        SettlementState current = settlementState.get();
        return current == SettlementState.RESULT_DELIVERY_RESERVED
                || current == SettlementState.RESULT_SETTLING
                || (current == SettlementState.SETTLED && resultCommitted.get());
    }

    public void markResultCommitted() {
        resultCommitted.set(true);
    }

    public boolean resultCommitted() {
        return resultCommitted.get();
    }

    public boolean shutdownRetiring() {
        return shutdownRetiring;
    }

    public void markShutdownRetiring() {
        shutdownRetiring = true;
    }

    public String previewFingerprint() {
        return previewFingerprint;
    }

    public void setPreviewFingerprint(String previewFingerprint) {
        this.previewFingerprint = previewFingerprint == null ? "" : previewFingerprint;
    }

    public long previewSeed() {
        return previewSeed;
    }

    public long previewForgedAt() {
        return previewForgedAt;
    }

    public void refreshPreviewRoll() {
        this.previewSeed = ThreadLocalRandom.current().nextLong();
        this.previewForgedAt = System.currentTimeMillis();
    }

    public ForgeService.PreparedForge preparedForge() {
        return preparedForge;
    }

    public void setPreparedForge(ForgeService.PreparedForge preparedForge) {
        this.preparedForge = preparedForge;
    }

    public void clearStoredItems() {
        blueprintItems.clear();
        requiredMaterialItems.clear();
        optionalMaterialItems.clear();
        targetItem = null;
        preparedForge = null;
    }

    public void prepareReturnPlan(Iterable<ItemStack> items) {
        if (returnPlanPrepared) {
            return;
        }
        if (items != null) {
            for (ItemStack itemStack : items) {
                ItemStack clone = ForgeGuiStateSupport.cloneNonAir(itemStack);
                if (clone != null) {
                    pendingReturns.add(new PendingReturn(clone, false));
                }
            }
        }
        returnPlanPrepared = true;
        clearStoredItems();
    }

    public ItemStack pendingReturn() {
        return pendingReturns.isEmpty() ? null : pendingReturns.getFirst().itemStack();
    }

    public boolean pendingReturnInventoryAttempted() {
        return !pendingReturns.isEmpty() && pendingReturns.getFirst().inventoryAttempted();
    }

    public void replacePendingReturnWithDropRemainders(Iterable<ItemStack> items) {
        if (!pendingReturns.isEmpty()) {
            pendingReturns.removeFirst();
        }
        int index = 0;
        if (items != null) {
            for (ItemStack itemStack : items) {
                ItemStack clone = ForgeGuiStateSupport.cloneNonAir(itemStack);
                if (clone != null) {
                    pendingReturns.add(index++, new PendingReturn(clone, true));
                }
            }
        }
    }

    public void commitPendingReturn() {
        if (!pendingReturns.isEmpty()) {
            pendingReturns.removeFirst();
        }
    }

    public boolean returnPlanPrepared() {
        return returnPlanPrepared;
    }

    public boolean hasPendingReturns() {
        return !pendingReturns.isEmpty();
    }

    public GuiItems toGuiItems() {
        return new GuiItems(
                targetItem == null ? null : targetItem.clone(),
                copyItems(blueprintItems),
                copyItems(requiredMaterialItems),
                copyItems(optionalMaterialItems)
        );
    }

    private static Map<Integer, ItemStack> copyItems(Map<Integer, ItemStack> source) {
        Map<Integer, ItemStack> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : source.entrySet()) {
            ItemStack itemStack = ForgeGuiStateSupport.cloneNonAir(entry.getValue());
            if (itemStack != null) {
                result.put(entry.getKey(), itemStack);
            }
        }
        return result;
    }
}
