package emaki.jiuwu.craft.station.dismantle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.item.ItemSourceService;
import emaki.jiuwu.craft.station.gui.AmountDisplay;
import emaki.jiuwu.craft.station.gui.ConfiguredGuiSupport;
import emaki.jiuwu.craft.station.gui.StationSlotType;

public final class DismantleGuiRenderer {

    private final ItemSourceService itemSourceService;
    private final Supplier<ConfiguredItemService> itemServiceSupplier;
    private final ConfiguredGuiSupport guiSupport;

    public DismantleGuiRenderer(ItemSourceService itemSourceService,
            Supplier<ConfiguredItemService> itemServiceSupplier,
            ConfiguredGuiSupport guiSupport) {
        this.itemSourceService = itemSourceService;
        this.itemServiceSupplier = itemServiceSupplier;
        this.guiSupport = guiSupport;
    }

    public ItemStack render(DismantleViewState state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (state == null || resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        DismantleRecipeDefinition recipe = state.selectedRecipe();
        GuiSlot slot = resolvedSlot.definition();
        String type = StationSlotType.normalize(slot.type());
        if (type.isEmpty()) {
            type = StationSlotType.normalize(slot.key());
        }
        return switch (type) {
            case StationSlotType.DISMANTLE_INPUT, StationSlotType.DISMANTLE_ITEM_DISPLAY ->
                    renderInput(state, recipe, slot);
            case StationSlotType.DISMANTLE_ROLLS_DISPLAY -> renderRolls(state, recipe, slot);
            case StationSlotType.DISMANTLE_OUTPUT_LIST -> renderOutputEntry(state, recipe, slot, resolvedSlot);
            case StationSlotType.DISMANTLE_CONFIRM -> renderConfirm(state, recipe, slot);
            case StationSlotType.PAGE_INFO, StationSlotType.PREV_PAGE, StationSlotType.NEXT_PAGE ->
                    renderPageInfo(state, recipe, slot, resolvedSlot);
            default -> null;
        };
    }

    public Map<String, Object> titleReplacements(DismantleViewState state) {
        DismantleRecipeDefinition recipe = state.selectedRecipe();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("station_name", state.station().displayName());
        values.put("station", state.station().id());
        values.put("player", state.viewer().getName());
        values.put("recipe", recipe == null ? "" : recipe.id());
        values.put("recipe_name", recipe == null ? "" : recipe.displayName());
        return values;
    }

    private ItemStack renderInput(DismantleViewState state,
            DismantleRecipeDefinition recipe,
            GuiSlot slot) {
        if (recipe == null) {
            return guiSupport.build(layoutId(state), "virtual_items.no_recipe",
                    Map.of(), fallbackNoRecipe());
        }
        ItemStack icon = buildIcon(recipe.inputSource());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("recipe", recipe.id());
        values.put("recipe_name", recipe.displayName());
        values.put("input", displayNameOf(recipe.inputSource()));
        return guiSupport.apply(layoutId(state), "virtual_items.dismantle_input", icon, values);
    }

    private ItemStack renderRolls(DismantleViewState state,
            DismantleRecipeDefinition recipe,
            GuiSlot slot) {
        if (recipe == null) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("rolls_min", String.valueOf(recipe.rolls().min()));
        values.put("rolls_max", String.valueOf(recipe.rolls().max()));
        values.put("recipe_name", recipe.displayName());
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderOutputEntry(DismantleViewState state,
            DismantleRecipeDefinition recipe,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        if (recipe == null) {
            return null;
        }

        if (state.hasRolled()) {
            return renderRolledOutput(state, slot, resolvedSlot);
        }
        return renderPoolEntry(state, recipe, slot, resolvedSlot);
    }

    private ItemStack renderPoolEntry(DismantleViewState state,
            DismantleRecipeDefinition recipe,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        List<DismantlePoolEntry> pool = recipe.pool();
        int pageSize = Math.max(1, slot.slots().size());
        int offset = state.outputPage() * pageSize + resolvedSlot.slotIndex();
        if (offset < 0 || offset >= pool.size()) {
            return null;
        }
        DismantlePoolEntry entry = pool.get(offset);
        ItemStack icon = buildIcon(entry.source());
        double totalWeight = pool.stream().mapToDouble(DismantlePoolEntry::weight).sum();
        double chance = totalWeight > 0 ? entry.weight() / totalWeight * 100.0 : 0.0;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("item", displayNameOf(entry.source()));
        values.put("amount_min", String.valueOf(entry.amount().min()));
        values.put("amount_max", String.valueOf(entry.amount().max()));
        values.put("weight", String.format("%.1f", entry.weight()));
        values.put("chance", String.format("%.1f", chance));
        values.put("index", String.valueOf(offset + 1));
        values.put("page", String.valueOf(state.outputPage() + 1));
        values.put("pages", String.valueOf(GuiPagination.totalPages(pool.size(), pageSize)));
        return guiSupport.apply(layoutId(state), "virtual_items.pool_entry", icon, values);
    }

    private ItemStack renderRolledOutput(DismantleViewState state,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        List<DismantleOutput> outputs = state.rolledOutputs();
        int pageSize = Math.max(1, slot.slots().size());
        int offset = state.outputPage() * pageSize + resolvedSlot.slotIndex();
        if (offset < 0 || offset >= outputs.size()) {
            return null;
        }
        DismantleOutput output = outputs.get(offset);
        ItemStack icon = buildIcon(output.source());
        if (icon != null) {
            icon.setAmount(Math.clamp(output.amount(), 1, icon.getMaxStackSize()));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("item", displayNameOf(output.source()));
        values.put("amount", AmountDisplay.compact(output.amount()));
        values.put("amount_exact", String.valueOf(output.amount()));
        values.put("index", String.valueOf(offset + 1));
        values.put("total", String.valueOf(outputs.size()));
        return guiSupport.apply(layoutId(state), "virtual_items.rolled_output", icon, values);
    }

    private ItemStack renderConfirm(DismantleViewState state,
            DismantleRecipeDefinition recipe,
            GuiSlot slot) {
        if (recipe == null) {
            return null;
        }
        if (state.hasRolled()) {

            Map<String, Object> values = new LinkedHashMap<>();
            values.put("recipe_name", recipe.displayName());
            values.put("output_count", String.valueOf(state.rolledOutputs().size()));
            return guiSupport.build(layoutId(state), "virtual_items.dismantle_claim", values,
                    slot.itemDefinition());
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("recipe", recipe.id());
        values.put("recipe_name", recipe.displayName());
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack renderPageInfo(DismantleViewState state,
            DismantleRecipeDefinition recipe,
            GuiSlot slot,
            GuiTemplate.ResolvedSlot resolvedSlot) {
        if (recipe == null) {
            return null;
        }
        int listSize = state.hasRolled() ? state.rolledOutputs().size() : recipe.pool().size();
        int pageSize = state.guiSession() == null
                ? 1
                : Math.max(1, GuiPagination.pageSize(state.guiSession().template(),
                        StationSlotType.DISMANTLE_OUTPUT_LIST));
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("page", String.valueOf(state.outputPage() + 1));
        values.put("pages", String.valueOf(GuiPagination.totalPages(listSize, pageSize)));
        values.put("total", String.valueOf(listSize));
        return GuiItemBuilder.build(slot.itemDefinition(), values, itemServiceSupplier.get());
    }

    private ItemStack buildIcon(ItemSourceRef source) {
        if (itemSourceService == null || source == null) {
            return new ItemStack(Material.PAPER);
        }
        ItemStack icon = itemSourceService.createItem(source, 1);
        return (icon == null || icon.getType().isAir()) ? new ItemStack(Material.BARRIER) : icon;
    }

    private String displayNameOf(ItemSourceRef source) {
        if (itemSourceService == null || source == null) {
            return source == null ? "" : source.identifier();
        }
        String name = itemSourceService.displayName(source);
        return name == null || name.isBlank() ? source.identifier() : name;
    }

    private static String layoutId(DismantleViewState state) {
        return state.station().layoutId();
    }

    private static ConfiguredItemDefinition fallbackNoRecipe() {
        return new ConfiguredItemDefinition("BARRIER", 1, Map.of());
    }
}
