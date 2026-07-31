package emaki.jiuwu.craft.level.service;

import java.util.List;
import java.util.Map;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.level.EmakiLevelPlugin;
import emaki.jiuwu.craft.level.config.LevelTypeConfig;

public final class LevelGuiService {

    static final String KEY_PAGE_INDEX = "page_index";
    static final String KEY_SELECTED_TYPE = "selected_type";
    static final String KEY_TYPE = "type";
    static final String KEY_TYPE_DISPLAY_NAME = "type_display_name";

    private static final String DEFAULT_TEMPLATE = "level_gui";

    private final EmakiLevelPlugin plugin;
    private final GuiService guiService;
    private final GuiTemplateLoader templateLoader;
    private final LevelGuiRenderer renderer;
    private final LevelGuiInteractionController interactionController;

    public LevelGuiService(EmakiLevelPlugin plugin, GuiService guiService, GuiTemplateLoader templateLoader) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.templateLoader = templateLoader;
        this.renderer = new LevelGuiRenderer(plugin, this);
        this.interactionController = new LevelGuiInteractionController(plugin, this);
    }

    public boolean open(Player player) {
        return open(player, plugin.appConfig().primaryType());
    }

    public boolean open(Player player, String typeId) {
        if (player == null) {
            return false;
        }
        if (!plugin.appConfig().guiEnabled()) {
            plugin.messages().send(player, "gui.open_failed");
            return false;
        }
        GuiTemplate template = resolveTemplate();
        if (template == null) {
            plugin.messages().send(player, "gui.open_failed");
            return false;
        }
        String selectedType = Texts.normalizeId(Texts.isBlank(typeId) ? plugin.appConfig().primaryType() : typeId);
        if (plugin.typeRegistry().type(selectedType).isEmpty()) {
            plugin.messages().send(player, "gui.level.unknown_type", Map.of("type", selectedType));
            return false;
        }
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                Map.of(
                        KEY_PAGE_INDEX, 0,
                        KEY_SELECTED_TYPE, selectedType,
                        KEY_TYPE, selectedType,
                        KEY_TYPE_DISPLAY_NAME, typeDisplayName(selectedType),
                        "page", 1,
                        "current_page", 1,
                        "total_pages", totalPages(template),
                        "type_count", types().size()
                ),
                renderer::render,
                interactionController
        ));
        return session != null;
    }

    public void refresh(GuiSession session) {
        if (session != null) {
            updatePageReplacements(session);
            session.refresh();
        }
    }

    public LevelTypeConfig typeAt(GuiSession session, GuiTemplate.ResolvedSlot slot) {
        if (session == null || slot == null || slot.definition() == null) {
            return null;
        }
        int pageSize = pageSize(session);
        if (pageSize <= 0) {
            return null;
        }
        int index = page(session) * pageSize + slotIndex(session, slot);
        List<LevelTypeConfig> types = types();
        return index < 0 || index >= types.size() ? null : types.get(index);
    }

    public List<LevelTypeConfig> types() {
        return plugin.typeRegistry().all().stream().toList();
    }

    public int page(GuiSession session) {
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

    public int totalPages(GuiSession session) {
        return totalPages(session == null ? null : session.template());
    }

    public int totalPages(GuiTemplate template) {
        int pageSize = pageSize(template);
        if (pageSize <= 0) {
            return 1;
        }
        return GuiPagination.totalPages(types().size(), pageSize);
    }

    public int pageSize(GuiSession session) {
        return pageSize(session == null ? null : session.template());
    }

    public int pageSize(GuiTemplate template) {
        if (template == null) {
            return 0;
        }
        int size = 0;
        for (GuiSlot slot : template.slotsByType("level_type")) {
            size += slot.slots().size();
        }
        return size;
    }

    public void setPage(GuiSession session, int page) {
        if (session == null) {
            return;
        }
        int totalPages = totalPages(session);
        session.putReplacement(KEY_PAGE_INDEX, Math.max(0, Math.min(totalPages - 1, page)));
        updatePageReplacements(session);
    }

    public void selectType(GuiSession session, String typeId) {
        if (session == null || Texts.isBlank(typeId)) {
            return;
        }
        session.putReplacement(KEY_SELECTED_TYPE, Texts.normalizeId(typeId));
        session.putReplacement(KEY_TYPE, Texts.normalizeId(typeId));
        session.putReplacement(KEY_TYPE_DISPLAY_NAME, typeDisplayName(typeId));
    }

    public String selectedType(GuiSession session) {
        Object raw = session == null ? null : session.replacements().get(KEY_SELECTED_TYPE);
        String selected = Texts.normalizeId(Texts.toStringSafe(raw));
        return Texts.isBlank(selected) ? plugin.appConfig().primaryType() : selected;
    }

    private int slotIndex(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        int offset = 0;
        for (GuiSlot slot : session.template().slotsByType("level_type")) {
            if (slot.key().equals(resolved.definition().key())) {
                return offset + resolved.slotIndex();
            }
            offset += slot.slots().size();
        }
        return resolved.slotIndex();
    }

    private GuiTemplate resolveTemplate() {
        String configured = plugin.appConfig().defaultGuiTemplate();
        GuiTemplate template = Texts.isBlank(configured) ? null : templateLoader.get(configured);
        return template == null ? templateLoader.get(DEFAULT_TEMPLATE) : template;
    }

    private void updatePageReplacements(GuiSession session) {
        session.putReplacement("page", page(session) + 1);
        session.putReplacement("current_page", page(session) + 1);
        session.putReplacement("total_pages", totalPages(session));
        session.putReplacement("type_count", types().size());
        String selected = selectedType(session);
        session.putReplacement(KEY_TYPE, selected);
        session.putReplacement(KEY_TYPE_DISPLAY_NAME, typeDisplayName(selected));
    }

    private String typeDisplayName(String typeId) {
        return plugin.typeRegistry().type(typeId).map(LevelTypeConfig::displayName).orElse(typeId);
    }
}
