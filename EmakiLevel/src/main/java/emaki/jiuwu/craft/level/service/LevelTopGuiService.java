package emaki.jiuwu.craft.level.service;

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

public final class LevelTopGuiService {

    static final String KEY_PAGE_INDEX = "page_index";
    static final String KEY_TYPE = "type";
    static final String KEY_TYPE_DISPLAY_NAME = "type_display_name";

    private static final String TEMPLATE = "top_gui";

    private final EmakiLevelPlugin plugin;
    private final GuiService guiService;
    private final GuiTemplateLoader templateLoader;
    private final LevelTopGuiRenderer renderer;
    private final LevelTopGuiInteractionController interactionController;

    public LevelTopGuiService(EmakiLevelPlugin plugin, GuiService guiService, GuiTemplateLoader templateLoader) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.templateLoader = templateLoader;
        this.renderer = new LevelTopGuiRenderer(plugin, this);
        this.interactionController = new LevelTopGuiInteractionController(plugin, this);
    }

    public boolean open(Player player, String typeId) {
        if (player == null) {
            return false;
        }
        if (!plugin.appConfig().guiEnabled()) {
            plugin.messages().send(player, "gui.open_failed");
            return false;
        }
        String normalizedType = Texts.normalizeId(Texts.isBlank(typeId) ? plugin.appConfig().primaryType() : typeId);
        if (plugin.typeRegistry().type(normalizedType).isEmpty()) {
            plugin.messages().send(player, "gui.top.unknown_type", Map.of("type", normalizedType));
            return false;
        }
        GuiTemplate template = templateLoader.get(TEMPLATE);
        if (template == null) {
            plugin.messages().send(player, "gui.open_failed");
            return false;
        }
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                Map.of(
                        KEY_PAGE_INDEX, 0,
                        KEY_TYPE, normalizedType,
                        KEY_TYPE_DISPLAY_NAME, typeDisplayName(normalizedType),
                        "page", 1,
                        "current_page", 1,
                        "total_pages", totalPages(template, normalizedType),
                        "entry_count", plugin.topService().top(normalizedType, Integer.MAX_VALUE).size()
                ),
                (source, amount) -> plugin.coreLib().itemSourceService().createItem(source, amount),
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

    public String typeId(GuiSession session) {
        Object raw = session == null ? null : session.replacements().get(KEY_TYPE);
        String typeId = Texts.normalizeId(Texts.toStringSafe(raw));
        return Texts.isBlank(typeId) ? plugin.appConfig().primaryType() : typeId;
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

    public int pageSize(GuiSession session) {
        return pageSize(session == null ? null : session.template());
    }

    public int pageSize(GuiTemplate template) {
        if (template == null) {
            return 0;
        }
        int size = 0;
        for (GuiSlot slot : template.slotsByType("top_entry")) {
            size += slot.slots().size();
        }
        return size;
    }

    public int totalPages(GuiSession session) {
        return totalPages(session == null ? null : session.template(), typeId(session));
    }

    public int totalPages(GuiTemplate template, String typeId) {
        int pageSize = pageSize(template);
        if (pageSize <= 0) {
            return 1;
        }
        String resolvedType = Texts.isBlank(typeId) ? plugin.appConfig().primaryType() : typeId;
        int count = plugin.topService().top(resolvedType, Integer.MAX_VALUE).size();
        return GuiPagination.totalPages(count, pageSize);
    }

    public int entryIndex(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        int offset = 0;
        for (GuiSlot slot : session.template().slotsByType("top_entry")) {
            if (slot.key().equals(resolved.definition().key())) {
                return page(session) * Math.max(1, pageSize(session)) + offset + resolved.slotIndex();
            }
            offset += slot.slots().size();
        }
        return page(session) * Math.max(1, pageSize(session)) + resolved.slotIndex();
    }

    public void setPage(GuiSession session, int page) {
        if (session == null) {
            return;
        }
        int totalPages = totalPages(session);
        session.putReplacement(KEY_PAGE_INDEX, Math.max(0, Math.min(totalPages - 1, page)));
        updatePageReplacements(session);
    }

    private void updatePageReplacements(GuiSession session) {
        String typeId = typeId(session);
        session.putReplacement(KEY_TYPE, typeId);
        session.putReplacement(KEY_TYPE_DISPLAY_NAME, typeDisplayName(typeId));
        session.putReplacement("page", page(session) + 1);
        session.putReplacement("current_page", page(session) + 1);
        session.putReplacement("total_pages", totalPages(session));
    }

    private String typeDisplayName(String typeId) {
        return plugin.typeRegistry().type(typeId).map(LevelTypeConfig::displayName).orElse(typeId);
    }
}
