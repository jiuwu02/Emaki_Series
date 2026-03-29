package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class ForgeGuiStateSupport {

    record MaterialSlotRules(List<String> requiredIds,
                             List<String> optionalWhitelist,
                             List<String> optionalBlacklist,
                             boolean optionalAny) {
    }

    private final EmakiForgePlugin plugin;

    ForgeGuiStateSupport(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    public String resolveTemplateId(Recipe recipe) {
        if (recipe == null || recipe.gui() == null || Texts.isBlank(recipe.gui().template())) {
            return "forge_gui";
        }
        return recipe.gui().template();
    }

    public GuiTemplate prepareTemplate(Recipe recipe, String templateId) {
        GuiTemplate template = plugin.guiTemplateLoader().get(templateId);
        if (template == null || recipe == null || recipe.gui() == null || recipe.gui().slots().isEmpty()) {
            return template;
        }
        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        for (Map.Entry<String, GuiSlot> entry : template.slots().entrySet()) {
            GuiSlot slot = entry.getValue();
            List<Integer> override = recipe.gui().slots().get(entry.getKey());
            if ((override == null || override.isEmpty()) && Texts.isNotBlank(slot.type())) {
                override = recipe.gui().slots().get(slot.type());
            }
            if (override != null && !override.isEmpty()) {
                slot = new GuiSlot(slot.key(), override, slot.type(), slot.item(), slot.components(), slot.sounds());
            }
            slots.put(entry.getKey(), slot);
        }
        return new GuiTemplate(template.id(), template.title(), template.rows(), slots);
    }

    public void refreshDerivedValues(ForgeGuiSession state) {
        if (state == null) {
            return;
        }
        state.setMaxCapacity(resolveMaxCapacity(state));
        state.setCurrentCapacity(calculateCurrentCapacity(state));
        state.setPreviewRecipe(resolvePreviewRecipe(state));
        refreshPreviewRollState(state);
    }

    public Recipe resolvePreviewRecipe(ForgeGuiSession state) {
        if (state == null) {
            return null;
        }
        if (state.recipe() != null) {
            return state.recipe();
        }
        if (state.targetItem() == null
            && state.blueprintItems().isEmpty()
            && state.requiredMaterialItems().isEmpty()
            && state.optionalMaterialItems().isEmpty()) {
            return null;
        }
        return plugin.forgeService().findMatchingRecipe(state.player(), state.toGuiItems()).recipe();
    }

    public int calculateCurrentCapacity(ForgeGuiSession state) {
        if (state == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack itemStack : state.optionalMaterialItems().values()) {
            ForgeMaterial material = findMaterialBySource(plugin.itemIdentifierService().identifyItem(itemStack));
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
        int max = state.recipe() != null && state.recipe().forgeCapacity() > 0 ? state.recipe().forgeCapacity() : 0;
        for (ItemStack itemStack : state.blueprintItems().values()) {
            Blueprint blueprint = findBlueprintBySource(plugin.itemIdentifierService().identifyItem(itemStack));
            if (blueprint != null) {
                max = Math.max(max, blueprint.forgeCapacity());
            }
        }
        for (ItemStack itemStack : state.optionalMaterialItems().values()) {
            ForgeMaterial material = findMaterialBySource(plugin.itemIdentifierService().identifyItem(itemStack));
            if (material != null) {
                max += material.forgeCapacityBonus() * itemStack.getAmount();
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
        List<Integer> targetSlots = slotsForType(state, "target_item");
        state.setTargetItem(targetSlots.isEmpty() ? null : cloneNonAir(inventory.getItem(targetSlots.get(0))));
    }

    public MaterialSlotRules resolveMaterialSlotRules(ForgeGuiSession state) {
        List<String> requiredIds = new ArrayList<>();
        List<String> optionalWhitelist = new ArrayList<>();
        List<String> optionalBlacklist = new ArrayList<>();
        boolean optionalAny = false;
        List<Recipe> candidateRecipes = resolveCandidateRecipes(state);
        if (candidateRecipes.isEmpty()) {
            candidateRecipes.addAll(plugin.recipeLoader().all().values());
            optionalAny = true;
        }
        for (Recipe recipe : candidateRecipes) {
            for (Recipe.RequiredMaterial requiredMaterial : recipe.requiredMaterials()) {
                if (!requiredIds.contains(requiredMaterial.id())) {
                    requiredIds.add(requiredMaterial.id());
                }
            }
            Recipe.OptionalMaterialsConfig optional = recipe.optionalMaterials();
            if (optional.enabled()) {
                if (optional.whitelist().isEmpty()) {
                    optionalAny = true;
                } else {
                    for (String entry : optional.whitelist()) {
                        if (!optionalWhitelist.contains(entry)) {
                            optionalWhitelist.add(entry);
                        }
                    }
                }
                for (String entry : optional.blacklist()) {
                    if (!optionalBlacklist.contains(entry)) {
                        optionalBlacklist.add(entry);
                    }
                }
            }
        }
        return new MaterialSlotRules(requiredIds, optionalWhitelist, optionalBlacklist, optionalAny);
    }

    public List<Recipe> resolveCandidateRecipes(ForgeGuiSession state) {
        if (state == null) {
            return List.of();
        }
        if (state.recipe() != null) {
            return List.of(state.recipe());
        }
        if (state.blueprintItems().isEmpty()) {
            return List.of();
        }
        List<Recipe> result = new ArrayList<>();
        List<ItemStack> blueprints = new ArrayList<>(state.blueprintItems().values());
        for (Recipe recipe : plugin.recipeLoader().all().values()) {
            boolean matches = true;
            for (Recipe.BlueprintRequirement requirement : recipe.blueprintRequirements()) {
                if ("all_of".equals(Texts.lower(requirement.requirementMode()))) {
                    for (Recipe.BlueprintOption option : requirement.blueprintOptions()) {
                        if (countBlueprints(blueprints, option.selector()) < option.count()) {
                            matches = false;
                            break;
                        }
                    }
                }
                if (!matches) {
                    break;
                }
            }
            if (matches) {
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

    public boolean canPlaceOptionalMaterial(String materialId, MaterialSlotRules rules) {
        if (rules == null || Texts.isBlank(materialId)) {
            return false;
        }
        if (rules.optionalBlacklist().contains(materialId)) {
            return false;
        }
        if (rules.optionalAny()) {
            return true;
        }
        return rules.optionalWhitelist().contains(materialId);
    }

    public Blueprint findBlueprintBySource(ItemSource source) {
        return plugin.forgeService().findBlueprintBySource(source);
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
        if (state.targetItem() != null) {
            returnItemCollection(state.player(), List.of(state.targetItem()));
        }
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

    private int countBlueprints(List<ItemStack> blueprints, Map<String, Object> selector) {
        int total = 0;
        for (ItemStack itemStack : blueprints) {
            Blueprint blueprint = findBlueprintBySource(plugin.itemIdentifierService().identifyItem(itemStack));
            if (blueprint != null && blueprint.matchesSelector(selector)) {
                total += itemStack.getAmount();
            }
        }
        return total;
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
