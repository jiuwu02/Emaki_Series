package emaki.jiuwu.craft.item.editor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.item.EmakiItemPlugin;

public final class ItemEditorGuiService {

    public static final String KEY_CURRENT_PAGE = "current_page";
    public static final String KEY_TOTAL_PAGES = "total_pages";
    public static final String KEY_MENU_TITLE = "menu_title";
    public static final String KEY_ENTRY_COUNT = "entry_count";
    public static final String TYPE_FIELD_ENTRY = "field_entry";
    public static final String TYPE_PREVIEW = "preview";
    public static final String TYPE_FILE_PATH = "file_path";
    public static final String TYPE_BACK_PARENT = "back_parent";
    public static final String TYPE_BACK_HOME = "back_home";
    public static final String TYPE_CONFIRM = "confirm";
    public static final String TYPE_PAGE_PREV = "page_prev";
    public static final String TYPE_PAGE_NEXT = "page_next";
    public static final String TYPE_PAGE_INFO = "page_info";
    public static final String TYPE_CLOSE = "close";

    public static final String TYPE_GET_ITEM = "get_item";
    public static final String TYPE_DELETE_ITEM = "delete_item";
    public static final String TYPE_BACK = "back";

    private final EmakiItemPlugin plugin;
    private final ItemEditService editService;
    private final ItemEditorRenderer renderer;
    private final ItemEditorInput input;
    private final ItemEditorInteractionController interactionController;
    private final Map<UUID, ItemEditorSession> sessions = new ConcurrentHashMap<>();

    public ItemEditorGuiService(EmakiItemPlugin plugin,
            ItemEditService editService,
            emaki.jiuwu.craft.corelib.chat.ChatInputService chatInput) {
        this.plugin = plugin;
        this.editService = editService;
        this.renderer = new ItemEditorRenderer(plugin);
        this.input = new ItemEditorInput(plugin, chatInput);
        this.interactionController = new ItemEditorInteractionController(plugin, this, editService);
    }

    public ItemEditorRenderer renderer() {
        return renderer;
    }

    public ItemEditorInput input() {
        return input;
    }

    public EmakiItemPlugin plugin() {
        return plugin;
    }

    public ItemEditService editService() {
        return editService;
    }

    public ItemEditorSession session(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public ItemEditorSession open(Player player, String itemId, String packId, int returnPage) {
        try {
            ItemDefinitionDocument document = editService.open(player, plugin.itemLoader().fileOf(itemId).toPath());
            ItemEditorSession session = new ItemEditorSession(
                    player, document.itemId(), packId, returnPage, document, ItemEditorMenus.HOME);
            sessions.put(player.getUniqueId(), session);
            render(session, ItemEditorMenus.HOME);
            return session;
        } catch (IOException | RuntimeException failure) {
            plugin.getLogger().warning("Could not open EmakiItem editor for " + itemId + ": " + failure);
            return null;
        }
    }

    public void openMenu(ItemEditorSession session, String menuId) {
        session.push(menuId);
        render(session, menuId);
    }

    public void goHome(ItemEditorSession session) {
        session.home(ItemEditorMenus.HOME);
        render(session, ItemEditorMenus.HOME);
    }

    public void returnToBrowser(ItemEditorSession session) {
        String packId = session.packId();
        int page = session.returnPage();
        close(session.player());
        if (!plugin.browserGuiService().openItemBrowser(session.player(), packId, page)) {
            plugin.messageService().send(session.player(), "browser.gui_open_failed");
        }
    }

    public void goBack(ItemEditorSession session) {
        if (session.back()) {
            render(session, session.currentMenu());
        } else {
            goHome(session);
        }
    }

    public void refresh(ItemEditorSession session) {
        render(session, session.currentMenu());
    }

    public void close(Player player) {
        ItemEditorSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            editService.close(player);
        }
    }

    public void closeAll() {
        sessions.clear();
        editService.flushAll();
        editService.closeAll();
    }

    public void createAndOpen(Player player, String packId, int returnPage, String rawId) {
        String itemId = Texts.normalizeId(rawId == null ? "" : rawId.trim());
        if (itemId.isEmpty() || !itemId.matches("[a-z0-9_]+")) {
            plugin.messageService().send(player, "editor.create.invalid_id");
            return;
        }
        if (plugin.itemLoader().get(itemId) != null) {
            plugin.messageService().send(player, "editor.create.duplicate_id", Map.of("id", itemId));
            return;
        }
        java.io.File directory = packId == null || packId.isBlank()
                ? plugin.itemLoader().rootDirectory()
                : new java.io.File(plugin.itemLoader().rootDirectory(), packId);
        java.io.File file = new java.io.File(directory, itemId + ".yml");
        if (file.exists()) {
            plugin.messageService().send(player, "editor.create.duplicate_id", Map.of("id", itemId));
            return;
        }
        try {
            java.nio.file.Files.createDirectories(directory.toPath());
            java.nio.file.Files.writeString(file.toPath(), skeleton(itemId),
                    java.nio.charset.StandardCharsets.UTF_8);
            plugin.reloadPluginStateAsync();
            plugin.messageService().send(player, "editor.create.success", Map.of("id", itemId));
            open(player, itemId, packId == null ? "" : packId, returnPage);
        } catch (java.io.IOException failure) {
            plugin.getLogger().warning("Could not create EmakiItem definition " + file + ": " + failure);
            plugin.messageService().send(player, "editor.create.failed");
        }
    }

    static String skeleton(String itemId) {
        return """
                id: "%s"

                item:
                  source: "minecraft-stone"
                  components:
                    custom_name: "<white>%s</white>"

                equip_slot: "all"

                update:
                  enabled: false
                  version: 1
                  triggers:
                    join: true
                    held_change: true
                    inventory_click: true
                    inventory_drag: true
                    pickup: true
                    interact: true
                    command: true

                effects: []

                set:
                  id: ""
                  piece: ""

                condition:
                  type: "all_of"
                  required_count: 0
                  invalid_as_failure: true
                  entries: []
                  on_pass:
                    actions: []
                  on_fail:
                    message: ""
                    block_output: false
                    actions: []

                repair:
                  enabled: false
                  materials: []
                  economy:
                    enabled: false
                    restore: "100%%"
                    currencies: []
                  disabled_display:
                    name_prefix: ""
                    lore_append: []
                  on_disabled: []
                  on_repaired: []

                actions: {}
                """.formatted(itemId, itemId);
    }

    private void render(ItemEditorSession session, String menuId) {
        GuiTemplate template = plugin.guiTemplateLoader().get(ItemEditorMenus.template(menuId));
        if (template == null) {
            plugin.getLogger().warning("Missing EmakiItem editor GUI template: " + ItemEditorMenus.template(menuId));
            return;
        }
        String menuTitle = plugin.messageService().message(ItemEditorMenus.titleKey(menuId));
        int totalEntries = renderer.entryCount(session, menuId);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(KEY_CURRENT_PAGE, session.page() + 1);
        replacements.put(KEY_TOTAL_PAGES, Math.max(1, (totalEntries + 20) / 21));
        replacements.put(KEY_MENU_TITLE, menuTitle);
        replacements.put(KEY_ENTRY_COUNT, totalEntries);
        GuiService gui = plugin.guiService();
        GuiSession guiSession = gui.open(new GuiOpenRequest(
                plugin,
                session.player(),
                template,
                replacements,
                (target, slot) -> renderer.render(session, menuId, slot),
                interactionController));
        if (guiSession != null) {
            guiSession.putReplacement(KEY_MENU_TITLE, menuTitle);
            guiSession.putReplacement(KEY_TOTAL_PAGES, Math.max(1, (totalEntries + 20) / 21));
        }
    }
}
