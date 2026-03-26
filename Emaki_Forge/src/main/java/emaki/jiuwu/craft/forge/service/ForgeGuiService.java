package emaki.jiuwu.craft.forge.service;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ForgeGuiService {

    public static final class ForgeGuiSession {
        private final Player player;
        private final String templateId;
        private Recipe recipe;
        private Recipe previewRecipe;
        private GuiSession guiSession;
        private final Map<Integer, ItemStack> blueprintItems = new LinkedHashMap<>();
        private ItemStack targetItem;
        private final Map<Integer, ItemStack> requiredMaterialItems = new LinkedHashMap<>();
        private final Map<Integer, ItemStack> optionalMaterialItems = new LinkedHashMap<>();
        private int currentCapacity;
        private int maxCapacity;
        private boolean processing;
        private boolean forgeCompleted;

        public ForgeGuiSession(Player player, Recipe recipe, String templateId) {
            this.player = player;
            this.recipe = recipe;
            this.templateId = templateId;
        }

        public ForgeService.GuiItems toGuiItems() {
            return new ForgeService.GuiItems(
                targetItem == null ? null : targetItem.clone(),
                copyItems(blueprintItems),
                copyItems(requiredMaterialItems),
                copyItems(optionalMaterialItems)
            );
        }

        private static Map<Integer, ItemStack> copyItems(Map<Integer, ItemStack> source) {
            Map<Integer, ItemStack> result = new LinkedHashMap<>();
            for (Map.Entry<Integer, ItemStack> entry : source.entrySet()) {
                ItemStack itemStack = cloneNonAir(entry.getValue());
                if (itemStack != null) {
                    result.put(entry.getKey(), itemStack);
                }
            }
            return result;
        }
    }

    private record MaterialSlotRules(List<String> requiredIds,
                                     List<String> optionalWhitelist,
                                     List<String> optionalBlacklist,
                                     boolean optionalAny) {
    }

    private final EmakiForgePlugin plugin;
    private final GuiService guiService;
    private final Map<UUID, ForgeGuiSession> sessions = new LinkedHashMap<>();

    public ForgeGuiService(EmakiForgePlugin plugin, GuiService guiService) {
        this.plugin = plugin;
        this.guiService = guiService;
    }

    public boolean openForgeGui(Player player, Recipe recipe) {
        if (player == null) {
            return false;
        }
        String templateId = resolveTemplateId(recipe);
        GuiTemplate template = prepareTemplate(recipe, templateId);
        if (template == null) {
            return false;
        }
        ForgeGuiSession state = new ForgeGuiSession(player, recipe, templateId);
        refreshDerivedValues(state);
        GuiSession session = guiService.open(new GuiOpenRequest(
            plugin,
            player,
            template,
            titleReplacements(state),
            plugin.itemIdentifierService()::createItem,
            (guiSession, slot) -> renderSlot(state, slot),
            new ForgeSessionHandler(state)
        ));
        if (session == null) {
            return false;
        }
        state.guiSession = session;
        sessions.put(player.getUniqueId(), state);
        return true;
    }

    public boolean openGeneralForgeGui(Player player) {
        return openForgeGui(player, null);
    }

    public ForgeGuiSession getSession(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public void removeSession(Player player) {
        if (player != null) {
            sessions.remove(player.getUniqueId());
        }
    }

    public void clearAllSessions() {
        sessions.clear();
    }

    private String resolveTemplateId(Recipe recipe) {
        if (recipe == null || recipe.gui() == null || Texts.isBlank(recipe.gui().template())) {
            return "forge_gui";
        }
        return recipe.gui().template();
    }

    private GuiTemplate prepareTemplate(Recipe recipe, String templateId) {
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

    private ItemStack renderSlot(ForgeGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = normalizedType(slot);
        ItemStack dynamic = switch (type) {
            case "blueprint_inputs" -> cloneNonAir(state.blueprintItems.get(resolvedSlot.inventorySlot()));
            case "target_item" -> cloneNonAir(state.targetItem);
            case "required_materials" -> cloneNonAir(state.requiredMaterialItems.get(resolvedSlot.inventorySlot()));
            case "optional_materials" -> cloneNonAir(state.optionalMaterialItems.get(resolvedSlot.inventorySlot()));
            case "capacity_display" -> buildCapacityDisplayItem(slot, state);
            case "confirm" -> buildConfirmItem(slot, state);
            case "result_preview" -> buildResultPreview(slot, state);
            default -> null;
        };
        if (dynamic != null) {
            return dynamic;
        }
        return GuiItemBuilder.build(slot.item(), slot.components(), 1, slotReplacements(state), plugin.itemIdentifierService()::createItem);
    }

    private ItemStack buildCapacityDisplayItem(GuiSlot slot, ForgeGuiSession state) {
        return GuiItemBuilder.build(slot.item(), slot.components(), 1, slotReplacements(state), plugin.itemIdentifierService()::createItem);
    }

    private ItemStack buildConfirmItem(GuiSlot slot, ForgeGuiSession state) {
        if (state.maxCapacity > 0 && state.currentCapacity > state.maxCapacity) {
            return GuiItemBuilder.build(
                "BARRIER",
                new ItemComponentParser.ItemComponents(
                    "<red>无法锻造</red>",
                    true,
                    List.of(
                        "<gray>当前容量: <yellow>{current}/{max}</yellow></gray>",
                        "<red>可选材料容量已超出上限</red>",
                        "<gray>减少材料后再试一次</gray>"
                    ),
                    null,
                    null,
                    Map.of(),
                    List.of()
                ),
                1,
                slotReplacements(state),
                plugin.itemIdentifierService()::createItem
            );
        }
        return GuiItemBuilder.build(slot.item(), slot.components(), 1, slotReplacements(state), plugin.itemIdentifierService()::createItem);
    }

    private ItemStack buildResultPreview(GuiSlot slot, ForgeGuiSession state) {
        Recipe preview = state.previewRecipe;
        if (preview == null || preview.result() == null || preview.result().outputItem() == null) {
            return null;
        }
        ItemStack itemStack = plugin.itemIdentifierService().createItem(preview.result().outputItem(), 1);
        if (itemStack == null) {
            return null;
        }
        ItemStack clone = itemStack.clone();
        ItemMeta itemMeta = clone.getItemMeta();
        if (itemMeta != null) {
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            if (itemMeta.hasLore() && itemMeta.lore() != null) {
                lore.addAll(itemMeta.lore());
            }
            lore.add(MiniMessages.parse("<gray>预览配方: <gold>" + preview.displayName() + "</gold></gray>"));
            lore.add(MiniMessages.parse("<yellow>确认锻造后将生成该物品</yellow>"));
            itemMeta.lore(lore);
            clone.setItemMeta(itemMeta);
        }
        return clone;
    }

    private void refreshDerivedValues(ForgeGuiSession state) {
        state.maxCapacity = resolveMaxCapacity(state);
        state.currentCapacity = calculateCurrentCapacity(state);
        state.previewRecipe = resolvePreviewRecipe(state);
    }

    private Recipe resolvePreviewRecipe(ForgeGuiSession state) {
        if (state.recipe != null) {
            return state.recipe;
        }
        if (state.targetItem == null && state.blueprintItems.isEmpty() && state.requiredMaterialItems.isEmpty() && state.optionalMaterialItems.isEmpty()) {
            return null;
        }
        return plugin.forgeService().findMatchingRecipe(state.player, state.toGuiItems()).recipe();
    }

    private int calculateCurrentCapacity(ForgeGuiSession state) {
        int total = 0;
        for (ItemStack itemStack : state.optionalMaterialItems.values()) {
            ForgeMaterial material = findMaterialBySource(plugin.itemIdentifierService().identifyItem(itemStack));
            if (material != null) {
                total += material.capacityCost() * itemStack.getAmount();
            }
        }
        return total;
    }

    private int resolveMaxCapacity(ForgeGuiSession state) {
        if (state.recipe != null && state.recipe.forgeCapacity() > 0) {
            return state.recipe.forgeCapacity();
        }
        int max = 0;
        for (ItemStack itemStack : state.blueprintItems.values()) {
            Blueprint blueprint = findBlueprintBySource(plugin.itemIdentifierService().identifyItem(itemStack));
            if (blueprint != null) {
                max = Math.max(max, blueprint.forgeCapacity());
            }
        }
        return max;
    }

    private Map<String, Object> titleReplacements(ForgeGuiSession state) {
        return Map.of("recipe", state.recipe == null ? "通用锻造" : Texts.stripMiniTags(state.recipe.displayName()));
    }

    private Map<String, Object> slotReplacements(ForgeGuiSession state) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("recipe", state.recipe == null ? "通用锻造" : Texts.stripMiniTags(state.recipe.displayName()));
        replacements.put("current", state.currentCapacity);
        replacements.put("max", state.maxCapacity <= 0 ? "?" : state.maxCapacity);
        replacements.put("capacity_state", capacityStateText(state));
        return replacements;
    }

    private String capacityStateText(ForgeGuiSession state) {
        if (state.maxCapacity <= 0) {
            return "<gray>等待图纸</gray>";
        }
        if (state.currentCapacity > state.maxCapacity) {
            return "<red>已超限</red>";
        }
        if (state.currentCapacity >= Math.max(1, (int) Math.ceil(state.maxCapacity * 0.8D))) {
            return "<gold>接近上限</gold>";
        }
        return "<green>正常</green>";
    }

    private List<Integer> slotsForType(ForgeGuiSession state, String type) {
        if (state == null || state.guiSession == null || Texts.isBlank(type)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        String normalized = Texts.lower(type);
        for (GuiSlot slot : state.guiSession.template().slots().values()) {
            if (slot == null) {
                continue;
            }
            if (normalized.equals(Texts.lower(slot.type())) || normalized.equals(Texts.lower(slot.key()))) {
                result.addAll(slot.slots());
            }
        }
        return result;
    }

    private void refreshGui(ForgeGuiSession state) {
        if (state == null || state.guiSession == null) {
            return;
        }
        refreshDerivedValues(state);
        state.guiSession.refresh();
    }

    private void handleShiftFromPlayerInventory(InventoryClickEvent event, ForgeGuiSession state) {
        ItemStack itemStack = cloneNonAir(event.getCurrentItem());
        if (itemStack == null) {
            return;
        }
        ItemSource source = plugin.itemIdentifierService().identifyItem(itemStack);
        if (source == null) {
            return;
        }
        Blueprint blueprint = findBlueprintBySource(source);
        if (blueprint != null) {
            int slot = firstFreeSlot(slotsForType(state, "blueprint_inputs"), state.blueprintItems);
            if (slot >= 0) {
                state.blueprintItems.put(slot, itemStack);
                event.getClickedInventory().setItem(event.getSlot(), null);
                refreshGui(state);
            }
            return;
        }
        ForgeMaterial material = findMaterialBySource(source);
        if (material != null) {
            MaterialSlotRules rules = resolveMaterialSlotRules(state);
            if (rules.requiredIds().contains(material.id())) {
                int slot = firstFreeSlot(slotsForType(state, "required_materials"), state.requiredMaterialItems);
                if (slot >= 0) {
                    state.requiredMaterialItems.put(slot, itemStack);
                    event.getClickedInventory().setItem(event.getSlot(), null);
                    refreshGui(state);
                }
                return;
            }
            if (canPlaceOptionalMaterial(material.id(), rules)) {
                int slot = firstFreeSlot(slotsForType(state, "optional_materials"), state.optionalMaterialItems);
                if (slot >= 0) {
                    state.optionalMaterialItems.put(slot, itemStack);
                    event.getClickedInventory().setItem(event.getSlot(), null);
                    refreshGui(state);
                }
            }
            return;
        }
        if (!matchesTarget(state.recipe, itemStack)) {
            return;
        }
        List<Integer> targetSlots = slotsForType(state, "target_item");
        if (targetSlots.isEmpty()) {
            return;
        }
        if (state.targetItem != null) {
            giveBackToPlayer(state.player, state.targetItem);
        }
        state.targetItem = itemStack;
        event.getClickedInventory().setItem(event.getSlot(), null);
        refreshGui(state);
    }

    private void handleBlueprintClick(InventoryClickEvent event, ForgeGuiSession state, int slot) {
        handleMappedSlotClick(
            event,
            state,
            slot,
            state.blueprintItems,
            itemStack -> findBlueprintBySource(plugin.itemIdentifierService().identifyItem(itemStack)) != null
        );
    }

    private void handleTargetClick(InventoryClickEvent event, ForgeGuiSession state) {
        if (event.getClick().isKeyboardClick()) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = cloneNonAir(event.getCursor());
        if (cursor != null) {
            if (!matchesTarget(state.recipe, cursor)) {
                return;
            }
            ItemStack previous = cloneNonAir(state.targetItem);
            state.targetItem = cursor;
            player.setItemOnCursor(previous);
            refreshGui(state);
            return;
        }
        ItemStack removed = cloneNonAir(state.targetItem);
        if (removed == null) {
            return;
        }
        state.targetItem = null;
        if (event.isShiftClick()) {
            giveBackToPlayer(player, removed);
        } else {
            player.setItemOnCursor(removed);
        }
        refreshGui(state);
    }

    private void handleMaterialClick(InventoryClickEvent event, ForgeGuiSession state, int slot, boolean required) {
        MaterialSlotRules rules = resolveMaterialSlotRules(state);
        handleMappedSlotClick(
            event,
            state,
            slot,
            required ? state.requiredMaterialItems : state.optionalMaterialItems,
            itemStack -> {
                ForgeMaterial material = findMaterialBySource(plugin.itemIdentifierService().identifyItem(itemStack));
                if (material == null) {
                    return false;
                }
                return required ? rules.requiredIds().contains(material.id()) : canPlaceOptionalMaterial(material.id(), rules);
            }
        );
    }

    private void handleMappedSlotClick(InventoryClickEvent event,
                                       ForgeGuiSession state,
                                       int slot,
                                       Map<Integer, ItemStack> items,
                                       java.util.function.Predicate<ItemStack> validator) {
        if (event.getClick().isKeyboardClick()) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        ItemStack cursor = cloneNonAir(event.getCursor());
        if (cursor != null) {
            if (validator != null && !validator.test(cursor)) {
                return;
            }
            ItemStack previous = cloneNonAir(items.put(slot, cursor));
            player.setItemOnCursor(previous);
            refreshGui(state);
            return;
        }
        ItemStack removed = cloneNonAir(items.remove(slot));
        if (removed == null) {
            return;
        }
        if (event.isShiftClick()) {
            giveBackToPlayer(player, removed);
        } else {
            player.setItemOnCursor(removed);
        }
        refreshGui(state);
    }

    private void handleConfirmClick(ForgeGuiSession state) {
        if (state.processing) {
            return;
        }
        refreshDerivedValues(state);
        if (state.maxCapacity > 0 && state.currentCapacity > state.maxCapacity) {
            plugin.messageService().send(
                state.player,
                "forge.error.capacity_exceeded",
                Map.of("current", state.currentCapacity, "max", state.maxCapacity)
            );
            return;
        }
        Recipe activeRecipe = state.recipe != null ? state.recipe : state.previewRecipe;
        if (activeRecipe == null) {
            ForgeService.RecipeMatch match = plugin.forgeService().findMatchingRecipe(state.player, state.toGuiItems());
            if (match.recipe() == null) {
                plugin.messageService().send(state.player, match.errorKey(), match.replacements());
                return;
            }
            activeRecipe = match.recipe();
        }
        ForgeService.GuiItems snapshot = state.toGuiItems();
        boolean firstCraft = !plugin.playerDataStore().hasCrafted(state.player.getUniqueId(), activeRecipe.id());
        Recipe finalRecipe = activeRecipe;
        state.processing = true;
        state.recipe = finalRecipe;
        state.previewRecipe = finalRecipe;
        state.player.closeInventory();
        plugin.forgeService().executeForgeAsync(state.player, finalRecipe, snapshot)
            .whenComplete((result, throwable) -> plugin.getServer().getScheduler().runTask(
                plugin,
                () -> completeForgeAttempt(state, finalRecipe, firstCraft, result, throwable)
            ));
    }

    private void completeForgeAttempt(ForgeGuiSession state,
                                      Recipe activeRecipe,
                                      boolean firstCraft,
                                      ForgeService.ForgeResult result,
                                      Throwable throwable) {
        state.processing = false;
        sessions.remove(state.player.getUniqueId());
        if (throwable != null) {
            plugin.getLogger().warning("Forge execution failed for recipe '" + activeRecipe.id() + "': " + throwable.getMessage());
            returnFailedAttempt(state, "forge.error.action_failed", Map.of("reason", Texts.toStringSafe(throwable.getMessage())));
            return;
        }
        if (result == null || !result.success()) {
            String errorKey = result == null || Texts.isBlank(result.errorKey()) ? "forge.error.action_failed" : result.errorKey();
            Map<String, Object> replacements = result == null || result.replacements() == null ? Map.of() : result.replacements();
            returnFailedAttempt(state, errorKey, replacements);
            return;
        }
        state.forgeCompleted = true;
        clearStoredItems(state);
        if (Texts.isNotBlank(result.quality())) {
            plugin.messageService().send(
                state.player,
                "forge.success.quality",
                Map.of("quality", result.quality(), "multiplier", result.multiplier())
            );
        }
        if (firstCraft) {
            plugin.messageService().sendRaw(state.player, "<green>首次完成该配方锻造!</green>");
        }
    }

    private void returnFailedAttempt(ForgeGuiSession state, String errorKey, Map<String, ?> replacements) {
        plugin.messageService().send(state.player, errorKey, replacements == null ? Map.of() : replacements);
        returnItems(state);
    }

    private void clearStoredItems(ForgeGuiSession state) {
        state.blueprintItems.clear();
        state.requiredMaterialItems.clear();
        state.optionalMaterialItems.clear();
        state.targetItem = null;
    }

    private void syncStateFromInventory(ForgeGuiSession state) {
        if (state == null || state.guiSession == null) {
            return;
        }
        Inventory inventory = state.guiSession.getInventory();
        state.blueprintItems.clear();
        for (int slot : slotsForType(state, "blueprint_inputs")) {
            ItemStack itemStack = cloneNonAir(inventory.getItem(slot));
            if (itemStack != null) {
                state.blueprintItems.put(slot, itemStack);
            }
        }
        state.requiredMaterialItems.clear();
        for (int slot : slotsForType(state, "required_materials")) {
            ItemStack itemStack = cloneNonAir(inventory.getItem(slot));
            if (itemStack != null) {
                state.requiredMaterialItems.put(slot, itemStack);
            }
        }
        state.optionalMaterialItems.clear();
        for (int slot : slotsForType(state, "optional_materials")) {
            ItemStack itemStack = cloneNonAir(inventory.getItem(slot));
            if (itemStack != null) {
                state.optionalMaterialItems.put(slot, itemStack);
            }
        }
        List<Integer> targetSlots = slotsForType(state, "target_item");
        state.targetItem = targetSlots.isEmpty() ? null : cloneNonAir(inventory.getItem(targetSlots.get(0)));
    }

    private MaterialSlotRules resolveMaterialSlotRules(ForgeGuiSession state) {
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

    private List<Recipe> resolveCandidateRecipes(ForgeGuiSession state) {
        if (state.recipe != null) {
            return List.of(state.recipe);
        }
        if (state.blueprintItems.isEmpty()) {
            return List.of();
        }
        List<Recipe> result = new ArrayList<>();
        List<ItemStack> blueprints = new ArrayList<>(state.blueprintItems.values());
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

    private int firstFreeSlot(List<Integer> slots, Map<Integer, ItemStack> occupied) {
        for (int slot : slots) {
            if (!occupied.containsKey(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean canPlaceOptionalMaterial(String materialId, MaterialSlotRules rules) {
        if (rules.optionalBlacklist().contains(materialId)) {
            return false;
        }
        if (rules.optionalAny()) {
            return true;
        }
        return rules.optionalWhitelist().contains(materialId);
    }

    private Blueprint findBlueprintBySource(ItemSource source) {
        if (source == null) {
            return null;
        }
        for (Blueprint blueprint : plugin.blueprintLoader().all().values()) {
            if (ItemSourceUtil.matches(source, blueprint.source())) {
                return blueprint;
            }
        }
        return null;
    }

    private ForgeMaterial findMaterialBySource(ItemSource source) {
        if (source == null) {
            return null;
        }
        for (ForgeMaterial material : plugin.materialLoader().all().values()) {
            if (ItemSourceUtil.matches(source, material.source())) {
                return material;
            }
        }
        return null;
    }

    private boolean matchesTarget(Recipe recipe, ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        if (recipe == null) {
            for (Recipe candidate : plugin.recipeLoader().all().values()) {
                if (candidate.targetItemSource() != null && plugin.itemIdentifierService().matchesSource(itemStack, candidate.targetItemSource())) {
                    return true;
                }
            }
            return false;
        }
        return recipe.targetItemSource() != null && plugin.itemIdentifierService().matchesSource(itemStack, recipe.targetItemSource());
    }

    private void returnItems(ForgeGuiSession state) {
        returnItemCollection(state.player, state.blueprintItems.values());
        returnItemCollection(state.player, state.requiredMaterialItems.values());
        returnItemCollection(state.player, state.optionalMaterialItems.values());
        if (state.targetItem != null) {
            returnItemCollection(state.player, List.of(state.targetItem));
        }
        state.blueprintItems.clear();
        state.requiredMaterialItems.clear();
        state.optionalMaterialItems.clear();
        state.targetItem = null;
    }

    private void returnItemCollection(Player player, Iterable<ItemStack> items) {
        for (ItemStack item : items) {
            giveBackToPlayer(player, item);
        }
    }

    private void giveBackToPlayer(Player player, ItemStack itemStack) {
        ItemStack clone = cloneNonAir(itemStack);
        if (player == null || clone == null) {
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(clone);
        leftover.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private String normalizedType(GuiSlot slot) {
        if (slot == null) {
            return "";
        }
        return Texts.isNotBlank(slot.type()) ? Texts.lower(slot.type()) : Texts.lower(slot.key());
    }

    private static ItemStack cloneNonAir(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return null;
        }
        return itemStack.clone();
    }

    private final class ForgeSessionHandler implements GuiSessionHandler {

        private final ForgeGuiSession state;

        private ForgeSessionHandler(ForgeGuiSession state) {
            this.state = state;
        }

        @Override
        public void onSlotClick(GuiSession session, InventoryClickEvent event, GuiTemplate.ResolvedSlot slot) {
            if (state.processing) {
                event.setCancelled(true);
                return;
            }
            if (slot == null || slot.definition() == null) {
                return;
            }
            switch (normalizedType(slot.definition())) {
                case "blueprint_inputs" -> handleBlueprintClick(event, state, slot.inventorySlot());
                case "target_item" -> handleTargetClick(event, state);
                case "required_materials" -> handleMaterialClick(event, state, slot.inventorySlot(), true);
                case "optional_materials" -> handleMaterialClick(event, state, slot.inventorySlot(), false);
                case "confirm" -> handleConfirmClick(state);
                default -> {
                }
            }
        }

        @Override
        public void onPlayerInventoryClick(GuiSession session, InventoryClickEvent event) {
            if (state.processing) {
                event.setCancelled(true);
                return;
            }
            if (!event.isShiftClick()) {
                return;
            }
            event.setCancelled(true);
            handleShiftFromPlayerInventory(event, state);
        }

        @Override
        public void onClose(GuiSession session, InventoryCloseEvent event) {
            if (state.processing) {
                return;
            }
            sessions.remove(state.player.getUniqueId());
            if (!state.forgeCompleted) {
                returnItems(state);
            }
        }
    }
}
