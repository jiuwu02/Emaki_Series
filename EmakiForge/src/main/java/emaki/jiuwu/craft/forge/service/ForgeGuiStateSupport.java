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
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.BlueprintRequirement;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeGuiStateSupport {

    record MaterialSlotRules(List<String> requiredIds,
            List<String> optionalIds,
            int optionalLimit) {

    }

    private final EmakiForgePlugin plugin;

    ForgeGuiStateSupport(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    public String resolveTemplateId(Recipe recipe) {
        return "forge_gui";
    }

    public GuiTemplate prepareTemplate(Recipe recipe, String templateId) {
        return plugin.guiTemplateLoader().get(Texts.isBlank(templateId) ? "forge_gui" : templateId);
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
        return plugin.forgeService().findMatchingRecipe(state.player(), state.toGuiItems()).recipe();
    }

    public int calculateCurrentCapacity(ForgeGuiSession state) {
        if (state == null) {
            return 0;
        }
        Recipe recipe = state.previewRecipe() != null ? state.previewRecipe() : state.recipe();
        if (recipe == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack itemStack : state.optionalMaterialItems().values()) {
            ForgeMaterial material = resolveMaterial(recipe, itemStack, true);
            if (material != null) {
                total += material.effectiveCapacityCost() * itemStack.getAmount();
            }
        }
        return total;
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
            for (ItemStack itemStack : state.optionalMaterialItems().values()) {
                ForgeMaterial material = resolveMaterial(recipe, itemStack, true);
                if (material != null) {
                    max += material.forgeCapacityBonus() * itemStack.getAmount();
                }
            }
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
        if (candidateRecipes.isEmpty()) {
            candidateRecipes.addAll(plugin.recipeLoader().all().values());
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
        for (Recipe recipe : plugin.recipeLoader().all().values()) {
            if (recipe != null && matchesBlueprintRequirements(recipe, blueprints)) {
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

    public BlueprintRequirement findBlueprintRequirementBySource(ItemSource source) {
        return plugin.forgeService().findBlueprintRequirementBySource(source);
    }

    public ForgeMaterial findMaterialBySource(ItemSource source) {
        return plugin.forgeService().findMaterialBySource(source);
    }

    public boolean matchesTarget(Recipe recipe, ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        if (recipe == null) {
            for (Recipe candidate : plugin.forgeService().sortedRecipes()) {
                if (candidate.targetItemSource() != null && plugin.itemIdentifierService().matchesSource(itemStack, candidate.targetItemSource())) {
                    return true;
                }
            }
            return false;
        }
        return recipe.targetItemSource() != null && plugin.itemIdentifierService().matchesSource(itemStack, recipe.targetItemSource());
    }

    public void returnItems(ForgeGuiSession state) {
        if (state == null) {
            return;
        }
        returnItemCollection(state.player(), state.blueprintItems().values());
        returnItemCollection(state.player(), state.requiredMaterialItems().values());
        returnItemCollection(state.player(), state.optionalMaterialItems().values());
        state.clearStoredItems();
    }

    public void giveBackToPlayer(Player player, ItemStack itemStack) {
        ItemStack clone = cloneNonAir(itemStack);
        if (player == null || clone == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(clone);
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
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

    private boolean matchesBlueprintRequirements(Recipe recipe, List<ItemStack> blueprints) {
        if (recipe == null) {
            return false;
        }
        if (recipe.blueprintRequirements().isEmpty()) {
            return true;
        }
        Map<String, Integer> available = blueprintAvailability(blueprints);
        for (BlueprintRequirement requirement : recipe.blueprintRequirements()) {
            if (available.getOrDefault(requirement.key(), 0) < requirement.amount()) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Integer> blueprintAvailability(List<ItemStack> blueprints) {
        Map<String, Integer> available = new LinkedHashMap<>();
        if (blueprints == null) {
            return available;
        }
        for (ItemStack itemStack : blueprints) {
            BlueprintRequirement requirement = findBlueprintRequirementBySource(plugin.itemIdentifierService().identifyItem(itemStack));
            if (requirement == null) {
                continue;
            }
            available.merge(requirement.key(), itemStack.getAmount(), Integer::sum);
        }
        return available;
    }

    private ForgeMaterial resolveMaterial(Recipe recipe, ItemStack itemStack, boolean optional) {
        if (recipe == null || itemStack == null) {
            return null;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        return recipe.findMaterialBySource(source, optional);
    }

    private void returnItemCollection(Player player, Iterable<ItemStack> items) {
        for (ItemStack item : items) {
            giveBackToPlayer(player, item);
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
        String fingerprint = plugin.forgeService().buildPreviewFingerprint(state.player(), previewRecipe, state.toGuiItems());
        if (!fingerprint.equals(state.previewFingerprint())) {
            state.setPreviewFingerprint(fingerprint);
            state.setPreparedForge(null);
            state.refreshPreviewRoll();
        }
    }
}
