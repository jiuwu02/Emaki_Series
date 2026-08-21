package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;

final class ForgeGuiRenderer {

    private static final int MAX_RECIPE_GROUPS = 3;

    private final EmakiForgePlugin plugin;
    private final ForgeGuiStateSupport stateSupport;
    private final ConfiguredGuiSupport guiSupport;

    ForgeGuiRenderer(EmakiForgePlugin plugin, ForgeGuiStateSupport stateSupport) {
        this.plugin = plugin;
        this.stateSupport = stateSupport;
        this.guiSupport = new ConfiguredGuiSupport(plugin);
    }

    public ItemStack renderSlot(ForgeGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = stateSupport.normalizedType(slot);
        ItemStack dynamic = switch (type) {
            case "blueprint_inputs" ->
                ForgeGuiStateSupport.cloneNonAir(state.blueprintItems().get(resolvedSlot.inventorySlot()));
            case "required_materials" ->
                ForgeGuiStateSupport.cloneNonAir(state.requiredMaterialItems().get(resolvedSlot.inventorySlot()));
            case "optional_materials" ->
                ForgeGuiStateSupport.cloneNonAir(state.optionalMaterialItems().get(resolvedSlot.inventorySlot()));
            case "capacity_display" ->
                buildCapacityDisplayItem(slot, state);
            case "confirm" ->
                buildConfirmItem(slot, state);
            default ->
                null;
        };
        if (dynamic != null) {
            return dynamic;
        }
        return GuiItemBuilder.build(slot.itemDefinition(), slotReplacements(state),
                plugin.coreLib().configuredItemService());
    }

    public Map<String, Object> titleReplacements(ForgeGuiSession state) {
        return Map.of("recipe", state.recipe() == null ? "通用锻造" : Texts.stripMiniTags(state.recipe().displayName()));
    }

    public void refreshGui(ForgeGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        stateSupport.refreshDerivedValues(state);
        state.guiSession().refresh();
    }

    private ItemStack buildCapacityDisplayItem(GuiSlot slot, ForgeGuiSession state) {
        Map<String, Object> replacements = slotReplacements(state);
        List<Recipe> candidates = requirementRecipes(state);
        replacements.put("recipe_count", candidates.size());
        replacements.put("blueprint_requirements", blueprintRequirementLines(state, candidates));
        return GuiItemBuilder.build(slot.itemDefinition(), replacements,
                plugin.coreLib().configuredItemService());
    }

    private ItemStack buildConfirmItem(GuiSlot slot, ForgeGuiSession state) {
        if (state.maxCapacity() > 0 && state.currentCapacity() > state.maxCapacity()) {
            return guiSupport.build(
                    state.runtimeSnapshot(),
                    "forge_gui",
                    "virtual_items.confirm_blocked",
                    slotReplacements(state),
                    "BARRIER",
                    new ConfiguredItemDefinition("BARRIER", 1, Map.of(
                            "minecraft:custom_name", ItemComponentPatch.set("<red>无法锻造</red>"),
                            "minecraft:lore", ItemComponentPatch.set(List.of(
                                    "<gray>当前容量: <yellow>%current%/%max%</yellow></gray>",
                                    "<red>可选材料容量已超出上限</red>",
                                    "<gray>减少材料后再试一次</gray>"
                            ))
                    ))
            );
        }
        return GuiItemBuilder.build(slot.itemDefinition(), slotReplacements(state),
                plugin.coreLib().configuredItemService());
    }

    private Map<String, Object> slotReplacements(ForgeGuiSession state) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put("recipe", state.recipe() == null ? "通用锻造" : Texts.stripMiniTags(state.recipe().displayName()));
        replacements.put("current", state.currentCapacity());
        replacements.put("max", state.maxCapacity() <= 0 ? "?" : state.maxCapacity());
        replacements.put("capacity_state", capacityStateText(state));
        return replacements;
    }

    private List<Recipe> requirementRecipes(ForgeGuiSession state) {
        Recipe resolved = state.previewRecipe() != null ? state.previewRecipe() : state.recipe();
        if (resolved != null) {
            return List.of(resolved);
        }
        return stateSupport.resolveCandidateRecipes(state);
    }

    private List<String> blueprintRequirementLines(ForgeGuiSession state, List<Recipe> candidates) {
        List<String> lines = new ArrayList<>();
        if (candidates.isEmpty()) {
            lines.add(text(state, "gui.blueprint.no_recipe"));
            return lines;
        }
        boolean grouped = candidates.size() > 1;
        int shown = Math.min(candidates.size(), MAX_RECIPE_GROUPS);
        for (int index = 0; index < shown; index++) {
            Recipe recipe = candidates.get(index);
            if (recipe == null) {
                continue;
            }
            if (grouped) {
                lines.add(text(state, "gui.blueprint.recipe_group_header", Map.of(
                        "recipe", Texts.stripMiniTags(recipe.displayName())
                )));
            }
            appendRequirementLines(lines, state, recipe);
        }
        int hidden = candidates.size() - shown;
        if (hidden > 0) {
            lines.add(text(state, "gui.blueprint.more_recipes", Map.of("count", hidden)));
        }
        return lines;
    }

    private void appendRequirementLines(List<String> lines, ForgeGuiSession state, Recipe recipe) {
        Map<String, Integer> placedAmounts = stateSupport.usagePlanner(state)
                .placedAmounts(state.player(), recipe, state.toGuiItems());
        for (ForgeMaterial material : recipe.requiredMaterials()) {
            if (material == null) {
                continue;
            }
            int required = Math.max(1, material.amount());
            int placed = placedAmounts.getOrDefault(material.key(), 0);
            lines.add(text(state, placed >= required
                    ? "gui.blueprint.requirement_satisfied"
                    : "gui.blueprint.requirement_missing", Map.of(
                    "material", materialDisplayName(material),
                    "placed", placed,
                    "required", required
            )));
        }
    }

    private String materialDisplayName(ForgeMaterial material) {
        String item = material == null ? "" : material.item();
        if (Texts.isBlank(item)) {
            return material == null ? "" : material.key();
        }
        String displayName = EmakiCoreLibApi.itemDisplayName(item).orElse("");
        return Texts.isBlank(displayName) ? item : displayName;
    }

    private String text(ForgeGuiSession state, String key) {
        return text(state, key, Map.of());
    }

    private String text(ForgeGuiSession state, String key, Map<String, ?> replacements) {
        MessageService messageService = state == null || state.runtimeSnapshot() == null
                ? null
                : state.runtimeSnapshot().messageService();
        if (messageService == null) {
            messageService = plugin == null ? null : plugin.messageService();
        }
        return messageService == null ? "" : messageService.message(key, replacements);
    }

    private String capacityStateText(ForgeGuiSession state) {
        if (state.maxCapacity() <= 0) {
            return guiSupport.text(state.runtimeSnapshot(), "forge_gui", "texts.capacity_state.waiting", "<gray>等待图纸</gray>", Map.of());
        }
        if (state.currentCapacity() > state.maxCapacity()) {
            return guiSupport.text(state.runtimeSnapshot(), "forge_gui", "texts.capacity_state.overflow", "<red>已超限</red>", Map.of());
        }
        if (state.currentCapacity() >= Math.max(1, (int) Math.ceil(state.maxCapacity() * 0.8D))) {
            return guiSupport.text(state.runtimeSnapshot(), "forge_gui", "texts.capacity_state.warning", "<gold>接近上限</gold>", Map.of());
        }
        return guiSupport.text(state.runtimeSnapshot(), "forge_gui", "texts.capacity_state.normal", "<green>正常</green>", Map.of());
    }
}
