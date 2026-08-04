package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.inventory.InventoryItemUtil;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeGuiStateSupport {

    record MaterialSlotRules(List<String> requiredIds,
            List<String> optionalIds,
            int optionalLimit) {

    }

    ForgeGuiStateSupport() {
    }

    public String resolveTemplateId(Recipe recipe) {
        return "forge_gui";
    }

    public void refreshDerivedValues(ForgeGuiSession state) {
        if (state == null) {
            return;
        }
        state.setPreviewRecipe(resolvePreviewRecipe(state));
        state.setMaxCapacity(resolveMaxCapacity(state));
        state.setCurrentCapacity(calculateCurrentCapacity(state));
        refreshPreviewRollState(state);
    }

    public Recipe resolvePreviewRecipe(ForgeGuiSession state) {
        if (state == null) {
            return null;
        }
        if (state.recipe() != null) {
            return state.recipe();
        }
        if (state.blueprintItems().isEmpty()
                && state.requiredMaterialItems().isEmpty()
                && state.optionalMaterialItems().isEmpty()
                && state.targetItem() == null) {
            return null;
        }
        ForgeService forgeService = state.runtimeSnapshot().forgeService();
        return forgeService == null ? null : forgeService.findMatchingRecipe(state.player(), state.toGuiItems()).recipe();
    }

    public int calculateCurrentCapacity(ForgeGuiSession state) {
        if (state == null) {
            return 0;
        }
        Recipe recipe = state.previewRecipe() != null ? state.previewRecipe() : state.recipe();
        if (recipe == null) {
            return 0;
        }
        return usagePlanner(state).optionalCapacityCost(recipe, state.toGuiItems());
    }

    public int resolveMaxCapacity(ForgeGuiSession state) {
        if (state == null) {
            return 0;
        }
        Recipe recipe = state.previewRecipe() != null ? state.previewRecipe() : state.recipe();
        int max = recipe == null ? 0 : Math.max(0, recipe.forgeCapacity());
        if (recipe == null) {
            List<Recipe> candidates = resolveCandidateRecipes(state);
            if (candidates.size() == 1 && candidates.get(0) != null) {
                max = Math.max(max, candidates.get(0).forgeCapacity());
            }
        }
        if (recipe != null) {
            max += usagePlanner(state).optionalCapacityBonus(recipe, state.toGuiItems());
        }
        return max;
    }

    public List<Integer> slotsForType(ForgeGuiSession state, String type) {
        if (state == null || state.guiSession() == null || Texts.isBlank(type)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        String normalized = Texts.lower(type);
        for (GuiSlot slot : state.guiSession().template().slots().values()) {
            if (slot == null) {
                continue;
            }
            if (normalized.equals(Texts.lower(slot.type())) || normalized.equals(Texts.lower(slot.key()))) {
                result.addAll(slot.slots());
            }
        }
        return result;
    }

    public void syncStateFromInventory(ForgeGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        Inventory inventory = state.guiSession().getInventory();
        state.blueprintItems().clear();
        for (int slot : slotsForType(state, "blueprint_inputs")) {
            ItemStack itemStack = cloneNonAir(inventory.getItem(slot));
            if (itemStack != null) {
                state.blueprintItems().put(slot, itemStack);
            }
        }
        state.requiredMaterialItems().clear();
        for (int slot : slotsForType(state, "required_materials")) {
            ItemStack itemStack = cloneNonAir(inventory.getItem(slot));
            if (itemStack != null) {
                state.requiredMaterialItems().put(slot, itemStack);
            }
        }
        state.optionalMaterialItems().clear();
        for (int slot : slotsForType(state, "optional_materials")) {
            ItemStack itemStack = cloneNonAir(inventory.getItem(slot));
            if (itemStack != null) {
                state.optionalMaterialItems().put(slot, itemStack);
            }
        }
        state.setTargetItem(null);
    }

    public MaterialSlotRules resolveMaterialSlotRules(ForgeGuiSession state) {
        List<String> requiredIds = new ArrayList<>();
        List<String> optionalIds = new ArrayList<>();
        int optionalLimit = 0;
        List<Recipe> candidateRecipes = new ArrayList<>(resolveCandidateRecipes(state));
        if (candidateRecipes.isEmpty() && state != null && state.runtimeSnapshot().recipeLoader() != null) {
            candidateRecipes.addAll(state.runtimeSnapshot().recipeLoader().all().values());
        }
        for (Recipe recipe : candidateRecipes) {
            if (recipe == null) {
                continue;
            }
            optionalLimit = Math.max(optionalLimit, recipe.optionalMaterialLimit());
            for (ForgeMaterial material : recipe.requiredMaterials()) {
                if (material != null && !requiredIds.contains(material.key())) {
                    requiredIds.add(material.key());
                }
            }
            for (ForgeMaterial material : recipe.optionalMaterials()) {
                if (material != null && !optionalIds.contains(material.key())) {
                    optionalIds.add(material.key());
                }
            }
        }
        return new MaterialSlotRules(requiredIds, optionalIds, optionalLimit);
    }

    public List<Recipe> resolveCandidateRecipes(ForgeGuiSession state) {
        if (state == null) {
            return List.of();
        }
        if (state.recipe() != null) {
            return List.of(state.recipe());
        }
        List<ItemStack> blueprints = new ArrayList<>(state.blueprintItems().values());
        if (blueprints.isEmpty()) {
            return List.of();
        }
        List<Recipe> result = new ArrayList<>();
        if (state.runtimeSnapshot().recipeLoader() == null) {
            return result;
        }
        for (Recipe recipe : state.runtimeSnapshot().recipeLoader().all().values()) {
            if (recipe != null && matchesBlueprintRequirements(state, recipe, blueprints)) {
                result.add(recipe);
            }
        }
        return result;
    }

    public int firstFreeSlot(List<Integer> slots, Map<Integer, ItemStack> occupied) {
        for (int slot : slots) {
            if (!occupied.containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    public boolean canPlaceOptionalMaterial(String materialId, MaterialSlotRules rules, int occupiedCount) {
        if (rules == null || Texts.isBlank(materialId)) {
            return false;
        }
        if (!rules.optionalIds().contains(materialId)) {
            return false;
        }
        return rules.optionalLimit() <= 0 || occupiedCount < rules.optionalLimit();
    }

    public BlueprintRequirement findBlueprintRequirementBySource(ForgeGuiSession state, ItemSourceRef source) {
        ForgeService forgeService = state == null ? null : state.runtimeSnapshot().forgeService();
        return forgeService == null ? null : forgeService.findBlueprintRequirementBySource(source);
    }

    public ForgeMaterial findMaterialBySource(ForgeGuiSession state, ItemSourceRef source) {
        ForgeService forgeService = state == null ? null : state.runtimeSnapshot().forgeService();
        return forgeService == null ? null : forgeService.findMaterialBySource(source);
    }

    public void returnItems(ForgeGuiSession state) {
        returnItems(state, null);
    }

    public void returnItems(ForgeGuiSession state, ItemStack additionalItem) {
        if (state == null) {
            return;
        }
        if (!state.returnPlanPrepared()) {
            List<ItemStack> returns = new ArrayList<>();
            returns.add(state.targetItem());
            returns.addAll(state.blueprintItems().values());
            returns.addAll(state.requiredMaterialItems().values());
            returns.addAll(state.optionalMaterialItems().values());
            returns.add(additionalItem);
            state.prepareReturnPlan(returns);
        }
        drainPendingReturns(state);
    }

    public void returnUnusedInputs(ForgeGuiSession state, Recipe recipe) {
        if (state == null || recipe == null) {
            return;
        }
        if (!state.returnPlanPrepared()) {
            state.prepareReturnPlan(usagePlanner(state).unconsumedInputs(recipe, state.toGuiItems()));
        }
        drainPendingReturns(state);
    }

    public void giveBackToPlayer(Player player, ItemStack itemStack) {
        ItemStack clone = cloneNonAir(itemStack);
        if (clone == null) {
            return;
        }
        if (player == null) {
            throw new IllegalStateException("Forge item return has no player owner.");
        }
        InventoryItemUtil.giveOrDrop(player, clone);
    }

    public String normalizedType(GuiSlot slot) {
        if (slot == null) {
            return "";
        }
        return Texts.isNotBlank(slot.type()) ? Texts.lower(slot.type()) : Texts.lower(slot.key());
    }

    static ItemStack cloneNonAir(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        return itemStack.clone();
    }

    ForgeMaterialUsagePlanner usagePlanner(ForgeGuiSession state) {
        ItemIdentifierService identifier = state == null || state.runtimeSnapshot() == null
                ? null
                : state.runtimeSnapshot().itemIdentifierService();
        return new ForgeMaterialUsagePlanner(identifier);
    }

    private boolean matchesBlueprintRequirements(ForgeGuiSession state, Recipe recipe, List<ItemStack> blueprints) {
        if (state == null || recipe == null) {
            return false;
        }
        if (recipe.blueprintRequirements().isEmpty()) {
            return true;
        }
        Map<String, Integer> available = blueprintAvailability(state, blueprints);
        for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if (available.getOrDefault(requirement.key(), 0) < requirement.amount()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Integer> blueprintAvailability(ForgeGuiSession state, List<ItemStack> blueprints) {
        Map<String, Integer> available = new LinkedHashMap<>();
        if (state == null || blueprints == null || state.runtimeSnapshot().itemIdentifierService() == null) {
            return available;
        }
        for (ItemStack itemStack : blueprints) {
            BlueprintRequirement requirement = findBlueprintRequirementBySource(
                    state,
                    state.runtimeSnapshot().itemIdentifierService().identifyItem(itemStack));
            if (requirement == null) {
                continue;
            }
            available.merge(requirement.key(), itemStack.getAmount(), Integer::sum);
        }
        return available;
    }

    private void drainPendingReturns(ForgeGuiSession state) {
        while (state != null && state.hasPendingReturns()) {
            Player player = state.player();
            ItemStack pending = cloneNonAir(state.pendingReturn());
            if (player == null || pending == null) {
                throw new IllegalStateException("Forge item return has no valid pending owner item.");
            }
            if (state.pendingReturnInventoryAttempted()) {
                player.getWorld().dropItemNaturally(player.getLocation(), pending);
                state.commitPendingReturn();
                continue;
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(pending);
            if (leftover.isEmpty()) {
                state.commitPendingReturn();
                continue;
            }
            state.replacePendingReturnWithDropRemainders(leftover.values());
        }
    }

    private void refreshPreviewRollState(ForgeGuiSession state) {
        Recipe previewRecipe = state.previewRecipe();
        if (previewRecipe == null) {
            state.setPreviewFingerprint("");
            state.setPreparedForge(null);
            state.refreshPreviewRoll();
            return;
        }
        ForgeService forgeService = state.runtimeSnapshot().forgeService();
        if (forgeService == null) {
            state.setPreparedForge(null);
            return;
        }
        String fingerprint = forgeService.buildPreviewFingerprint(state.player(), previewRecipe, state.toGuiItems());
        if (!fingerprint.equals(state.previewFingerprint())) {
            state.setPreviewFingerprint(fingerprint);
            state.setPreparedForge(null);
            state.refreshPreviewRoll();
        }
    }
}
