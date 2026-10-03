package emaki.jiuwu.craft.item.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;

public final class YamlTextDocument {

    private static final String RENDER_KEY = "renderkey";

    public record Node(List<String> path, int start, int end, int indent) {

        public int length() {
            return Math.max(0, end - start);
        }
    }

    private final List<String> lines;
    private Map<String, Object> root;
    private Map<List<String>, Node> nodes;
    private Map<List<String>, List<Node>> sequenceItems;

    private YamlTextDocument(List<String> lines) {
        this.lines = new ArrayList<>(lines);
        rebuild();
    }

    public static YamlTextDocument parse(String text) {
        List<String> parsed = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            for (String line : text.split("\n", -1)) {
                parsed.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
            }
        }
        return new YamlTextDocument(parsed);
    }

    public String text() {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < lines.size(); index++) {
            builder.append(lines.get(index));
            if (index + 1 < lines.size()) {
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    public List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    public Map<String, Object> root() {
        return root;
    }

    public Object value(String... path) {
        return descend(root, path, 0);
    }

    public boolean has(String... path) {
        return nodes.containsKey(pathList(path)) || value(path) != null;
    }

    public List<Object> sequence(String... path) {
        Object value = value(path);
        return value instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    }

    public int sequenceSize(String... listPath) {
        List<Node> items = sequenceItems.get(pathList(listPath));
        return items == null ? 0 : items.size();
    }

    public void set(Object value, String... path) {
        requirePath(path);
        for (int split = path.length - 2; split >= 0; split--) {
            if (!isIndex(path[split + 1])) {
                continue;
            }
            String[] listPath = new String[split + 1];
            System.arraycopy(path, 0, listPath, 0, split + 1);
            if (!(value(listPath) instanceof List<?>)) {
                continue;
            }
            int index = Integer.parseInt(path[split + 1]);
            String[] keyPath = new String[path.length - split - 2];
            System.arraycopy(path, split + 2, keyPath, 0, keyPath.length);
            List<Object> items = sequence(listPath);
            if (index < 0 || index >= items.size()) {
                throw new IndexOutOfBoundsException("Sequence item index out of range: " + index);
            }
            Object updated = keyPath.length == 0 ? value : deepPut(items.get(index), keyPath, value);
            setListItem(updated, index, listPath);
            return;
        }
        List<String> lookup = pathList(path);
        Node node = nodes.get(lookup);
        if (node != null) {
            replaceNode(node, value);
        } else {
            insertMissing(path, value);
        }
        rebuild();
    }

    public void remove(String... path) {
        for (int split = path.length - 2; split >= 0; split--) {
            if (!isIndex(path[split + 1])) {
                continue;
            }
            String[] listPath = new String[split + 1];
            System.arraycopy(path, 0, listPath, 0, split + 1);
            if (!(value(listPath) instanceof List<?>)) {
                continue;
            }
            removeListItem(Integer.parseInt(path[split + 1]), listPath);
            return;
        }
        Node node = nodes.get(pathList(path));
        if (node == null) {
            return;
        }
        removeLines(node.start(), node.end());
        rebuild();
    }

    public void setListItem(Object value, int index, String... listPath) {
        List<Node> items = sequenceItems.get(pathList(listPath));
        if (items == null || index < 0 || index >= items.size()) {
            throw new IndexOutOfBoundsException("Sequence item index out of range: " + index);
        }
        Node item = items.get(index);
        replaceRange(item.start(), item.end(), renderItem(value, item.indent()));
        rebuild();
    }

    public void appendListItem(Object value, String... listPath) {
        List<String> lookup = pathList(listPath);
        Node listNode = nodes.get(lookup);
        List<Node> items = sequenceItems.get(lookup);
        if (listNode == null) {
            set(new ArrayList<>(List.of(value)), listPath);
            return;
        }
        if (items == null || items.isEmpty()) {
            List<Object> replacement = new ArrayList<>(sequence(listPath));
            replacement.add(value);
            replaceNode(listNode, replacement);
            rebuild();
            return;
        }
        Node last = items.get(items.size() - 1);
        insertLines(last.end(), renderItem(value, last.indent()));
        rebuild();
    }

    public void removeListItem(int index, String... listPath) {
        List<String> lookup = pathList(listPath);
        List<Node> items = sequenceItems.get(lookup);
        if (items == null || index < 0 || index >= items.size()) {
            throw new IndexOutOfBoundsException("Sequence item index out of range: " + index);
        }
        removeLines(items.get(index).start(), items.get(index).end());
        rebuild();
        List<Node> remaining = sequenceItems.get(lookup);
        if (remaining == null || remaining.isEmpty()) {
            Node refreshed = nodes.get(lookup);
            if (refreshed != null) {
                replaceNode(refreshed, new ArrayList<>());
                rebuild();
            }
        }
    }

    public void moveListItem(int from, int to, String... listPath) {
        List<String> lookup = pathList(listPath);
        List<Node> items = sequenceItems.get(lookup);
        if (items == null || from < 0 || from >= items.size() || to < 0 || to >= items.size()) {
            throw new IndexOutOfBoundsException("Sequence item index out of range: " + from + "/" + to);
        }
        if (from == to) {
            return;
        }
        List<Object> values = sequence(listPath);
        Object moved = values.remove(from);
        values.add(to, moved);
        Node listNode = nodes.get(lookup);
        if (listNode != null) {
            replaceNode(listNode, values);
            rebuild();
        }
    }

    private void insertMissing(String[] path, Object value) {
        for (int depth = path.length - 1; depth >= 0; depth--) {
            String[] ancestor = new String[depth + 1];
            System.arraycopy(path, 0, ancestor, 0, depth + 1);
            Node node = nodes.get(pathList(ancestor));
            if (node == null) {
                continue;
            }
            String[] remaining = new String[path.length - depth - 1];
            System.arraycopy(path, depth + 1, remaining, 0, remaining.length);
            Object merged = deepPut(value(ancestor), remaining, value);
            if (node.length() > 1 && remaining.length > 0) {
                insertLines(node.end(), renderBlock(remaining[0], childOf(merged, remaining), node.indent() + 2));
            } else {
                replaceNode(node, merged);
            }
            return;
        }
        Object merged = deepPut(null, path, value);
        insertLines(lines.size(), renderBlock(path[0], childOf(merged, path), 0));
    }

    private static Object childOf(Object merged, String[] path) {
        return merged instanceof Map<?, ?> map ? map.get(path[0]) : merged;
    }

    private static Object deepPut(Object current, String[] remaining, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (current instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        if (remaining.length == 0) {
            return value;
        }
        String head = remaining[0];
        String[] tail = new String[remaining.length - 1];
        System.arraycopy(remaining, 1, tail, 0, tail.length);
        result.put(head, deepPut(result.get(head), tail, value));
        return result;
    }

    private void replaceNode(Node node, Object value) {
        List<String> path = node.path();
        replaceRange(node.start(), node.end(), renderBlock(path.get(path.size() - 1), value, node.indent()));
    }

    private List<String> renderBlock(String key, Object value, int indent) {
        List<String> source = dumpLines(Map.of(RENDER_KEY, value));
        List<String> result = new ArrayList<>(source.size());
        String first = source.isEmpty() ? RENDER_KEY + ":" : source.get(0);
        String suffix;
        if (first.startsWith(RENDER_KEY + ":")) {
            suffix = first.substring(RENDER_KEY.length());
        } else if (first.equals(RENDER_KEY)) {
            suffix = ":";
        } else {
            suffix = ": " + first;
        }
        result.add(spaces(indent) + quoteKey(key) + suffix);
        for (int index = 1; index < source.size(); index++) {
            String line = source.get(index);
            result.add(line.isEmpty() ? "" : spaces(indent) + line);
        }
        return result;
    }

    private List<String> renderItem(Object value, int indent) {
        List<String> source = dumpLines(Map.of(RENDER_KEY, List.of(value)));
        List<String> body = source.size() <= 1 ? List.of("-") : source.subList(1, source.size());
        int shift = Integer.MAX_VALUE;
        for (String line : body) {
            if (!line.isBlank()) {
                shift = Math.min(shift, leadingSpaces(line));
            }
        }
        if (shift == Integer.MAX_VALUE) {
            shift = 0;
        }
        List<String> result = new ArrayList<>(body.size());
        for (String line : body) {
            result.add(line.isEmpty() ? "" : spaces(indent) + line.substring(Math.min(shift, line.length())));
        }
        return result;
    }

    private static List<String> dumpLines(Map<String, Object> payload) {
        String dumped = YamlFiles.dump(payload);
        List<String> source = new ArrayList<>();
        for (String line : dumped.split("\n", -1)) {
            source.add(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
        }
        while (!source.isEmpty() && source.get(source.size() - 1).isEmpty()) {
            source.remove(source.size() - 1);
        }
        return source;
    }

    private void replaceRange(int start, int end, List<String> replacement) {
        for (int index = end - 1; index >= start; index--) {
            lines.remove(index);
        }
        lines.addAll(start, replacement);
    }

    private void insertLines(int index, List<String> addition) {
        lines.addAll(index, addition);
    }

    private void removeLines(int start, int end) {
        for (int index = end - 1; index >= start; index--) {
            lines.remove(index);
        }
    }

    private void rebuild() {
        Map<String, Object> parsed;
        try {
            parsed = new LinkedHashMap<>(YamlFiles.load(text()).asMap());
        } catch (RuntimeException exception) {
            parsed = new LinkedHashMap<>();
        }
        root = parsed;
        Map<List<String>, Node> parsedNodes = new LinkedHashMap<>();
        Map<List<String>, List<Node>> parsedSequences = new LinkedHashMap<>();
        scan(parsedNodes, parsedSequences);
        nodes = parsedNodes;
        sequenceItems = parsedSequences;
    }

    private void scan(Map<List<String>, Node> parsedNodes, Map<List<String>, List<Node>> parsedSequences) {
        List<List<String>> stackPath = new ArrayList<>();
        List<Integer> stackIndent = new ArrayList<>();
        List<Integer> stackStart = new ArrayList<>();
        List<Boolean> stackSequence = new ArrayList<>();
        List<Boolean> stackValued = new ArrayList<>();
        int itemCounter = 0;
        for (int index = 0; index < lines.size(); index++) {
            String raw = lines.get(index);
            if (isIgnorable(raw)) {
                continue;
            }
            int indent = leadingSpaces(raw);
            String trimmed = raw.trim();
            boolean sequence = isSequenceItem(trimmed);
            while (!stackPath.isEmpty()) {
                int topIndent = stackIndent.get(stackIndent.size() - 1);
                boolean topSequence = stackSequence.get(stackSequence.size() - 1);
                if (topIndent > indent) {
                    closeNode(stackPath, stackIndent, stackStart, stackSequence, stackValued, parsedNodes, index);
                    continue;
                }
                if (topIndent == indent && (sequence == topSequence || !sequence)) {
                    closeNode(stackPath, stackIndent, stackStart, stackSequence, stackValued, parsedNodes, index);
                    continue;
                }
                break;
            }
            List<String> parentPath = stackPath.isEmpty()
                    ? List.of()
                    : stackPath.get(stackPath.size() - 1);
            if (sequence) {
                List<String> itemPath = new ArrayList<>(parentPath);
                itemPath.add("item" + itemCounter);
                itemCounter++;
                parsedSequences.computeIfAbsent(parentPath, ignored -> new ArrayList<>())
                        .add(new Node(parentPath, index, index + 1, indent));
                stackPath.add(itemPath);
                stackIndent.add(indent);
                stackStart.add(index);
                stackSequence.add(Boolean.TRUE);
                stackValued.add(Boolean.TRUE);
                continue;
            }
            String[] pair = splitKey(trimmed);
            if (pair == null) {
                continue;
            }
            List<String> nodePath = new ArrayList<>(parentPath);
            nodePath.add(pair[0]);
            parsedNodes.put(nodePath, new Node(nodePath, index, index + 1, indent));
            stackPath.add(nodePath);
            stackIndent.add(indent);
            stackStart.add(index);
            stackSequence.add(Boolean.FALSE);
            stackValued.add(pair[1] != null);
        }
        while (!stackPath.isEmpty()) {
            closeNode(stackPath, stackIndent, stackStart, stackSequence, stackValued, parsedNodes, lines.size());
        }
        for (Map.Entry<List<String>, List<Node>> entry : parsedSequences.entrySet()) {
            List<Node> items = entry.getValue();
            for (int i = 0; i < items.size(); i++) {
                Node item = items.get(i);
                int rawEnd = i + 1 < items.size() ? items.get(i + 1).start() : lines.size();
                items.set(i, new Node(item.path(), item.start(), trimTrailing(item.start(), rawEnd), item.indent()));
            }
        }
    }

    private void closeNode(List<List<String>> stackPath,
            List<Integer> stackIndent,
            List<Integer> stackStart,
            List<Boolean> stackSequence,
            List<Boolean> stackValued,
            Map<List<String>, Node> parsedNodes,
            int end) {
        List<String> path = stackPath.remove(stackPath.size() - 1);
        stackIndent.remove(stackIndent.size() - 1);
        int start = stackStart.remove(stackStart.size() - 1);
        boolean sequence = stackSequence.remove(stackSequence.size() - 1);
        stackValued.remove(stackValued.size() - 1);
        if (sequence) {
            return;
        }
        Node previous = parsedNodes.get(path);
        if (previous != null && previous.start() == start) {
            parsedNodes.put(path, new Node(previous.path(), start, trimTrailing(start, end), previous.indent()));
        }
    }

    private int trimTrailing(int start, int end) {
        int result = end;
        while (result > start && isIgnorable(lines.get(result - 1))) {
            result--;
        }
        return result;
    }

    private static boolean isIgnorable(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("---") || trimmed.startsWith("...");
    }

    private static boolean isSequenceItem(String trimmed) {
        return trimmed.equals("-") || trimmed.startsWith("- ");
    }

    private static String[] splitKey(String trimmed) {
        boolean inSingle = false;
        boolean inDouble = false;
        int depth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (inSingle) {
                if (current == '\'') {
                    inSingle = false;
                }
                continue;
            }
            if (inDouble) {
                if (current == '\\') {
                    i++;
                } else if (current == '"') {
                    inDouble = false;
                }
                continue;
            }
            if (current == '\'') {
                inSingle = true;
            } else if (current == '"') {
                inDouble = true;
            } else if (current == '[' || current == '{') {
                depth++;
            } else if (current == ']' || current == '}') {
                depth--;
            } else if (current == ':' && depth == 0
                    && (i + 1 >= trimmed.length() || trimmed.charAt(i + 1) == ' ')) {
                String key = unquote(trimmed.substring(0, i).trim());
                String value = trimmed.substring(i + 1).trim();
                return new String[]{ key, value.isEmpty() ? null : value };
            }
        }
        return null;
    }

    private static String unquote(String key) {
        if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
            return key.substring(1, key.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (key.length() >= 2 && key.startsWith("'") && key.endsWith("'")) {
            return key.substring(1, key.length() - 1).replace("''", "'");
        }
        return key;
    }

    private static String quoteKey(String key) {
        if (!key.isEmpty() && key.matches("[A-Za-z0-9_$\\-./:]+") && !key.endsWith(":")) {
            return key;
        }
        return "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Object descend(Object current, String[] path, int index) {
        if (index >= path.length) {
            return current;
        }
        if (current instanceof List<?> list) {
            if (!isIndex(path[index])) {
                return null;
            }
            int position = Integer.parseInt(path[index]);
            return descend(position >= 0 && position < list.size() ? list.get(position) : null, path, index + 1);
        }
        if (!(current instanceof Map<?, ?> map)) {
            return null;
        }
        return descend(map.get(path[index]), path, index + 1);
    }

    private static boolean isIndex(String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> pathList(String[] path) {
        List<String> result = new ArrayList<>(path.length);
        for (String segment : path) {
            result.add(segment);
        }
        return result;
    }

    private static int leadingSpaces(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static String spaces(int count) {
        return " ".repeat(Math.max(0, count));
    }

    private static void requirePath(String[] path) {
        if (path == null || path.length == 0) {
            throw new IllegalArgumentException("Path must not be empty");
        }
    }
}
