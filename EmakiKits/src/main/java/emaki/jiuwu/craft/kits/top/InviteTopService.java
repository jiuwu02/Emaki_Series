package emaki.jiuwu.craft.kits.top;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiPagination;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiSlot;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.service.MessageService;
import emaki.jiuwu.craft.kits.EmakiKitsPlugin;
import emaki.jiuwu.craft.kits.service.KitService;

public final class InviteTopService {

    static final String KEY_PAGE_INDEX = "page_index";
    static final String TEMPLATE = "top_gui";

    private final EmakiKitsPlugin plugin;
    private final GuiService guiService;
    private final GuiTemplateLoader templates;
    private final MessageService messages;
    private final KitService service;
    private final Map<UUID, List<InviteEntry>> snapshots = new ConcurrentHashMap<>();
    private final InviteTopGuiRenderer renderer;
    private final InviteTopGuiInteractionController interactionController;

    public InviteTopService(EmakiKitsPlugin plugin, GuiService guiService, GuiTemplateLoader templates,
                            MessageService messages, KitService service) {
        this.plugin = plugin;
        this.guiService = guiService;
        this.templates = templates;
        this.messages = messages;
        this.service = service;
        this.renderer = new InviteTopGuiRenderer(this);
        this.interactionController = new InviteTopGuiInteractionController(this);
    }

    public void open(Player player, int page) {
        if (player == null) {
            return;
        }
        GuiTemplate template = templates.get(TEMPLATE);
        if (template == null) {
            messages.send(player, "top.open_failed");
            return;
        }
        List<InviteEntry> entries = ranked();
        snapshots.put(player.getUniqueId(), entries);
        int totalPages = totalPages(entries.size(), template);
        int safePage = Math.min(Math.max(page, 1), totalPages);
        GuiSession session = guiService.open(new GuiOpenRequest(
                plugin,
                player,
                template,
                Map.of(
                        KEY_PAGE_INDEX, safePage - 1,
                        "page", safePage,
                        "current_page", safePage,
                        "total_pages", totalPages,
                        "entry_count", entries.size(),
                        "top_title", messages.message("top.title")
                ),
                renderer,
                interactionController
        ));
        if (session == null) {
            snapshots.remove(player.getUniqueId());
            messages.send(player, "top.open_failed");
        }
    }

    public void closeAll() {
        snapshots.clear();
    }

    void refresh(GuiSession session) {
        if (session == null) {
            return;
        }
        UUID viewer = session.viewer() == null ? null : session.viewer().getUniqueId();
        if (viewer != null) {
            snapshots.put(viewer, ranked());
            int totalPages = totalPages(session);
            session.putReplacement(KEY_PAGE_INDEX, Math.max(0, Math.min(totalPages - 1, page(session))));
        }
        session.refresh();
    }

    void setPage(GuiSession session, int page) {
        if (session == null) {
            return;
        }
        int totalPages = totalPages(session);
        session.putReplacement(KEY_PAGE_INDEX, Math.max(0, Math.min(totalPages - 1, page)));
    }

    int page(GuiSession session) {
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

    int pageSize(GuiSession session) {
        return pageSize(session == null ? null : session.template());
    }

    int pageSize(GuiTemplate template) {
        return GuiPagination.pageSize(template, "top_entry");
    }

    int totalPages(GuiSession session) {
        UUID viewer = session == null || session.viewer() == null ? null : session.viewer().getUniqueId();
        List<InviteEntry> entries = snapshot(viewer);
        return totalPages(entries == null ? 0 : entries.size(), session == null ? null : session.template());
    }

    int totalPages(int count, GuiTemplate template) {
        return GuiPagination.totalPages(count, pageSize(template));
    }

    int entryIndex(GuiSession session, GuiTemplate.ResolvedSlot resolved) {
        int offset = 0;
        for (GuiSlot slot : session.template().slotsByType("top_entry")) {
            if (slot.key().equals(resolved.definition().key())) {
                return page(session) * Math.max(1, pageSize(session)) + offset + resolved.slotIndex();
            }
            offset += slot.slots().size();
        }
        return page(session) * Math.max(1, pageSize(session)) + resolved.slotIndex();
    }

    List<InviteEntry> snapshot(UUID viewer) {
        return viewer == null ? null : snapshots.get(viewer);
    }

    void onViewClosed(UUID viewer) {
        if (viewer != null) {
            snapshots.remove(viewer);
        }
    }

    MessageService messages() {
        return messages;
    }

    GuiService guiService() {
        return guiService;
    }

    static String playerName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return Texts.isBlank(name) ? uuid.toString().substring(0, 8) : name;
    }

    private List<InviteEntry> ranked() {
        Map<UUID, Long> counts = service.invitedCounts();
        List<InviteEntry> entries = new ArrayList<>();
        if (counts != null) {
            for (Map.Entry<UUID, Long> entry : counts.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0L) {
                    continue;
                }
                entries.add(new InviteEntry(entry.getKey(), entry.getValue()));
            }
        }
        entries.sort((left, right) -> {
            int byCount = Long.compare(right.count(), left.count());
            if (byCount != 0) {
                return byCount;
            }
            return left.uuid().toString().compareTo(right.uuid().toString());
        });
        return List.copyOf(entries);
    }

    record InviteEntry(UUID uuid, long count) {
    }
}
