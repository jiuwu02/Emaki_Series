package emaki.jiuwu.craft.corelib.gui;

import emaki.jiuwu.craft.corelib.text.MiniMessages;
import emaki.jiuwu.craft.corelib.text.Texts;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class GuiSession implements InventoryHolder {

    private final Plugin owner;
    private final Player viewer;
    private final GuiTemplate template;
    private final GuiItemBuilder.ItemFactory itemFactory;
    private final GuiRenderer renderer;
    private final GuiSessionHandler handler;
    private final Map<String, Object> replacements = new LinkedHashMap<>();
    private Inventory inventory;

    GuiSession(Plugin owner,
               Player viewer,
               GuiTemplate template,
               Map<String, ?> replacements,
               GuiItemBuilder.ItemFactory itemFactory,
               GuiRenderer renderer,
               GuiSessionHandler handler) {
        this.owner = owner;
        this.viewer = viewer;
        this.template = template;
        this.itemFactory = itemFactory;
        this.renderer = renderer;
        this.handler = handler == null ? new GuiSessionHandler() {
        } : handler;
        if (replacements != null) {
            this.replacements.putAll(replacements);
        }
        String title = Texts.formatTemplate(template.title(), this.replacements);
        this.inventory = Bukkit.createInventory(this, template.rows() * 9, MiniMessages.parse(title));
    }

    public void open() {
        refresh();
        viewer.openInventory(inventory);
    }

    public void refresh() {
        for (GuiSlot slot : template.slots().values()) {
            for (int index = 0; index < slot.slots().size(); index++) {
                int inventorySlot = slot.slots().get(index);
                GuiTemplate.ResolvedSlot resolved = new GuiTemplate.ResolvedSlot(slot, inventorySlot, index);
                ItemStack rendered = renderer == null ? null : renderer.render(this, resolved);
                if (rendered == null) {
                    rendered = GuiItemBuilder.build(
                        slot.item(),
                        slot.components(),
                        1,
                        replacements,
                        itemFactory
                    );
                }
                inventory.setItem(inventorySlot, rendered);
            }
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

    public Map<String, Object> replacements() {
        return replacements;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
