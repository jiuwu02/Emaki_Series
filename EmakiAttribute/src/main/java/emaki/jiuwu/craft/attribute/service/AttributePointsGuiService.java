package emaki.jiuwu.craft.attribute.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.attribute.EmakiAttributePlugin;
import emaki.jiuwu.craft.attribute.model.AttributeDefinition;
import emaki.jiuwu.craft.attribute.model.ParentAttributeData;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiItemBuilder;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.gui.ItemComponentParser;
import emaki.jiuwu.craft.corelib.math.Numbers;
import emaki.jiuwu.craft.corelib.text.Texts;

public final class AttributePointsGuiService {

    private static final String DEFAULT_TEMPLATE = "attribute_points";
    private static final String KEY_PAGE_INDEX = "page_index";

    private final EmakiAttributePlugin plugin;
    private final GuiService guiService;
    private final GuiTemplateLoader templateLoader;
    private final Handler handler = new Handler();

    public AttributePointsGuiService(EmakiAttributePlugin plugin, GuiService guiService, GuiTemplateLoader templateLoader) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.templateLoader = templateLoader;
    }

    public boolean open(Player player) {
        if (player == null) {
            return false;
        }
        GuiTemplate template = templateLoader == null ? null : templateLoader.get(DEFAULT_TEMPLATE);
        if (template == null) {
            return false;
        }
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                replacements(player, template, 0),
                (source, amount) -> plugin.coreLib().itemSourceService().createItem(source, amount),
                this::render,
                handler
        ));
        return session != null;
    }

    public void refresh(GuiSession session) {
        if (session == null) {
            return;
        }
        session.replaceReplacements(replacements(session.viewer(), session.template(), page(session)));
        session.refresh();
    }

    private ItemStack render(GuiSession session, GuiTemplate.ResolvedSlot resolvedSlot) {
        if (session == null || resolvedSlot == null || resolvedSlot.definition() == null) {
            return null;
        }
        GuiSlot slot = resolvedSlot.definition();
        return switch (Texts.lower(slot.type())) {
            case "parent_attribute" -> renderParentAttribute(session, resolvedSlot, slot);
            case "summary" -> renderSummary(session, slot);
            default -> null;
        };
    }

    private ItemStack renderParentAttribute(GuiSession session, GuiTemplate.ResolvedSlot resolvedSlot, GuiSlot slot) {
        AttributeDefinition definition = attributeAt(session, resolvedSlot);
        if (definition == null) {
            return new ItemStack(Material.AIR);
        }
        ParentAttributeData data = plugin.attributeService().parentAttributeService().data(session.viewer());
        Map<String, Object> replacements = replacements(session.viewer(), session.template(), page(session));
        replacements.putAll(attributeReplacements(definition, data));
        return buildConfiguredItem(slot, "experience_bottle", "%display_name% <gray>Lv.%points%</gray>", defaultAttributeLore(), replacements);
    }

    private ItemStack renderSummary(GuiSession session, GuiSlot slot) {
        ParentAttributeData data = plugin.attributeService().parentAttributeService().data(session.viewer());
        Map<String, Object> replacements = replacements(session.viewer(), session.template(), page(session));
        replacements.put("available", data.availablePoints());
        replacements.put("reset", data.resetPoints());
        replacements.put("allocated", data.allocatedTotal());
        return buildConfiguredItem(slot, "book", "<aqua>属性点</aqua>", List.of(
                "<gray>可用点数: <yellow>%available%</yellow></gray>",
                "<gray>已分配: <white>%allocated%</white></gray>",
                "<gray>洗点次数: <gold>%reset%</gold></gray>"
        ), replacements);
    }

    private AttributeDefinition attributeAt(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        int pageSize = pageSize(session.template());
        if (pageSize <= 0) {
            return null;
        }
        int index = page(session) * pageSize + slotIndex(session, resolved);
        List<AttributeDefinition> attributes = attributes();
        return index < 0 || index >= attributes.size() ? null : attributes.get(index);
    }

    private List<AttributeDefinition> attributes() {
        return plugin.attributeService().parentAttributeService().parentAttributes();
    }

    private int page(GuiSession session) {
        Object raw = session == null ? null : session.replacements().get(KEY_PAGE_INDEX);
        if (raw instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(Texts.toStringSafe(raw)));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int totalPages(GuiTemplate template) {
        int pageSize = pageSize(template);
        if (pageSize <= 0) {
            return 1;
        }
        return GuiPagination.totalPages(attributes().size(), pageSize);
    }

    private int pageSize(GuiTemplate template) {
        return GuiPagination.pageSize(template, "parent_attribute");
    }

    private int slotIndex(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        int offset = 0;
        for (GuiSlot slot : session.template().slotsByType("parent_attribute")) {
            if (slot.key().equals(resolved.definition().key())) {
                return offset + resolved.slotIndex();
            }
            offset += slot.slots().size();
        }
        return resolved.slotIndex();
    }

    private void setPage(GuiSession session, int page) {
        int totalPages = totalPages(session.template());
        session.replaceReplacements(replacements(session.viewer(), session.template(), Math.max(0, Math.min(totalPages - 1, page))));
        session.refresh();
    }

    private Map<String, Object> replacements(Player player, GuiTemplate template, int page) {
        ParentAttributeData data = plugin.attributeService().parentAttributeService().data(player);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(KEY_PAGE_INDEX, page);
        replacements.put("player", player == null ? "" : player.getName());
        replacements.put("available", data == null ? 0 : data.availablePoints());
        replacements.put("reset", data == null ? 0 : data.resetPoints());
        replacements.put("allocated", data == null ? 0 : data.allocatedTotal());
        replacements.put("current_page", page + 1);
        replacements.put("total_pages", totalPages(template));
        replacements.put("attribute_count", attributes().size());
        return replacements;
    }

    private Map<String, Object> attributeReplacements(AttributeDefinition definition, ParentAttributeData data) {
        Map<String, Object> replacements = new LinkedHashMap<>();
        int points = data == null ? 0 : data.allocation(definition.id());
        replacements.put("attribute", definition.id());
        replacements.put("display_name", definition.displayName());
        replacements.put("points", points);
        replacements.put("description", definition.description());
        replacements.put("child_bonuses", formatChildBonuses(definition));
        return replacements;
    }

    private String formatChildBonuses(AttributeDefinition definition) {
        if (definition.childBonuses().isEmpty()) {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> entry : definition.childBonuses().entrySet()) {
            AttributeDefinition child = plugin.attributeRegistry().resolve(entry.getKey());
            String name = child == null ? entry.getKey() : child.displayName();
            parts.add(name + "+" + Numbers.formatNumber(entry.getValue(), "0.##"));
        }
        return String.join(", ", parts);
    }

    private List<String> defaultAttributeLore() {
        return List.of(
                "<gray>当前点数: <yellow>%points%</yellow></gray>",
                "<gray>每点加成: <white>%child_bonuses%</white></gray>",
                "",
                "<yellow>左键</yellow><gray>投入 1 点</gray>",
                "<yellow>右键</yellow><gray>投入 5 点</gray>"
        );
    }

    private ItemStack buildConfiguredItem(GuiSlot slot, String fallbackItem, String fallbackName, List<String> fallbackLore, Map<String, ?> replacements) {
        ItemComponentParser.ItemComponents fallbackComponents = new ItemComponentParser.ItemComponents(
                fallbackName,
                true,
                fallbackLore == null ? List.of() : fallbackLore,
                null,
                null,
                Map.of(),
                List.of()
        );
        ItemComponentParser.ItemComponents components = hasConfiguredComponents(slot) ? slot.components() : fallbackComponents;
        String item = Texts.isBlank(slot == null ? null : slot.item()) ? fallbackItem : slot.item();
        return GuiItemBuilder.build(
                Texts.isBlank(item) ? "barrier" : item,
                components,
                1,
                replacements == null ? Map.of() : replacements,
                (source, amount) -> plugin.coreLib().itemSourceService().createItem(source, amount)
        );
    }

    private boolean hasConfiguredComponents(GuiSlot slot) {
        if (slot == null || slot.components() == null) {
            return false;
        }
        ItemComponentParser.ItemComponents components = slot.components();
        return Texts.isNotBlank(components.displayName())
                || components.displayNameConfig() != null
                || components.loreConfigured()
                || Texts.isNotBlank(components.itemModel())
                || components.customModelData() != null
                || !components.enchantments().isEmpty()
                || !components.hiddenComponents().isEmpty();
    }

    private final class Handler implements GuiSessionHandler {

        @Override
        public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
            if (session == null || click == null || slot == null || slot.definition() == null) {
                return;
            }
            Player player = session.viewer();
            switch (Texts.lower(slot.definition().type())) {
                case "parent_attribute" -> {
                    AttributeDefinition definition = attributeAt(session, slot);
                    if (definition == null) {
                        return;
                    }
                    int amount = click.isRightClick() ? 5 : 1;
                    var result = plugin.attributeService().parentAttributeService().allocate(player, definition.id(), amount);
                    if (result == ParentAttributeService.AllocateResult.SUCCESS) {
                        plugin.messageService().send(player, "command.points.add_success", Map.of("player", player.getName(), "attribute", definition.displayName(), "amount", amount));
                    } else {
                        plugin.messageService().send(player, "command.points.add_failed", Map.of("reason", result.name().toLowerCase(java.util.Locale.ROOT)));
                    }
                    refresh(session);
                }
                case "reset" -> {
                    var result = plugin.attributeService().parentAttributeService().reset(player, true);
                    if (result == ParentAttributeService.ResetResult.SUCCESS) {
                        plugin.messageService().send(player, "command.points.reset_success", Map.of("player", player.getName()));
                    } else {
                        plugin.messageService().send(player, "command.points.reset_failed", Map.of("reason", result.name().toLowerCase(java.util.Locale.ROOT)));
                    }
                    refresh(session);
                }
                case "page_prev", "previous_page" -> {
                    if (page(session) > 0) {
                        setPage(session, page(session) - 1);
                    }
                }
                case "page_next", "next_page" -> {
                    if (page(session) < totalPages(session.template()) - 1) {
                        setPage(session, page(session) + 1);
                    }
                }
                case "close" -> player.closeInventory();
                default -> { }
            }
        }
    }
}
