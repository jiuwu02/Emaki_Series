package emaki.jiuwu.craft.gem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.item.ItemSource;
import emaki.jiuwu.craft.corelib.item.ItemSourceUtil;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.gem.EmakiGemPlugin;
import emaki.jiuwu.craft.gem.model.GemDefinition;

final class GemUpgradeGuiRenderer {

    private static final String TEXT_PREFIX = "gui_text.upgrade.";
    private static final String COMMON_PREFIX = "gui_text.common.";

    private final EmakiGemPlugin plugin;

    GemUpgradeGuiRenderer(EmakiGemPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemStack renderSlot(GemUpgradeGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        return switch (Texts.lower(slot.type())) {
            case "target_gem" -> renderTargetGem(state, resolvedSlot);
            case "level_info" -> renderLevelInfo(state, slot);
            case "material_slot" -> renderMaterialSlot(state, resolvedSlot.slotIndex(), slot);
            case "preview" -> renderPreview(state, slot);
            case "success_rate" -> renderSuccessRate(state, slot);
            case "confirm" -> renderConfirm(state, slot);
            default -> GuiItemBuilder.build(slot.item(), slot.components(), 1, Map.of(),
                    (source, amount) -> plugin.coreItemSourceService() == null ? null : plugin.coreItemSourceService().createItem(source, amount));
        };
    }

    public void refreshGui(GemUpgradeGuiSession state) {
        if (state == null || state.guiSession() == null) {
            return;
        }
        state.guiSession().refresh();
    }

    private ItemStack renderTargetGem(GemUpgradeGuiSession state, GuiTemplate.ResolvedSlot resolvedSlot) {
        ItemStack targetGem = state.targetGem();
        if (targetGem == null) {
            String item = resolvedSlot == null || resolvedSlot.definition() == null || Texts.isBlank(resolvedSlot.definition().item())
                    ? Material.RED_STAINED_GLASS_PANE.name()
                    : resolvedSlot.definition().item();
            return buildConfiguredItem(resolvedSlot.definition(), item, text("target_empty_name", "<red>Place Gem</red>"), List.of(
                    text("target_empty_lore_1", "<gray>Place an uninlaid gem item here</gray>"),
                    common("click_take_back", "<gray>Supports placing from cursor and clicking to retrieve</gray>")
            ));
        }
        return targetGem.clone();
    }

    private ItemStack renderLevelInfo(GemUpgradeGuiSession state, GuiSlot guiSlot) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        List<String> lore = new ArrayList<>();
        if (!preview.eligible()) {
            lore.add(text("level_info_empty", "<gray>Please place an upgradeable gem first</gray>"));
            return buildConfiguredItem(guiSlot, Material.BOOK, text("level_info_name", "<gold>Level Info</gold>"), lore);
        }
        lore.add(text("gem_line", Map.of("gem", plugin.itemFactory().resolveGemDisplayName(preview.definition(), preview.instance().level())), "<gray>Gem: <yellow>%gem%</yellow></gray>"));
        lore.add(text("current_level", Map.of("level", preview.instance().level()), "<gray>Current level: <yellow>%level%</yellow></gray>"));
        lore.add(text("target_level", Map.of("level", preview.targetLevel()), "<gray>Target level: <gold>%level%</gold></gray>"));
        lore.add(text("max_level", Map.of("level", preview.definition().upgrade().maxLevel()), "<gray>Max level: <aqua>%level%</aqua></gray>"));
        return buildConfiguredItem(guiSlot, Material.BOOK, text("level_info_name", "<gold>Level Info</gold>"), lore);
    }

    private ItemStack renderMaterialSlot(GemUpgradeGuiSession state, int displayIndex, GuiSlot guiSlot) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        if (!preview.eligible() || displayIndex >= preview.upgradeLevel().materials().size()) {
            return buildConfiguredItem(guiSlot, Material.GRAY_STAINED_GLASS_PANE, text("material_slot_name", "<dark_gray>Material Slot</dark_gray>"), List.of(
                    text("material_slot_empty", "<dark_gray>No material preview</dark_gray>")
            ));
        }
        GemDefinition.MaterialCost material = preview.upgradeLevel().materials().get(displayIndex);
        String itemName = materialDisplayName(material.itemSource());
        ItemStack placedItem = state.materialItem(displayIndex);
        List<String> lore = new ArrayList<>();
        lore.add(text("material_line", Map.of("material", itemName), "<gray>Material: <yellow>%material%</yellow></gray>"));
        lore.add(text("material_amount", Map.of("amount", material.amount()), "<gray>Required amount: <gold>x%amount%</gold></gray>"));
        if (placedItem != null) {
            return placedItem.clone();
        }
        lore.add(text("material_place", "<gray>Please place matching item source material</gray>"));
        lore.add(text("material_scope", "<dark_gray>Only materials placed in this GUI are consumed</dark_gray>"));
        ItemStack previewItem = material.itemSource() == null || plugin.coreItemSourceService() == null
                ? null
                : plugin.coreItemSourceService().createItem(material.itemSource(), 1);
        if (previewItem != null) {
            return GuiItemBuilder.apply(previewItem, configuredComponents(guiSlot, text("material_name", "<aqua>Upgrade Material</aqua>"), lore), Map.of());
        }
        return buildConfiguredItem(guiSlot, Material.BLAZE_POWDER, text("material_name", "<aqua>Upgrade Material</aqua>"), lore);
    }

    private ItemStack renderPreview(GemUpgradeGuiSession state, GuiSlot guiSlot) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        List<String> lore = new ArrayList<>();
        if (!preview.eligible()) {
            lore.add(text("preview_empty_1", "<gray>Upgrade preview will be shown here</gray>"));
            lore.add(text("preview_empty_2", "<gray>Place a gem to view materials and result</gray>"));
            return buildConfiguredItem(guiSlot, Material.WRITABLE_BOOK, text("preview_name", "<gold>Upgrade Preview</gold>"), lore);
        }
        lore.add(text("result_name", Map.of("gem", plugin.itemFactory().resolveGemDisplayName(preview.definition(), preview.targetLevel())), "<gray>Result item name: <yellow>%gem%</yellow></gray>"));
        List<GemDefinition.CurrencyCost> currencies = !preview.upgradeLevel().currencies().isEmpty()
                ? preview.upgradeLevel().currencies()
                : preview.definition().upgrade().currencies();
        if (!currencies.isEmpty()) {
            lore.add(text("economy_cost", "<gray>Economy cost:</gray>"));
            int currentLevel = preview.instance().level();
            for (GemDefinition.CurrencyCost currency : currencies) {
                double amount = currency.resolveAmount(Map.of(
                        "level", preview.definition().level(),
                        "current_level", currentLevel,
                        "target_level", preview.targetLevel()
                ));
                lore.add(text("economy_line", Map.of("provider", currency.provider(), "amount", amount), "<gold> - %provider%: %amount%</gold>"));
            }
        }
        lore.add(text("material_required_hint", "<gray>All upgrade materials must be placed in material slots</gray>"));
        lore.add(text("confirm_update_hint", "<green>Confirming will directly update this gem item</green>"));
        return buildConfiguredItem(guiSlot, Material.WRITABLE_BOOK, text("preview_name", "<gold>Upgrade Preview</gold>"), lore);
    }

    private ItemStack renderSuccessRate(GemUpgradeGuiSession state, GuiSlot guiSlot) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        List<String> lore = new ArrayList<>();
        if (!preview.eligible()) {
            lore.add(text("success_rate_empty", "<gray>Success rate will be shown after placing a gem</gray>"));
            return buildConfiguredItem(guiSlot, Material.EXPERIENCE_BOTTLE, text("success_rate_name", "<gold>Success Rate</gold>"), lore);
        }
        double successRate = plugin.upgradeService().effectiveSuccessChance(preview.definition(), preview.targetLevel(), preview.upgradeLevel().successChance());
        lore.add(text("success_rate_line", Map.of("rate", successRate), "<gray>Base success rate: <green>%rate%%</green></gray>"));
        String failurePenalty = !preview.upgradeLevel().failurePenalty().isBlank()
                ? preview.upgradeLevel().failurePenalty()
                : !preview.definition().upgrade().failurePenalty().isBlank()
                        ? preview.definition().upgrade().failurePenalty()
                        : plugin.appConfig().upgrade().globalFailurePenalty();
        lore.add(text("failure_penalty", Map.of("penalty", failurePenalty), "<gray>Failure penalty: <yellow>%penalty%</yellow></gray>"));
        return buildConfiguredItem(guiSlot, Material.EXPERIENCE_BOTTLE, text("success_rate_name", "<gold>Success Rate</gold>"), lore);
    }

    private ItemStack renderConfirm(GemUpgradeGuiSession state, GuiSlot guiSlot) {
        GemUpgradeService.UpgradePreview preview = preview(state);
        if (!preview.eligible()) {
            return buildConfiguredItem(guiSlot, Material.BARRIER, text("confirm_disabled_name", "<red>Cannot Upgrade</red>"), List.of(
                    text("confirm_disabled_lore", "<gray>Please satisfy upgrade requirements first</gray>")
            ));
        }
        return buildConfiguredItem(guiSlot, Material.LIME_STAINED_GLASS_PANE, text("confirm_name", "<green>Confirm Upgrade</green>"), List.of(
                text("confirm_lore", "<gray>Click to consume GUI materials and try upgrading</gray>"),
                text("target_level", Map.of("level", preview.targetLevel()), "<gray>Target level: <gold>%level%</gold></gray>")
        ));
    }

    private GemUpgradeService.UpgradePreview preview(GemUpgradeGuiSession state) {
        return state == null || state.mutableTargetGem() == null
                ? GemUpgradeService.UpgradePreview.failure("command.upgrade.hold_gem")
                : plugin.upgradeService().preview(state.mutableTargetGem());
    }

    private String materialDisplayName(ItemSource source) {
        if (source == null) {
            return common("unknown_material", "Unknown material");
        }
        if (plugin.coreItemSourceService() != null) {
            String displayName = plugin.coreItemSourceService().displayName(source);
            if (Texts.isNotBlank(displayName)) {
                return displayName;
            }
        }
        String shorthand = ItemSourceUtil.toShorthand(source);
        return Texts.isBlank(shorthand) ? source.getIdentifier() : shorthand;
    }

    private ItemStack buildConfiguredItem(GuiSlot slot, Material material, String name, List<String> lore) {
        return buildConfiguredItem(slot, material.name(), name, lore);
    }

    private ItemStack buildConfiguredItem(GuiSlot slot, String item, String name, List<String> lore) {
        String itemId = Texts.isBlank(slot == null ? null : slot.item()) ? item : slot.item();
        return GuiItemBuilder.build(
                itemId,
                configuredComponents(slot, name, lore),
                1,
                Map.of(),
                (source, amount) -> plugin.coreItemSourceService() == null ? null : plugin.coreItemSourceService().createItem(source, amount)
        );
    }

    private ItemComponentParser.ItemComponents configuredComponents(GuiSlot slot, String name, List<String> lore) {
        ItemComponentParser.ItemComponents configured = slot == null ? null : slot.components();
        if (configured == null) {
            return new ItemComponentParser.ItemComponents(name, true, lore, null, null, Map.of(), List.of());
        }
        boolean hasDisplayNameConfig = configured.displayNameConfig() != null;
        boolean hasLoreConfig = configured.loreConfig() != null;
        String displayName = Texts.isBlank(configured.displayName()) && !hasDisplayNameConfig ? name : configured.displayName();
        boolean loreConfigured = configured.loreConfigured();
        return new ItemComponentParser.ItemComponents(
                displayName,
                true,
                loreConfigured ? configured.lore() : lore,
                configured.itemModel(),
                configured.customModelData(),
                configured.enchantments(),
                configured.hiddenComponents(),
                hasDisplayNameConfig ? configured.displayNameConfig() : null,
                hasLoreConfig && loreConfigured ? configured.loreConfig() : null
        );
    }

    private String text(String key, String fallback) {
        return text(key, Map.of(), fallback);
    }

    private String text(String key, Map<String, ?> placeholders, String fallback) {
        return resolve(TEXT_PREFIX + key, placeholders, fallback);
    }

    private String common(String key, String fallback) {
        return common(key, Map.of(), fallback);
    }

    private String common(String key, Map<String, ?> placeholders, String fallback) {
        return resolve(COMMON_PREFIX + key, placeholders, fallback);
    }

    private String resolve(String key, Map<String, ?> placeholders, String fallback) {
        String value = plugin.messageService().message(key, placeholders);
        return Texts.isBlank(value) || key.equals(value) ? fallback : value;
    }
}
