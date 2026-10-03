package emaki.jiuwu.craft.item.editor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.entity.Player;

public final class ItemEditorSession {

    private final Player player;
    private final String itemId;
    private final String packId;
    private final int returnPage;
    private final ItemDefinitionDocument document;
    private final Deque<String> menuStack = new ArrayDeque<>();
    private final Map<String, String> context = new LinkedHashMap<>();
    private String currentMenu;
    private int page;

    public ItemEditorSession(Player player,
            String itemId,
            String packId,
            int returnPage,
            ItemDefinitionDocument document,
            String homeMenuId) {
        this.player = player;
        this.itemId = itemId;
        this.packId = packId;
        this.returnPage = returnPage;
        this.document = document;
        this.currentMenu = homeMenuId;
    }

    public Player player() {
        return player;
    }

    public String itemId() {
        return itemId;
    }

    public String packId() {
        return packId;
    }

    public int returnPage() {
        return returnPage;
    }

    public ItemDefinitionDocument document() {
        return document;
    }

    public YamlTextDocument draft() {
        return document.view();
    }

    public String currentMenu() {
        return currentMenu;
    }

    public String parentMenu() {
        return menuStack.isEmpty() ? null : menuStack.peek();
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public void push(String menuId) {
        if (currentMenu != null) {
            menuStack.push(currentMenu);
        }
        currentMenu = menuId;
        page = 0;
    }

    public boolean back() {
        if (menuStack.isEmpty()) {
            return false;
        }
        currentMenu = menuStack.pop();
        page = 0;
        return true;
    }

    public void home(String homeMenuId) {
        menuStack.clear();
        currentMenu = homeMenuId;
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
