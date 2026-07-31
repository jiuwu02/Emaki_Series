package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.item.EmakiItemPlugin;
import emaki.jiuwu.craft.item.model.EmakiItemDefinition;
import emaki.jiuwu.craft.item.model.RepairMaterial;

final class ItemRepairGuiRenderer {

    private final EmakiItemPlugin plugin;
    private final ItemRepairService repairService;

    ItemRepairGuiRenderer(EmakiItemPlugin plugin, ItemRepairService repairService) {
        this.plugin = plugin;
        this.repairService = repairService;
    }

    public ItemStack renderSlot(ItemRepairGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        ItemStack dynamic = switch (type) {
            case "target_item" -> ItemRepairGuiSession.cloneNonAir(state.targetItem());
            case "preview_display" -> buildPreviewItem(state, slot);
            case "material_repair" -> buildMaterialRepairItem(state, slot);
            case "economy_repair" -> buildEconomyRepairItem(state, slot);
            default -> {
                if (type.startsWith("material_input_")) {
                    int index = parseMaterialIndex(type);
                    yield index >= 0 ? ItemRepairGuiSession.cloneNonAir(state.materialInput(index)) : null;
                }
                yield null;
            }
        };
        if (dynamic != null) {
            return dynamic;
        }
        return buildStatic(slot);
    }

    public void refreshGui(ItemRepairGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.guiSession().refresh();
    }

    private ItemStack buildPreviewItem(ItemRepairGuiSession state, GuiSlot slot) {
        EmakiItemDefinition definition = definition(state);
        if (state.targetItem() == null) {
            return buildItem(slot, "BOOK", msg("repair.preview.title"), List.of(
                    msg("repair.preview.no_target_1"),
                    msg("repair.preview.no_target_2")
            ));
        }
        if (definition == null) {
            return buildItem(slot, "BARRIER", msg("repair.preview.invalid_title"), List.of(msg("repair.preview.invalid_item")));
        }
        if (!definition.repair().enabled()) {
            return buildItem(slot, "BARRIER", msg("repair.preview.invalid_title"), List.of(msg("repair.preview.repair_disabled")));
        }
        int maxDamage = repairService.maxDamage(state.targetItem());
        int damage = repairService.currentDamage(state.targetItem());
        if (maxDamage <= 0) {
            return buildItem(slot, "BARRIER", msg("repair.preview.invalid_title"), List.of(msg("repair.preview.not_repairable")));
        }
        List<String> lore = new ArrayList<>();
        lore.add(msg("repair.preview.item", Map.of("id", definition.id())));
        lore.add(msg("repair.preview.damage", Map.of("damage", damage, "max", maxDamage)));
        lore.add(msg(repairService.isDisabled(state.targetItem()) ? "repair.preview.disabled" : "repair.preview.enabled"));
        if (damage <= 0 && !repairService.isDisabled(state.targetItem())) {
            lore.add(msg("repair.preview.already_repaired"));
        }
        appendMaterialLines(lore, definition, state);
        appendEconomyLines(lore, definition, state);
        return buildItem(slot, "BOOK", msg("repair.preview.title"), lore);
    }

    private ItemStack buildMaterialRepairItem(ItemRepairGuiSession state, GuiSlot slot) {
        EmakiItemDefinition definition = definition(state);
        List<String> lore = new ArrayList<>();
        if (definition == null || !definition.repair().enabled()) {
            lore.add(msg("repair.material_button.no_target"));
            return buildItem(slot, "BARRIER", msg("repair.material_button.blocked"), lore);
        }
        RepairMaterial material = repairService.findAffordableMaterial(definition, state.materialInputMap());
        if (material == null) {
            lore.add(msg("repair.material_button.need_material"));
            return buildItem(slot, "BARRIER", msg("repair.material_button.blocked"), lore);
        }
        lore.add(msg("repair.material_button.material", Map.of(
                "material", material.displaySources(),
                "amount", material.amount(),
                "restore", material.restoreRaw()
        )));
        return buildItem(slot, "ANVIL", msg("repair.material_button.ready"), lore);
    }

    private ItemStack buildEconomyRepairItem(ItemRepairGuiSession state, GuiSlot slot) {
        EmakiItemDefinition definition = definition(state);
        if (definition == null || !definition.repair().enabled() || !definition.repair().hasEconomyRepair()) {
            return buildItem(slot, "BARRIER", msg("repair.economy_button.blocked"), List.of(msg("repair.economy_button.disabled")));
        }
        ItemRepairService.EconomyQuote quote = repairService.quoteEconomy(state.player(), definition, state.targetItem());
        List<String> lore = new ArrayList<>();
        if (!quote.success()) {
            lore.add(msg(quote.errorKey(), quote.replacements()));
            return buildItem(slot, "BARRIER", msg("repair.economy_button.blocked"), lore);
        }
        lore.add(msg("repair.economy_button.restore", Map.of("restore", quote.restoreAmount())));
        for (ItemRepairService.CurrencyQuote currency : quote.currencies()) {
            lore.add(msg("repair.economy_button.cost", Map.of(
                    "name", currency.cost().effectiveDisplayName(),
                    "required", formatAmount(currency.amount()),
                    "available", formatAmount(currency.balance())
            )));
        }
        return buildItem(slot, "EMERALD", msg("repair.economy_button.ready"), lore);
    }

    private void appendMaterialLines(List<String> lore, EmakiItemDefinition definition, ItemRepairGuiSession state) {
        if (definition.repair().materials().isEmpty()) {
            lore.add(msg("repair.preview.materials_empty"));
            return;
        }
        lore.add(msg("repair.preview.materials_header"));
        for (RepairMaterial material : definition.repair().materials()) {
            long available = repairService.countProvidedMaterial(state.materialInputMap(), material);
            String key = available >= material.amount() ? "repair.preview.material_line_ready" : "repair.preview.material_line_missing";
            lore.add(msg(key, Map.of(
                    "material", material.displaySources(),
                    "required", material.amount(),
                    "available", available,
                    "restore", material.restoreRaw()
            )));
        }
    }

    private void appendEconomyLines(List<String> lore, EmakiItemDefinition definition, ItemRepairGuiSession state) {
        if (!definition.repair().hasEconomyRepair()) {
            lore.add(msg("repair.preview.economy_disabled"));
            return;
        }
        ItemRepairService.EconomyQuote quote = repairService.quoteEconomy(state.player(), definition, state.targetItem());
        lore.add(msg("repair.preview.economy_header"));
        if (!quote.success()) {
            lore.add(msg(quote.errorKey(), quote.replacements()));
            return;
        }
        for (ItemRepairService.CurrencyQuote currency : quote.currencies()) {
            lore.add(msg(currency.affordable() ? "repair.preview.economy_line_ready" : "repair.preview.economy_line_missing", Map.of(
                    "name", currency.cost().effectiveDisplayName(),
                    "required", formatAmount(currency.amount()),
                    "available", formatAmount(currency.balance())
            )));
        }
    }

    private EmakiItemDefinition definition(ItemRepairGuiSession state) {
        if (state == null || state.targetItem() == null) {
            return null;
        }
        String id = plugin.identifier().identify(state.targetItem());
        return Texts.isBlank(id) ? null : plugin.itemLoader().get(id);
    }

    private ItemStack buildStatic(GuiSlot slot) {
        return GuiItemBuilder.build(slot.itemDefinition(), Map.of(), plugin.coreLib().configuredItemService());
    }

    private ItemStack buildItem(GuiSlot slot, String item, String name, List<String> lore) {
        return GuiItemBuilder.build(
                slot,
                Texts.isBlank(slot == null ? null : slot.item()) ? item : slot.item(),
                name,
                lore,
                Map.of(),
                plugin.coreLib().configuredItemService()
        );
    }

    private String msg(String key) {
        return plugin.messageService().message(key);
    }

    private String msg(String key, Map<String, ?> replacements) {
        return plugin.messageService().message(key, replacements == null ? Map.of() : replacements);
    }

    private String formatAmount(double value) {
        if (Math.rint(value) == value) {
            return Long.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static int parseMaterialIndex(String type) {
        try {
            return Integer.parseInt(type.substring("material_input_".length())) - 1;
        } catch (NumberFormatException _) {
            return -1;
        }
    }
}
