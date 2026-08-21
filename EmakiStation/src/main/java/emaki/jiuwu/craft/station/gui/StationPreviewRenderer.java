package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.item.ItemComponentPatch;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.recipe.MaterialRequirement;
import emaki.jiuwu.craft.station.recipe.RecipeDefinition;

public final class StationPreviewRenderer {

    private final ItemSourceService itemSourceService;
    private final Supplier<ConfiguredItemService> itemServiceSupplier;
    private final ConfiguredGuiSupport guiSupport;

    public StationPreviewRenderer(ItemSourceService itemSourceService,
            Supplier<ConfiguredItemService> itemServiceSupplier,
            ConfiguredGuiSupport guiSupport) {
        this.itemSourceService = itemSourceService;
        this.itemServiceSupplier = itemServiceSupplier;
        this.guiSupport = guiSupport;
    }

    public ItemStack render(StationViewState state,
            long maxBatch,
            double balance,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        if (state == null || resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        RecipeDefinition recipe = state.selectedRecipe();
        if (recipe == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = StationSlotType.normalize(slot.type());
        if (type.isEmpty()) {
            type = StationSlotType.normalize(slot.key());
        }
        return switch (type) {
            case StationSlotType.MATERIAL_LIST -> renderMaterial(state, recipe, slot, resolvedSlot);
            case StationSlotType.RECIPE_DISPLAY -> renderRecipe(state, recipe, slot);
            case StationSlotType.COST_DISPLAY -> renderCost(state, recipe, slot, balance);
            case StationSlotType.MAX_CRAFTABLE -> renderMaxCraftable(state, slot, maxBatch);
            case StationSlotType.BATCH_MULTIPLIER -> renderBatch(state, slot);
            case StationSlotType.CONFIRM -> renderConfirm(state, recipe, slot);
            case StationSlotType.PAGE_INFO, StationSlotType.PREV_PAGE, StationSlotType.NEXT_PAGE ->
                    renderPageInfo(state, recipe, slot);
            default -> null;
        };
    }

    public Map<String, Object> titleReplacements(StationViewState state) {
        RecipeDefinition recipe = state.selectedRecipe();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("station_name", state.station().displayName());
        values.put("station", state.station().id());
        values.put("player", state.viewer().getName());
        values.put("recipe", recipe == null ? "" : recipe.id());
        values.put("recipe_name", recipe == null ? "" : recipe.displayName());
        return values;
    }

    private ItemStack renderMaterial(StationViewState state,
            RecipeDefinition recipe,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        List<MaterialRequirement> requirements = recipe.requirements();
        int pageSize = Math.max(1, slot.slots().size());
        int offset = state.materialPage() * pageSize + resolvedSlot.slotIndex();
        if (offset < 0 || offset >= requirements.size()) {
            return null;
        }
        MaterialRequirement requirement = requirements.get(offset);
        ItemSourceRef primary = requirement.primarySource();
        long required = requirement.totalFor(state.batch());
        long owned = state.availability().totalOf(requirement);
        ItemStack icon = itemSourceService == null || primary == null
                ? null
                : itemSourceService.createItem(primary, 1);
        if (icon == null || icon.getType().isAir()) {
            icon = new ItemStack(Material.BARRIER);
        }
        applyRequiredAmount(icon, required);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("material", materialNameOf(state, requirement));
        values.put("required", AmountDisplay.compact(required));
        values.put("required_exact", AmountDisplay.precise(required));
        values.put("owned", AmountDisplay.compact(owned));
        values.put("owned_exact", AmountDisplay.precise(owned));
        values.put("satisfied", owned >= required ? "true" : "false");
        values.put("consumed", requirement.consume() ? "true" : "false");
        values.put("alternatives", String.valueOf(Math.max(0, requirement.sources().size() - 1)));
        values.put("index", String.valueOf(offset + 1));
        values.put("page", String.valueOf(state.materialPage() + 1));
        values.put("pages", String.valueOf(GuiPagination.totalPages(requirements.size(), pageSize)));
        String path = owned >= required
                ? "virtual_items.material_satisfied"
                : "virtual_items.material_missing";
        return guiSupport.apply(state.station().previewLayoutId(), path, icon, values);
    }

    private void applyRequiredAmount(ItemStack icon, long required) {
        int rendered = AmountDisplay.previewStackSize(required);
        if (rendered > icon.getMaxStackSize()) {

            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setMaxStackSize(AmountDisplay.MAX_RENDERED_STACK);
                icon.setItemMeta(meta);
            }
        }
        icon.setAmount(rendered);
    }

    private ItemStack renderRecipe(StationViewState state, RecipeDefinition recipe, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("recipe", recipe.id());
        values.put("recipe_name", recipe.displayName());
        values.put("result_name", outputNameOf(recipe));
        values.put("result_amount", AmountDisplay.compact(totalOutput(recipe, state.batch())));
        values.put("duration", DurationDisplay.format(
                recipe.effectiveDurationMillis(state.station().queueSettings().speedMultiplier())));
        values.put("batch", AmountDisplay.compact(state.batch()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderCost(StationViewState state,
            RecipeDefinition recipe,
            GuiSlot slot,
            double balance) {
        long charge = recipe.cost().totalFor(state.batch());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("cost", AmountDisplay.compact(charge));
        values.put("cost_exact", AmountDisplay.precise(charge));
        values.put("currency", recipe.cost().providerId());
        values.put("balance", AmountDisplay.precise((long) balance));
        values.put("affordable", charge <= 0L || balance >= (double) charge ? "true" : "false");
        values.put("free", recipe.cost().charges() ? "false" : "true");
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderMaxCraftable(StationViewState state, GuiSlot slot, long maxBatch) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("max", AmountDisplay.compact(maxBatch));
        values.put("max_exact", AmountDisplay.precise(maxBatch));
        values.put("batch", AmountDisplay.compact(state.batch()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderBatch(StationViewState state, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("batch", AmountDisplay.compact(state.batch()));
        values.put("batch_exact", AmountDisplay.precise(state.batch()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderConfirm(StationViewState state, RecipeDefinition recipe, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("recipe", recipe.id());
        values.put("recipe_name", recipe.displayName());
        values.put("batch", AmountDisplay.compact(state.batch()));
        values.put("block_reason", state.blockReason());
        if (!state.blockReason().isEmpty()) {
            values.put("block_text", guiSupport.text(state.station().previewLayoutId(),
                    "texts.block_reason." + state.blockReason(), state.blockReason(), Map.of()));
            return guiSupport.build(state.station().previewLayoutId(),
                    "virtual_items.confirm_blocked", values, fallbackBlocked(state.blockReason()));
        }
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderPageInfo(StationViewState state, RecipeDefinition recipe, GuiSlot slot) {
        int pageSize = state.guiSession() == null
                ? 1
                : Math.max(1, GuiPagination.pageSize(state.guiSession().template(),
                        StationSlotType.MATERIAL_LIST));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("page", String.valueOf(state.materialPage() + 1));
        values.put("pages", String.valueOf(
                GuiPagination.totalPages(recipe.requirements().size(), pageSize)));
        values.put("total", String.valueOf(recipe.requirements().size()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private String outputNameOf(RecipeDefinition recipe) {
        if (recipe.outputs().isEmpty()) {
            return recipe.displayName();
        }
        return displayNameOf(recipe.outputs().getFirst().source());
    }

    private String displayNameOf(ItemSourceRef source) {
        if (itemSourceService == null) {
            return source.identifier();
        }
        String name = itemSourceService.displayName(source);
        return name == null || name.isBlank() ? source.identifier() : name;
    }

    private String materialNameOf(StationViewState state, MaterialRequirement requirement) {
        ItemSourceRef primary = requirement.primarySource();
        if (primary != null) {
            return displayNameOf(primary);
        }
        return guiSupport.text(state.station().previewLayoutId(), "texts.recipe.material_matcher",
                "Custom condition", Map.of());
    }

    private static long totalOutput(RecipeDefinition recipe, long batch) {
        if (recipe.outputs().isEmpty()) {
            return 0L;
        }
        return recipe.outputs().getFirst().totalFor(batch);
    }

    private static ConfiguredItemDefinition fallbackBlocked(String reason) {
        List<String> lore = new ArrayList<>();
        lore.add("<red>" + reason + "</red>");
        return new ConfiguredItemDefinition("BARRIER", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<red>Cannot craft</red>"),
                "minecraft:lore", ItemComponentPatch.set(lore)));
    }
}
