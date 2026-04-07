package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import emaki.jiuwu.craft.corelib.config.ConfigNodes;
import emaki.jiuwu.craft.corelib.gui.GuiSession;
import emaki.jiuwu.craft.corelib.text.Texts;

final class EditorSession {

    enum Mode {
        INDEX,
        LIST,
        DOCUMENT,
        SLOT_GRID,
        CONFIRM_CLOSE
    }

    @FunctionalInterface
    interface ClickAction {

        void execute(EditorSession session, org.bukkit.event.inventory.InventoryClickEvent event);
    }

    record PendingInput(String prompt, long expiresAt, Consumer<String> submitHandler) {

        boolean expired() {
            return expiresAt > 0L && System.currentTimeMillis() > expiresAt;
        }
    }

    private final UUID playerId;
    private EditableResourceType resourceType;
    private String originalId;
    private Map<String, Object> rootData;
    private volatile Mode mode;
    private List<String> currentPath = new ArrayList<>();
    private volatile int page;
    private volatile GuiSession guiSession;
    private volatile boolean dirty;
    private volatile boolean closingByService;
    private volatile boolean suspendCloseHandling;
    private volatile PendingInput pendingInput;
    private final Map<Integer, ItemStack> renderedItems = new LinkedHashMap<>();
    private final Map<Integer, ClickAction> clickActions = new LinkedHashMap<>();

    EditorSession(Player player, EditableResourceType resourceType, String originalId, Map<String, Object> rootData, Mode mode) {
        this.playerId = player.getUniqueId();
        this.resourceType = resourceType;
        this.originalId = originalId;
        this.rootData = rootData == null ? null : castMap(copyValue(rootData));
        this.mode = mode == null ? Mode.INDEX : mode;
    }

    UUID playerId() {
        return playerId;
    }

    EditableResourceType resourceType() {
        return resourceType;
    }

    void setResourceType(EditableResourceType resourceType) {
        this.resourceType = resourceType;
    }

    String originalId() {
        return originalId;
    }

    void setOriginalId(String originalId) {
        this.originalId = originalId;
    }

    String currentId() {
        Object value = rootData == null ? null : rootData.get("id");
        return Texts.toStringSafe(value).trim();
    }

    Map<String, Object> rootData() {
        return rootData;
    }

    void setRootData(Map<String, Object> rootData) {
        this.rootData = rootData == null ? null : castMap(copyValue(rootData));
    }

    Mode mode() {
        return mode;
    }

    void setMode(Mode mode) {
        this.mode = mode == null ? Mode.INDEX : mode;
    }

    List<String> currentPath() {
        return List.copyOf(currentPath);
    }

    void setCurrentPath(List<String> currentPath) {
        this.currentPath = currentPath == null ? new ArrayList<>() : new ArrayList<>(currentPath);
    }

    int page() {
        return page;
    }

    void setPage(int page) {
        this.page = Math.max(0, page);
    }

    GuiSession guiSession() {
        return guiSession;
    }

    void setGuiSession(GuiSession guiSession) {
        this.guiSession = guiSession;
    }

    boolean dirty() {
        return dirty;
    }

    void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    boolean closingByService() {
        return closingByService;
    }

    void setClosingByService(boolean closingByService) {
        this.closingByService = closingByService;
    }

    boolean suspendCloseHandling() {
        return suspendCloseHandling;
    }

    void setSuspendCloseHandling(boolean suspendCloseHandling) {
        this.suspendCloseHandling = suspendCloseHandling;
    }

    PendingInput pendingInput() {
        return pendingInput;
    }

    void setPendingInput(PendingInput pendingInput) {
        this.pendingInput = pendingInput;
    }

    void clearPendingInput() {
        this.pendingInput = null;
    }

    Map<Integer, ItemStack> renderedItems() {
        return renderedItems;
    }

    Map<Integer, ClickAction> clickActions() {
        return clickActions;
    }

    boolean editingDocument() {
        return rootData != null && (mode == Mode.DOCUMENT || mode == Mode.SLOT_GRID || mode == Mode.CONFIRM_CLOSE);
    }

    Object currentNode() {
        return node(currentPath);
    }

    Object node(List<String> path) {
        Object current = rootData;
        if (path == null) {
            return current;
        }
        for (String segment : path) {
            if (segment == null) {
                return null;
            }
            if (segment.startsWith("#")) {
                if (!(current instanceof List<?> list)) {
                    return null;
                }
                int index = parseIndex(segment);
                if (index < 0 || index >= list.size()) {
                    return null;
                }
                current = list.get(index);
                continue;
            }
            current = ConfigNodes.get(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    void setNode(List<String> path, Object value) {
        if (rootData == null || path == null || path.isEmpty()) {
            return;
        }
        Object parent = node(path.subList(0, path.size() - 1));
        String leaf = path.get(path.size() - 1);
        if (leaf.startsWith("#") && parent instanceof List<?> rawList) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) rawList;
            int index = parseIndex(leaf);
            if (index >= 0 && index < list.size()) {
                list.set(index, copyValue(value));
                dirty = true;
            }
            return;
        }
        if (parent instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) rawMap;
            map.put(leaf, copyValue(value));
            dirty = true;
        }
    }

    void putMapValue(List<String> mapPath, String key, Object value) {
        Object node = node(mapPath);
        if (!(node instanceof Map<?, ?> rawMap) || Texts.isBlank(key)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        map.put(key, copyValue(value));
        dirty = true;
    }

    void removeMapValue(List<String> mapPath, String key) {
        Object node = node(mapPath);
        if (!(node instanceof Map<?, ?> rawMap) || Texts.isBlank(key)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) rawMap;
        map.remove(key);
        dirty = true;
    }

    void addListValue(List<String> listPath, Object value) {
        Object node = node(listPath);
        if (!(node instanceof List<?> rawList)) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) rawList;
        list.add(copyValue(value));
        dirty = true;
    }

    void removeListValue(List<String> listPath, int index) {
        Object node = node(listPath);
        if (!(node instanceof List<?> rawList)) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) rawList;
        if (index < 0 || index >= list.size()) {
            return;
        }
        list.remove(index);
        dirty = true;
    }

    void toggleSlotValue(List<String> listPath, int slot) {
        Object node = node(listPath);
        if (!(node instanceof List<?> rawList)) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) rawList;
        Integer existingIndex = null;
        for (int index = 0; index < list.size(); index++) {
            Object entry = list.get(index);
            if (entry instanceof Number number && number.intValue() == slot) {
                existingIndex = index;
                break;
            }
            if (Texts.toStringSafe(entry).equals(String.valueOf(slot))) {
                existingIndex = index;
                break;
            }
        }
        if (existingIndex != null) {
            list.remove(existingIndex.intValue());
        } else {
            list.add(slot);
        }
        dirty = true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                result.put(String.valueOf(entry.getKey()), copyValue(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object entry : list) {
                result.add(copyValue(entry));
            }
            return result;
        }
        return value;
    }

    private int parseIndex(String token) {
        try {
            return Integer.parseInt(token.substring(1));
        } catch (Exception ignored) {
            return -1;
        }
    }

    @Override
    public String toString() {
        return "EditorSession{"
                + "playerId=" + playerId
                + ", resourceType=" + resourceType
                + ", currentId=" + currentId()
                + ", mode=" + mode
                + ", path=" + currentPath
                + ", dirty=" + dirty
                + '}';
    }
}
