package emaki.jiuwu.craft.station.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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

public final class StationCatalogRenderer {

    private final ItemSourceService itemSourceService;
    private final Supplier<ConfiguredItemService> itemServiceSupplier;
    private final ConfiguredGuiSupport guiSupport;

    public StationCatalogRenderer(ItemSourceService itemSourceService,
            Supplier<ConfiguredItemService> itemServiceSupplier,
            ConfiguredGuiSupport guiSupport) {
        this.itemSourceService = itemSourceService;
        this.itemServiceSupplier = itemServiceSupplier;
        this.guiSupport = guiSupport;
    }

    public ItemStack render(StationViewState state,
            List<StationCatalogEntry> entries,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        if (state == null || resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = StationSlotType.normalize(slot.type());
        if (type.isEmpty()) {
            type = StationSlotType.normalize(slot.key());
        }
        return switch (type) {
            case StationSlotType.RECIPE_LIST -> renderEntry(state, entries, slot, resolvedSlot);
            case StationSlotType.BATCH_MULTIPLIER -> renderBatch(state, slot);
            case StationSlotType.OUTPUT_TOGGLE -> renderOutputToggle(state, slot);
            case StationSlotType.PAGE_INFO, StationSlotType.PREV_PAGE, StationSlotType.NEXT_PAGE ->
                    renderPageInfo(state, entries, slot);
            default -> null;
        };
    }

    public Map<String, Object> titleReplacements(StationViewState state, List<StationCatalogEntry> entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("station_name", state.station().displayName());
        values.put("station", state.station().id());
        values.put("player", state.viewer().getName());
        values.put("page", String.valueOf(state.catalogPage() + 1));
        values.put("pages", String.valueOf(Math.max(1, entries.size())));
        return values;
    }

    private ItemStack renderEntry(StationViewState state,
            List<StationCatalogEntry> entries,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        int pageSize = Math.max(1, slot.slots().size());
        int offset = state.catalogPage() * pageSize + resolvedSlot.slotIndex();
        if (offset < 0 || offset >= entries.size()) {
            return null;
        }
        StationCatalogEntry entry = entries.get(offset);
        RecipeDefinition recipe = entry.recipe();
        String layoutId = state.station().layoutId();
        ItemStack icon = iconOf(recipe);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("recipe", recipe.id());
        values.put("recipe_name", recipe.displayName());
        values.put("result_name", outputNameOf(recipe));
        values.put("result_amount", AmountDisplay.compact(totalOutput(recipe, 1L)));
        values.put("duration", DurationDisplay.format(
                recipe.effectiveDurationMillis(state.station().queueSettings().speedMultiplier())));
        values.put("batch", AmountDisplay.compact(state.batch()));
        values.put("index", String.valueOf(offset + 1));
        values.put("page", String.valueOf(state.catalogPage() + 1));
        values.put("pages", String.valueOf(GuiPagination.totalPages(entries.size(), pageSize)));
        values.put("conditions", conditionLines(layoutId, recipe));
        values.put("costs", costLines(layoutId, recipe));
        values.put("materials", materialLines(layoutId, recipe));
        if (!entry.unlocked()) {
            values.put("lock_reason", guiSupport.text(layoutId, "texts.recipe.locked",
                    "Locked", Map.of()));
            return guiSupport.build(layoutId, "virtual_items.recipe_locked", values,
                    fallbackLocked(recipe.displayName()));
        }
        return guiSupport.apply(layoutId, "virtual_items.recipe_entry", icon, values);
    }

    private List<String> conditionLines(String layoutId, RecipeDefinition recipe) {
        List<String> lines = new ArrayList<>();
        if (recipe.hasPermission()) {
            lines.add(guiSupport.text(layoutId, "texts.recipe.condition_permission",
                    "Requires permission", Map.of("permission", recipe.permission())));
        }
        if (recipe.condition().configured()) {
            lines.add(guiSupport.text(layoutId, "texts.recipe.condition_gate",
                    "Has craft conditions", Map.of()));
        }
        if (recipe.hasDisplayCondition()) {
            lines.add(guiSupport.text(layoutId, "texts.recipe.condition_display",
                    "Has unlock conditions", Map.of()));
        }
        if (lines.isEmpty()) {
            lines.add(guiSupport.text(layoutId, "texts.recipe.condition_none", "No conditions", Map.of()));
        }
        return lines;
    }

    private List<String> costLines(String layoutId, RecipeDefinition recipe) {
        List<String> lines = new ArrayList<>();
        if (recipe.cost().charges()) {
            lines.add(guiSupport.text(layoutId, "texts.recipe.cost_currency", "Costs %amount%",
                    Map.of("amount", AmountDisplay.precise(recipe.cost().amount()),
                            "currency", recipe.cost().providerId())));
        }
        lines.add(guiSupport.text(layoutId, "texts.recipe.cost_duration", "Takes %duration%",
                Map.of("duration", DurationDisplay.format(recipe.durationSeconds() * 1_000L))));

        for (MaterialRequirement requirement : recipe.requirements()) {
            if (requirement.consume()) {
                continue;
            }
            lines.add(guiSupport.text(layoutId, "texts.recipe.cost_keep", "Requires holding %material% x%amount%",
                    Map.of("material", displayNameOf(requirement.sources().getFirst()),
                            "amount", AmountDisplay.precise(requirement.amount()))));
        }
        return lines;
    }

    private List<String> materialLines(String layoutId, RecipeDefinition recipe) {
        List<String> lines = new ArrayList<>();
        for (MaterialRequirement requirement : recipe.requirements()) {
            if (!requirement.consume()) {
                continue;
            }
            lines.add(guiSupport.text(layoutId, "texts.recipe.material_line", "%material% x%amount%",
                    Map.of("material", displayNameOf(requirement.sources().getFirst()),
                            "amount", AmountDisplay.precise(requirement.amount()),
                            "alternatives", String.valueOf(requirement.sources().size() - 1))));
        }
        if (lines.isEmpty()) {
            lines.add(guiSupport.text(layoutId, "texts.recipe.material_none", "No materials", Map.of()));
        }
        return lines;
    }

    private ItemStack renderBatch(StationViewState state, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("batch", AmountDisplay.compact(state.batch()));
        values.put("batch_exact", AmountDisplay.precise(state.batch()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderOutputToggle(StationViewState state, GuiSlot slot) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("output", state.outputRouting().token());
        values.put("output_state", guiSupport.text(state.station().layoutId(),
                "texts.output_state." + state.outputRouting().token(),
                state.outputRouting().token(), Map.of()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderPageInfo(StationViewState state,
            List<StationCatalogEntry> entries,
            GuiSlot slot) {
        int pageSize = pageSizeOf(state, StationSlotType.RECIPE_LIST);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("page", String.valueOf(state.catalogPage() + 1));
        values.put("pages", String.valueOf(GuiPagination.totalPages(entries.size(), pageSize)));
        values.put("total", String.valueOf(entries.size()));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private int pageSizeOf(StationViewState state, String type) {
        if (state.guiSession() != null) {
            return Math.max(1, GuiPagination.pageSize(state.guiSession().template(), type));
        }
        return 1;
    }

    private ItemStack iconOf(RecipeDefinition recipe) {
        if (recipe.outputs().isEmpty() || itemSourceService == null) {
            return new ItemStack(Material.PAPER);
        }
        ItemStack icon = itemSourceService.createItem(recipe.outputs().getFirst().source(), 1);
        return icon == null || icon.getType().isAir() ? new ItemStack(Material.PAPER) : icon;
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

    private static long totalOutput(RecipeDefinition recipe, long batch) {
        if (recipe.outputs().isEmpty()) {
            return 0L;
        }
        return recipe.outputs().getFirst().totalFor(batch);
    }

    private static ConfiguredItemDefinition fallbackLocked(String recipeName) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>Not unlocked</dark_gray>");
        return new ConfiguredItemDefinition("GRAY_DYE", 1, Map.of(
                "minecraft:custom_name", ItemComponentPatch.set("<dark_gray>" + recipeName + "</dark_gray>"),
                "minecraft:lore", ItemComponentPatch.set(lore)));
    }
}
