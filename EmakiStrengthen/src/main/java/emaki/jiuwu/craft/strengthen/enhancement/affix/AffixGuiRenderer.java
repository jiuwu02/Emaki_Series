package emaki.jiuwu.craft.strengthen.enhancement.affix;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.api.item.ConfiguredItemDefinition;
import emaki.jiuwu.craft.corelib.api.math.Numbers;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.strengthen.EmakiStrengthenPlugin;
import emaki.jiuwu.craft.strengthen.api.model.AttemptCost;
import emaki.jiuwu.craft.strengthen.enhancement.EnhancementAttemptPreview;

/**
 * 词条强化 GUI 的槽位渲染。
 *
 * <p>ES-05 要求确认前能看到旧值、新值、容量、费用与概率，因此这些数字全部取自
 * {@link EnhancementAttemptPreview}——与确认时真正执行的解析同源，避免展示值和扣费值分叉。
 */
final class AffixGuiRenderer {

    private final EmakiStrengthenPlugin plugin;

    AffixGuiRenderer(EmakiStrengthenPlugin plugin) {
        this.plugin = plugin;
    }

    ItemStack renderSlot(AffixGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        String type = Texts.lower(slot.type());
        ItemStack dynamic = switch (type) {
            case "target_item" -> AffixGuiSession.cloneNonAir(state.targetItem());
            case "affix_select" -> buildAffixSelectItem(state, slot);
            case "preview_display" -> buildPreviewItem(state, slot);
            case "confirm" -> buildConfirmItem(state, slot);
            default -> {
                if (type.startsWith("material_input_")) {
                    int index = parseMaterialIndex(type);
                    yield index >= 0 ? AffixGuiSession.cloneNonAir(state.materialInput(index)) : null;
                }
                yield null;
            }
        };
        if (dynamic != null) {
            return dynamic;
        }
        return GuiItemBuilder.build(slot.itemDefinition(), Map.of(), plugin.coreLib().configuredItemService());
    }

    private ItemStack buildAffixSelectItem(AffixGuiSession state, GuiSlot slot) {
        List<String> lore = new ArrayList<>();
        if (state.targetItem() == null) {
            lore.add(msg("gui.affix.select.hint_no_target"));
            return buildItem(slot, msg("gui.affix.select.title"), lore, "affix_title", "affix_lines", null);
        }
        List<String> candidates = state.candidates();
        if (candidates.isEmpty()) {
            lore.add(msg("gui.affix.select.no_candidate"));
            return buildItem(slot, msg("gui.affix.select.title"), lore, "affix_title", "affix_lines", "BARRIER");
        }
        int index = candidates.indexOf(state.selectedAffix());
        lore.add(msg("gui.affix.select.current", Map.of(
                "affix", state.selectedAffix(),
                "index", String.valueOf(Math.max(0, index) + 1),
                "total", String.valueOf(candidates.size())
        )));
        lore.add(msg("gui.affix.select.next_hint"));
        lore.add(msg("gui.affix.select.previous_hint"));
        return buildItem(slot, msg("gui.affix.select.title"), lore, "affix_title", "affix_lines", null);
    }

    private ItemStack buildPreviewItem(AffixGuiSession state, GuiSlot slot) {
        List<String> lore = new ArrayList<>();
        if (state.targetItem() == null) {
            lore.add(msg("gui.affix.preview.hint_no_target"));
            return buildItem(slot, msg("gui.affix.preview.title"), lore, "preview_title", "preview_lines", null);
        }
        EnhancementAttemptPreview preview = state.preview();
        if (preview == null) {
            lore.add(msg("gui.affix.preview.unavailable"));
            return buildItem(slot, msg("gui.affix.preview.title"), lore, "preview_title", "preview_lines", null);
        }
        appendPreviewLines(lore, state, preview);
        if (!preview.valid() && Texts.isNotBlank(preview.errorKey())) {
            lore.add("<red>" + msg(preview.errorKey()) + "</red>");
        }
        return buildItem(slot, msg("gui.affix.preview.title"), lore, "preview_title", "preview_lines", null);
    }

    private ItemStack buildConfirmItem(AffixGuiSession state, GuiSlot slot) {
        EnhancementAttemptPreview preview = state.preview();
        if (preview == null || !preview.valid()) {
            return buildItem(slot, msg("gui.affix.confirm.ineligible_title"),
                    List.of(msg("gui.affix.confirm.ineligible_hint")),
                    "confirm_title", "confirm_lines", "BARRIER");
        }
        List<String> lore = new ArrayList<>();
        appendPreviewLines(lore, state, preview);
        lore.add(msg("gui.affix.confirm.hint"));
        return buildItem(slot, msg("gui.affix.confirm.title"), lore, "confirm_title", "confirm_lines", null);
    }

    private void appendPreviewLines(List<String> lore,
            AffixGuiSession state,
            EnhancementAttemptPreview preview) {
        lore.add(msg("gui.affix.preview.affix", Map.of("affix", state.selectedAffix())));
        lore.add(msg("gui.affix.preview.level", Map.of(
                "previous", String.valueOf(preview.previousLevel()),
                "resulting", String.valueOf(preview.resultingLevel())
        )));
        lore.add(msg("gui.affix.preview.capacity", Map.of(
                "used", String.valueOf(state.capacityUsed()),
                "max", String.valueOf(state.capacityMax()),
                "remaining", String.valueOf(Math.max(0, state.capacityMax() - state.capacityUsed()))
        )));
        lore.add(msg("gui.affix.preview.chance", Map.of(
                "base", Numbers.formatNumber(preview.baseRate() * 100D, "0.##"),
                "effective", Numbers.formatNumber(preview.effectiveRate() * 100D, "0.##")
        )));
        if (preview.pityTriggered()) {
            lore.add(msg("gui.affix.preview.pity_triggered", Map.of(
                    "counter", String.valueOf(preview.pityCounter()))));
        }
        if (preview.costs().isEmpty()) {
            lore.add(msg("gui.affix.preview.cost_free"));
        } else {
            lore.add(msg("gui.affix.preview.cost_header"));
            for (AttemptCost cost : preview.costs()) {
                lore.add(msg("gui.affix.preview.cost_line", Map.of(
                        "amount", String.valueOf(cost.amount()),
                        "name", cost.displayName()
                )));
            }
        }
        for (EnhancementAttemptPreview.MaterialRequirement requirement : preview.materials()) {
            lore.add(msg(requirement.satisfied()
                    ? "gui.affix.preview.material_ok"
                    : "gui.affix.preview.material_missing", Map.of(
                    "slot", requirement.slotId(),
                    "supplied", String.valueOf(requirement.supplied()),
                    "required", String.valueOf(requirement.required())
            )));
        }
    }

    private String msg(String key) {
        return plugin.messageService().message(key);
    }

    private String msg(String key, Map<String, ?> replacements) {
        return plugin.messageService().message(key, replacements);
    }

    private ItemStack buildItem(GuiSlot slot,
            String name,
            List<String> lore,
            String titleKey,
            String linesKey,
            String overrideSource) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        if (Texts.isNotBlank(titleKey)) {
            replacements.put(titleKey, name == null ? "" : name);
        }
        replacements.put(linesKey, lore == null ? List.of() : List.copyOf(lore));
        ConfiguredItemDefinition definition = slot == null
                ? new ConfiguredItemDefinition("BOOK", 1, Map.of())
                : slot.itemDefinition();
        if (Texts.isNotBlank(overrideSource)) {
            definition = definition.withSource(overrideSource);
        }
        return GuiItemBuilder.build(definition, replacements, plugin.coreLib().configuredItemService());
    }

    private static int parseMaterialIndex(String type) {
        try {
            return Integer.parseInt(type.substring("material_input_".length())) - 1;
        } catch (NumberFormatException _) {
            return -1;
        }
    }
}
