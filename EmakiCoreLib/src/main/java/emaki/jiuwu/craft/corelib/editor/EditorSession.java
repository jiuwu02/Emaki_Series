package emaki.jiuwu.craft.corelib.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;

public final class EditorSession {

    public static final String SCOPE_DEFAULT = "";

    public record MenuRef(String menuId, String[] path, String scope) {

        public static MenuRef menu(String menuId, String scope) {
            return new MenuRef(menuId, null, scope == null ? SCOPE_DEFAULT : scope);
        }

        public static MenuRef structural(String[] path, String scope) {
            return new MenuRef(null, path == null ? new String[0] : path,
                    scope == null ? SCOPE_DEFAULT : scope);
        }

        public boolean structural() {
            return menuId == null;
        }
    }

    private final Player player;
    private final EditorService service;
    private final EditorDocument document;
    private final Object returnTarget;
    private final Map<String, EditorDocument> documents = new LinkedHashMap<>();
    private final Deque<MenuRef> menuStack = new ArrayDeque<>();
    private final Map<String, String> context = new LinkedHashMap<>();
    private MenuRef current;
    private int page;

    public EditorSession(Player player,
            EditorService service,
            EditorDocument document,
            Object returnTarget,
            String homeMenuId,
            String homeScope) {
        this.player = player;
        this.service = service;
        this.document = document;
        this.returnTarget = returnTarget;
        this.documents.put(SCOPE_DEFAULT, document);
        this.current = MenuRef.menu(homeMenuId, homeScope);
    }

    public Player player() {
        return player;
    }

    public EditorService service() {
        return service;
    }

    public Object returnTarget() {
        return returnTarget;
    }

    public EditorDocument document() {
        return document;
    }

    public EditorDocument document(String scope) {
        if (scope == null || scope.isEmpty()) {
            return document;
        }
        EditorDocument secondary = documents.get(scope);
        return secondary == null ? document : secondary;
    }

    public void attach(String scope, EditorDocument secondary) {
        if (scope == null || scope.isEmpty() || secondary == null) {
            return;
        }
        documents.put(scope, secondary);
    }

    public YamlTextDocument draft() {
        return document.view();
    }

    public YamlTextDocument draft(String scope) {
        return document(scope).view();
    }

    public MenuRef current() {
        return current;
    }

    public String currentMenuId() {
        return current == null ? null : current.menuId();
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public void push(MenuRef ref) {
        if (current != null) {
            menuStack.push(current);
        }
        current = ref;
        page = 0;
    }

    public boolean back() {
        if (menuStack.isEmpty()) {
            return false;
        }
        current = menuStack.pop();
        page = 0;
        return true;
    }

    public void home(String homeMenuId, String homeScope) {
        menuStack.clear();
        current = MenuRef.menu(homeMenuId, homeScope);
        page = 0;
    }

    public String context(String key) {
        return context.get(key);
    }

    public void putContext(String key, String value) {
        if (value == null) {
            context.remove(key);
        } else {
            context.put(key, value);
        }
    }
}
