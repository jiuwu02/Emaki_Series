package emaki.jiuwu.craft.corelib.gui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.expression.ExpressionEngine;
import emaki.jiuwu.craft.corelib.item.ConfiguredItemService;
import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import net.kyori.adventure.text.Component;

public final class GuiSession implements InventoryHolder {

    private final Plugin owner;
    private final Player viewer;
    private final GuiTemplate template;
    private final GuiItemBuilder.ItemFactory itemFactory;
    private final ConfiguredItemService configuredItemService;
    private final GuiRenderer renderer;
    private final GuiSessionHandler handler;
    private final GuiBackend backend;
    private final GuiSessionRegistry registry;
    private final Map<String, Object> replacements = new LinkedHashMap<>();
    private final String plainTitle;
    private final Component titleComponent;
    private Inventory inventory;
    private long lastClickAt;

    GuiSession(Plugin owner,
            Player viewer,
            GuiTemplate template,
            Map<String, ?> replacements,
            GuiItemBuilder.ItemFactory itemFactory,
            ConfiguredItemService configuredItemService,
            GuiRenderer renderer,
            GuiSessionHandler handler,
            GuiBackend backend,
            GuiSessionRegistry registry) {
        this.owner = owner;
        this.viewer = viewer;
        this.template = template;
        this.itemFactory = itemFactory;
        this.configuredItemService = configuredItemService;
        this.renderer = renderer;
        this.handler = handler == null ? new GuiSessionHandler() {
        } : handler;
        this.backend = backend == null ? new BukkitGuiBackend() : backend;
        this.registry = registry;
        if (replacements != null) {
            this.replacements.putAll(replacements);
        }
        this.titleComponent = MiniMessages.parse(resolveTitle(template, this.replacements));
        this.plainTitle = MiniMessages.plain(this.titleComponent);
        this.inventory = createInventory(template, this.titleComponent);
    }

    /**
     * 判断本次点击是否满足最小间隔。满足时记录时间戳并返回 {@code true}；
     * 间隔不足时返回 {@code false}，调用方应丢弃该次点击回调。
     * {@code intervalMs} 小于等于 0 表示不限制。
     */
    boolean tryConsumeClick(long intervalMs) {
        if (intervalMs <= 0L) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (lastClickAt != 0L && now - lastClickAt < intervalMs) {
            return false;
        }
        lastClickAt = now;
        return true;
    }

    private Inventory createInventory(GuiTemplate template, Component titleComponent) {
        InventoryType type = template.inventoryType();
        if (template.isChest()) {
            return Bukkit.createInventory(this, template.rows() * 9, titleComponent);
        }
        if (!isCreatable(type)) {
            return Bukkit.createInventory(this, Math.max(9, template.slotCount()), titleComponent);
        }
        return Bukkit.createInventory(this, type, titleComponent);
    }

    private static boolean isCreatable(InventoryType type) {
        if (type == null || type == InventoryType.CHEST) {
            return false;
        }
        return switch (type) {
            case CRAFTING, CREATIVE, PLAYER, MERCHANT -> false;
            default -> type.getDefaultSize() > 0;
        };
    }

    private static String resolveTitle(GuiTemplate template, Map<String, ?> replacements) {
        if (template == null) {
            return "";
        }
        Map<String, ?> safeReplacements = replacements == null ? Map.of() : replacements;
        if (template.titleConfig() != null) {
            return ExpressionEngine.evaluateStringConfig(template.titleConfig(), safeReplacements);
        }
        return Texts.formatTemplate(template.title(), safeReplacements);
    }

    public void open() {
        backend.open(this, renderSlots());
    }

    public void refresh() {
        backend.applySlots(this, renderSlots());
    }

    public Map<Integer, ItemStack> renderSlots() {
        Map<Integer, ItemStack> renderedSlots = new LinkedHashMap<>();
        for (GuiSlot slot : template.slots().values()) {
            for (int index = 0; index < slot.slots().size(); index++) {
                int inventorySlot = slot.slots().get(index);
                GuiTemplate.ResolvedSlot resolved = new GuiTemplate.ResolvedSlot(slot, inventorySlot, index);
                ItemStack rendered = renderer == null ? null : renderer.render(this, resolved);
                if (rendered == null) {
                    rendered = configuredItemService == null
                            ? GuiItemBuilder.build(
                                    slot.item(),
                                    slot.components(),
                                    1,
                                    replacements,
                                    itemFactory
                            )
                            : GuiItemBuilder.build(slot.itemDefinition(), replacements, configuredItemService);
                }
                renderedSlots.put(inventorySlot, rendered);
            }
        }
        return renderedSlots;
    }

    public void applyRenderedSlots(Map<Integer, ItemStack> renderedSlots) {
        if (renderedSlots == null) {
            return;
        }
        inventory.clear();
        for (Map.Entry<Integer, ItemStack> entry : renderedSlots.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
    }

    public void replaceReplacements(Map<String, ?> values) {
        replacements.clear();
        if (values != null) {
            replacements.putAll(values);
        }
    }

    public void putReplacement(String key, Object value) {
        if (key != null) {
            replacements.put(key, value);
        }
    }

    public Plugin owner() {
        return owner;
    }

    public Player viewer() {
        return viewer;
    }

    public GuiTemplate template() {
        return template;
    }

    public GuiSessionHandler handler() {
        return handler;
    }

    public GuiBackend backend() {
        return backend;
    }

    public GuiSessionRegistry registry() {
        return registry;
    }

    public String title() {
        return plainTitle;
    }

    public String plainTitle() {
        return plainTitle;
    }

    public Component titleComponent() {
        return titleComponent;
    }

    public Map<String, Object> replacements() {
        return Collections.unmodifiableMap(replacements);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
