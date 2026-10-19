package emaki.jiuwu.craft.dungeon.challenge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.api.text.MiniMessages;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiClickContext;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiRenderer;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSessionHandler;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.loader.LanguageLoader;

public final class ChallengeGuiService {

    private static final String KEY_PREFIX_OPTION = "option:";
    private static final String KEY_CONFIRM = "confirm";
    private static final String KEY_CANCEL_WAIT = "cancel-wait";

    private final GuiService gui;
    private final LanguageLoader lang;
    private final org.bukkit.plugin.Plugin plugin;

    public ChallengeGuiService(org.bukkit.plugin.Plugin plugin, GuiService gui, LanguageLoader lang) {
        this.plugin = plugin;
        this.gui = gui;
        this.lang = lang;
    }

    public void openOptionSelect(Player viewer,
            String templateId,
            String titleKey,
            String optionKey,
            List<String> options,
            Consumer<String> onPick) {
        Map<String, GuiSlot> slots = new LinkedHashMap<>();
        int slotIndex = 0;
        for (String option : options) {
            slots.put(KEY_PREFIX_OPTION + option,
                    new GuiSlot(KEY_PREFIX_OPTION + option, List.of(slotIndex), "option", null, null));
            slotIndex++;
        }
        if (slots.isEmpty()) {
            return;
        }
        GuiTemplate template = new GuiTemplate(templateId, render(titleKey), 1, slots);
        GuiRenderer renderer = (session, resolvedSlot) -> renderOption(resolvedSlot, optionKey, options);
        GuiSessionHandler handler = new GuiSessionHandler() {
            @Override
            public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
                if (slot == null || slot.definition() == null) {
                    return;
                }
                String key = slot.definition().key();
                if (key.startsWith(KEY_PREFIX_OPTION)) {
                    onPick.accept(key.substring(KEY_PREFIX_OPTION.length()));
                    gui.close(viewer.getUniqueId());
                }
            }
        };
        gui.open(new GuiOpenRequest(plugin, viewer, template, Map.of(), renderer, handler));
    }

    public void openConfirm(Player viewer, Consumer<String> onAction) {
        Map<String, GuiSlot> slots = Map.of(
                KEY_CONFIRM, new GuiSlot(KEY_CONFIRM, List.of(4), "confirm", null, null)
        );
        GuiTemplate template = new GuiTemplate("edg_confirm", render("gui.confirm.title"), 1, slots);
        GuiRenderer renderer = (session, resolvedSlot) -> icon(Material.LIME_DYE,
                render("gui.confirm.name"), List.of(render("gui.confirm.lore")));
        GuiSessionHandler handler = new GuiSessionHandler() {
            @Override
            public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
                if (slot != null && slot.definition() != null && KEY_CONFIRM.equals(slot.definition().key())) {
                    onAction.accept(KEY_CONFIRM);
                }
            }
        };
        gui.open(new GuiOpenRequest(plugin, viewer, template, Map.of(), renderer, handler));
    }

    public void openWaitCancel(Player viewer, Consumer<String> onAction) {
        Map<String, GuiSlot> slots = Map.of(
                KEY_CANCEL_WAIT, new GuiSlot(KEY_CANCEL_WAIT, List.of(4), "cancel", null, null)
        );
        GuiTemplate template = new GuiTemplate("edg_wait", render("gui.wait.title"), 1, slots);
        GuiRenderer renderer = (session, resolvedSlot) -> icon(Material.BARRIER,
                render("gui.wait.cancel_name"), List.of(render("gui.wait.cancel_lore")));
        GuiSessionHandler handler = new GuiSessionHandler() {
            @Override
            public void onSlotClick(GuiSession session, GuiClickContext click, GuiTemplate.ResolvedSlot slot) {
                if (slot != null && slot.definition() != null && KEY_CANCEL_WAIT.equals(slot.definition().key())) {
                    onAction.accept(KEY_CANCEL_WAIT);
                }
            }
        };
        gui.open(new GuiOpenRequest(plugin, viewer, template, Map.of(), renderer, handler));
    }

    public void close(Player viewer) {
        gui.close(viewer.getUniqueId());
    }

    public void closeAll(List<Player> viewers) {
        for (Player viewer : viewers) {
            close(viewer);
        }
    }

    private ItemStack renderOption(GuiTemplate.ResolvedSlot resolvedSlot, String optionKey, List<String> options) {
        String key = resolvedSlot.definition().key();
        String option = key.startsWith(KEY_PREFIX_OPTION) ? key.substring(KEY_PREFIX_OPTION.length()) : "";
        Material material = options.indexOf(option) == 0 ? Material.IRON_INGOT : Material.GOLD_INGOT;
        Map<String, Object> replacements = Map.of("option", option);
        return icon(material,
                Texts.formatTemplate(render(optionKey + ".name"), replacements),
                List.of(Texts.formatTemplate(render(optionKey + ".lore"), replacements)));
    }

    private String render(String key) {
        String template = lang.getMessage(key);
        if (template == null || template.equals(key)) {
            return key;
        }
        return template;
    }

    private static ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(MiniMessages.parse(name));
            List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>(lore.size());
            for (String line : lore) {
                loreComponents.add(MiniMessages.parse(line));
            }
            meta.lore(loreComponents);
            item.setItemMeta(meta);
        }
        return item;
    }
}
