package emaki.jiuwu.craft.corelib.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import emaki.jiuwu.craft.corelib.chat.ChatInputService;
import emaki.jiuwu.craft.corelib.editor.EditorSession.MenuRef;
import emaki.jiuwu.craft.corelib.gui.GuiOpenRequest;
import emaki.jiuwu.craft.corelib.gui.GuiService;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.gui.GuiTemplate;
import emaki.jiuwu.craft.corelib.gui.GuiTemplateLoader;
import emaki.jiuwu.craft.corelib.service.AbstractMessageService;

public final class EditorService {

    public static final String TEMPLATE_PAGE = "editor_page_gui";
    public static final String TEMPLATE_BROWSER = "editor_browser_gui";
    public static final String MENU_LIST_ENTRIES = "list_entries";

    public static final String KEY_CURRENT_PAGE = "current_page";
    public static final String KEY_TOTAL_PAGES = "total_pages";
    public static final String KEY_MENU_TITLE = "menu_title";
    public static final String KEY_ENTRY_COUNT = "entry_count";

    public static final String TYPE_FIELD_ENTRY = "field_entry";
    public static final String TYPE_BACK_PARENT = "back_parent";
    public static final String TYPE_BACK_HOME = "back_home";
    public static final String TYPE_CONFIRM = "confirm";
    public static final String TYPE_PAGE_PREV = "page_prev";
    public static final String TYPE_PAGE_NEXT = "page_next";
    public static final String TYPE_PAGE_INFO = "page_info";
    public static final String TYPE_CLOSE = "close";
    public static final String TYPE_BACK = "back";
    public static final String TYPE_FILLER = "filler";

    public static final String CONTEXT_LIST_PATH = "list_path";

    private final JavaPlugin plugin;
    private final GuiService gui;
    private final GuiTemplateLoader templates;
    private final AbstractMessageService messages;
    private final EditorModule module;
    private final EditorEditService editService;
    private final EditorRenderer renderer;
    private final EditorInput input;
    private final EditorInteractionController controller;
    private final EditorBrowser browser;
    private final Map<UUID, EditorSession> sessions = new ConcurrentHashMap<>();

    public EditorService(JavaPlugin plugin,
            GuiService gui,
            GuiTemplateLoader templates,
            AbstractMessageService messages,
            ChatInputService chatInput,
            EditorModule module) {
        this.plugin = plugin;
        this.gui = gui;
        this.templates = templates;
        this.messages = messages;
        this.module = module;
        this.editService = new EditorEditService(plugin);
        this.renderer = new EditorRenderer(messages, module);
        this.input = new EditorInput(plugin, chatInput, messages);
        this.controller = new EditorInteractionController(this);
        this.browser = new EditorBrowser(this);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public GuiService gui() {
        return gui;
    }

    public GuiTemplateLoader templates() {
        return templates;
    }

    public AbstractMessageService messages() {
        return messages;
    }

    public EditorModule module() {
        return module;
    }

    public EditorEditService editService() {
        return editService;
    }

    public EditorRenderer renderer() {
        return renderer;
    }

    public EditorInput input() {
        return input;
    }

    public EditorInteractionController controller() {
        return controller;
    }

    public EditorBrowser browser() {
        return browser;
    }

    public EditorSession session(Player player) {
        return player == null ? null : sessions.get(player.getUniqueId());
    }

    public EditorSession open(Player player, Path file, String label, EditorValidator validator,
            Object returnTarget) {
        try {
            EditorDocument document = editService.open(player, file, validator);
            EditorSession session = new EditorSession(player, this, document, returnTarget,
                    module.homeMenuId(), module.scopeForMenu(module.homeMenuId()));
            sessions.put(player.getUniqueId(), session);
            render(session, session.current());
            return session;
        } catch (IOException | RuntimeException failure) {
            messages.warning("console.editor_open_failed",
                    Map.of("item", label, "error", String.valueOf(failure)));
            return null;
        }
    }

    public void openMenu(EditorSession session, String menuId) {
        session.push(MenuRef.menu(menuId, module.scopeForMenu(menuId)));
        render(session, session.current());
    }

    public void push(EditorSession session, MenuRef ref) {
        session.push(ref);
        render(session, session.current());
    }

    public void goHome(EditorSession session) {
        session.home(module.homeMenuId(), module.scopeForMenu(module.homeMenuId()));
        render(session, session.current());
    }

    public void goBack(EditorSession session) {
        if (session.back()) {
            render(session, session.current());
        } else {
            goHome(session);
        }
    }

    public void refresh(EditorSession session) {
        render(session, session.current());
    }

    public void close(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            editService.close(player);
        }
    }

    public void closeAll() {
        sessions.clear();
        editService.flushAll();
        editService.closeAll();
    }

    public void flushAll() {
        editService.flushAll();
    }

    public void openBrowser(Player player) {
        close(player);
        browser.open(player);
    }

    public EditorDocument.SaveResult mutate(EditorSession session, EditorField field, String label, boolean reset,
            Consumer<YamlTextDocument> mutation) {
        return controller.mutate(session, field, label, reset, mutation);
    }

    public void resetValue(EditorSession session, EditorField field, String label, String... path) {
        controller.resetValue(session, field, label, path);
    }

    public void appendListLine(EditorSession session, EditorField field, String label, String listPath) {
        controller.appendListLine(session, field, label, listPath);
    }

    public void removeLastListLine(EditorSession session, EditorField field, String label, String listPath) {
        controller.removeLastListLine(session, field, label, listPath);
    }

    private void render(EditorSession session, MenuRef ref) {
        String templateId = module.templateId(ref.structural() ? null : ref.menuId());
        GuiTemplate template = templates.get(templateId);
        if (template == null) {
            messages.warning("console.editor_template_missing", Map.of("template", templateId));
            return;
        }
        String menuTitle = renderer.titleText(session, ref);
        int totalEntries = renderer.entryCount(session, ref);
        Map<String, Object> replacements = new LinkedHashMap<>();
        replacements.put(KEY_CURRENT_PAGE, session.page() + 1);
        replacements.put(KEY_TOTAL_PAGES,
                Math.max(1, (totalEntries + EditorRenderer.PAGE_SIZE - 1) / EditorRenderer.PAGE_SIZE));
        replacements.put(KEY_MENU_TITLE, menuTitle);
        replacements.put(KEY_ENTRY_COUNT, totalEntries);
        GuiSession guiSession = gui.open(new GuiOpenRequest(
                plugin,
                session.player(),
                template,
                replacements,
                (target, slot) -> renderer.render(session, ref, slot),
                controller));
        if (guiSession != null) {
            guiSession.putReplacement(KEY_MENU_TITLE, menuTitle);
            guiSession.putReplacement(KEY_TOTAL_PAGES,
                    Math.max(1, (totalEntries + EditorRenderer.PAGE_SIZE - 1) / EditorRenderer.PAGE_SIZE));
        }
    }
}
