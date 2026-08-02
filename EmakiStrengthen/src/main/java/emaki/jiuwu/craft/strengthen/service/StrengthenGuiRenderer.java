package emaki.jiuwu.craft.strengthen.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.EmakiCoreLibApi;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.api.itemsource.ItemSourceRef;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.api.model.AttemptMaterial;
import emaki.jiuwu.craft.strengthen.api.model.AttemptPreview;
import emaki.jiuwu.craft.strengthen.api.model.StrengthenState;

final class StrengthenGuiRenderer {

    private final EmakiStrengthenPlugin plugin;
    private final StrengthenAttemptService attemptService;

    StrengthenGuiRenderer(EmakiStrengthenPlugin plugin, StrengthenAttemptService attemptService) {
        this.plugin = plugin;
        this.attemptService = attemptService;
    }

    public ItemStack renderSlot(StrengthenGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        ItemStack dynamic = switch (type) {
            case "target_item" -> StrengthenGuiSession.cloneNonAir(state.targetItem());
            case "preview_display" -> buildPreviewItem(state, slot);
            case "temper_display" -> buildTemperItem(state, slot);
            case "confirm" -> buildConfirmItem(state, slot);
            default -> {
                if (type.startsWith("material_input_")) {
                    int index = parseMaterialIndex(type);
                    yield index >= 0 ? StrengthenGuiSession.cloneNonAir(state.materialInput(index)) : null;
                }
                yield null;
            }
        };
        if (dynamic != null) {
            return dynamic;
        }
        return GuiItemBuilder.build(slot.itemDefinition(), Map.of(), plugin.coreLib().configuredItemService());
    }

    public void refreshGui(StrengthenGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.setPreview(attemptService.preview(state.player(), state.toAttemptContext()));
        state.guiSession().refresh();
    }

    private ItemStack buildPreviewItem(StrengthenGuiSession state, GuiSlot slot) {
        AttemptPreview preview = state.preview();
        if (state.targetItem() == null) {
            return buildItem(slot, "BOOK", msg("gui.preview.title"), List.of(
                    msg("gui.preview.hint_no_target_1"),
                    msg("gui.preview.hint_no_target_2")
            ));
        }
        if (preview == null || !preview.eligible()) {
            List<String> lore = new ArrayList<>();
            lore.add(msg("gui.preview.ineligible_hint"));
            if (preview != null && Texts.isNotBlank(preview.errorKey())) {
                lore.add("<red>" + msg(preview.errorKey()) + "</red>");
            }
            appendMaterialLines(lore, preview);
            return buildItem(slot, "BOOK", msg("gui.preview.ineligible_title"), lore);
        }
        List<String> lore = new ArrayList<>();
        lore.add(msg("gui.preview.current_star", Map.of("star", preview.currentStar())));
        lore.add(msg("gui.preview.target_star", Map.of("star", preview.targetStar())));
        appendRateAndCostSummary(lore, preview);
        lore.add(preview.protectionApplied()
                ? msg("strengthen.preview.failure_drop_protected")
                : (preview.failureStar() < preview.currentStar()
                ? msg("strengthen.preview.failure_drop")
                : msg("strengthen.preview.failure_keep")));
        appendMaterialLines(lore, preview);
        if (!preview.successDeltaStats().isEmpty()) {
            lore.add(msg("gui.preview.delta_stats_header"));
            preview.successDeltaStats().forEach((id, value)
                    -> lore.add(msg("gui.preview.delta_stats_line", Map.of("id", id, "value", Numbers.formatNumber(value, "0.##")))));
        }
        return buildItem(slot, "BOOK", msg("gui.preview.title"), lore);
    }

    private void appendMaterialLines(List<String> lore, AttemptPreview preview) {
        if (lore == null || preview == null) {
            return;
        }
        if (!preview.requiredMaterials().isEmpty()) {
            lore.add(msg("gui.preview.required_materials_header"));
            for (AttemptMaterial material : preview.requiredMaterials()) {
                String color = material.satisfied() ? "<green>" : "<red>";
                lore.add(color + " - " + materialDisplayName(material.item()) + " x" + material.requiredAmount()
                        + " <gray>(" + material.availableAmount() + "/" + material.requiredAmount() + ")</gray>");
            }
        }
        boolean hasOptional = preview.optionalMaterials().stream().anyMatch(material -> material != null && Texts.isNotBlank(material.item()));
        if (hasOptional) {
            lore.add(msg("gui.preview.optional_materials_header"));
            for (AttemptMaterial material : preview.optionalMaterials()) {
                if (material == null || Texts.isBlank(material.item())) {
                    continue;
                }
                lore.add("<aqua> - " + materialDisplayName(material.item()) + " x" + material.availableAmount() + "</aqua>");
            }
        }
    }

    private String materialDisplayName(String item) {
        if (Texts.isBlank(item)) {
            return msg("strengthen.misc.unknown_material");
        }
        String displayName = EmakiCoreLibApi.itemDisplayName(item).orElse("");
        return Texts.isBlank(displayName) ? item : displayName;
    }

    private ItemStack buildTemperItem(StrengthenGuiSession state, GuiSlot slot) {
        StrengthenState strengthenState = state.preview() == null ? null : state.preview().state();
        int temper = strengthenState == null ? 0 : strengthenState.temperLevel();
        int maxTemper = state.preview() == null || state.preview().recipe() == null ? 0 : state.preview().recipe().limits().maxTemper();
        List<String> lore = new ArrayList<>();
        lore.add(msg("gui.temper.current", Map.of("temper", temper, "max", maxTemper)));
        if (state.preview() != null && state.preview().appliedTemperBonus() > 0) {
            lore.add(msg("gui.temper.bonus", Map.of("bonus", state.preview().appliedTemperBonus())));
        }
        lore.add(msg("gui.temper.hint"));
        return buildItem(slot, "INK_SAC", msg("gui.temper.title"), lore);
    }

    private ItemStack buildConfirmItem(StrengthenGuiSession state, GuiSlot slot) {
        AttemptPreview preview = state.preview();
        if (preview == null || !preview.eligible()) {
            return buildItem(slot, "BARRIER", msg("gui.confirm.ineligible_title"), List.of(msg("gui.confirm.ineligible_hint")));
        }
        List<String> lore = new ArrayList<>();
        appendRateAndCostSummary(lore, preview);
        return buildItem(slot, "ANVIL", msg("gui.confirm.title"), lore);
    }

    private void appendRateAndCostSummary(List<String> lore, AttemptPreview preview) {
        lore.add(msg("gui.preview.success_rate", Map.of("rate", Numbers.formatNumber(preview.successRate(), "0.##"))));
        if (preview.costs().isEmpty()) {
            lore.add(msg("gui.preview.cost_free"));
        } else {
            lore.add(msg("gui.preview.cost_header"));
            for (AttemptCost cost : preview.costs()) {
                lore.add(msg("gui.preview.cost_line", Map.of("amount", cost.amount(), "name", cost.displayName())));
            }
        }
    }

    private String msg(String key) {
        return plugin.messageService().message(key);
    }

    private String msg(String key, Map<String, ?> replacements) {
        return plugin.messageService().message(key, replacements);
    }

    private ItemStack buildItem(String item, String name, List<String> lore) {
        return buildItem(null, item, name, lore);
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

    private static int parseMaterialIndex(String type) {
        try {
            return Integer.parseInt(type.substring("material_input_".length())) - 1;
        } catch (NumberFormatException _) {
            return -1;
        }
    }
}
