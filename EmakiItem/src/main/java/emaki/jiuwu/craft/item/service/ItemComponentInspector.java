package emaki.jiuwu.craft.item.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.papermc.paper.datacomponent.DataComponentType;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.corelib.yaml.YamlFiles;

public final class ItemComponentInspector {

    private static final int INDENT_SIZE = 2;

    public String raw(ItemStack itemStack) {
        ItemMeta itemMeta = meta(itemStack);
        if (itemMeta == null) {
            return "[]";
        }
        try {
            String raw = itemMeta.getAsComponentString();
            return Texts.isBlank(raw) ? "[]" : raw;
        } catch (RuntimeException | LinkageError _) {
            return "[]";
        }
    }

    public Map<String, ComponentEntry> components(ItemStack itemStack) {
        Map<String, ComponentEntry> result = parse(raw(itemStack));
        paperDataComponents(itemStack).forEach((id, entry) -> result.putIfAbsent(id, entry));
        return result;
    }

    public List<String> ids(ItemStack itemStack) {
        return new ArrayList<>(components(itemStack).keySet());
    }

    public String idList(ItemStack itemStack) {
        List<String> ids = ids(itemStack);
        return ids.isEmpty() ? "" : String.join(",", ids);
    }

    public boolean contains(ItemStack itemStack, String componentId) {
        ComponentEntry entry = component(itemStack, componentId);
        return entry != null && !entry.removed();
    }

    public String value(ItemStack itemStack, String componentId) {
        ComponentEntry entry = component(itemStack, componentId);
        return entry == null || entry.removed() ? "" : entry.value();
    }

    public String prettyJson(ItemStack itemStack) {
        Map<String, Object> json = new LinkedHashMap<>();
        components(itemStack).forEach((id, entry) -> json.put(id, jsonValue(entry)));
        return JsonWriter.write(json);
    }

    public String prettyJson(ItemStack itemStack, String componentId) {
        String normalizedId = normalizeComponentId(componentId);
        ComponentEntry entry = component(itemStack, normalizedId);
        Map<String, Object> json = new LinkedHashMap<>();
        if (entry != null) {
            json.put(entry.id(), jsonValue(entry));
        }
        return JsonWriter.write(json);
    }

    public String prettyYaml(ItemStack itemStack) {
        return YamlFiles.dump(Map.of("components", yamlComponents(parse(raw(itemStack)).values()))).stripTrailing();
    }

    public String prettyYaml(ItemStack itemStack, String componentId) {
        String normalizedId = normalizeComponentId(componentId);
        ComponentEntry entry = parse(raw(itemStack)).get(normalizedId);
        Collection<ComponentEntry> entries = entry == null ? List.of() : List.of(entry);
        return YamlFiles.dump(Map.of("components", yamlComponents(entries))).stripTrailing();
    }

    public ComponentEntry component(ItemStack itemStack, String componentId) {
        String normalizedId = normalizeComponentId(componentId);
        if (Texts.isBlank(normalizedId)) {
            return null;
        }
        return components(itemStack).get(normalizedId);
    }

    public String normalizeComponentId(String componentId) {
        String normalized = Texts.toStringSafe(componentId).trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (normalized.startsWith("!")) {
            normalized = normalized.substring(1).trim();
        }
        if (normalized.isEmpty()) {
            return "";
        }
        return normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized;
    }

    private Map<String, ComponentEntry> parse(String rawComponents) {
        Map<String, ComponentEntry> result = new LinkedHashMap<>();
        String normalized = Texts.toStringSafe(rawComponents).trim();
        if (normalized.isEmpty() || "[]".equals(normalized)) {
            return result;
        }
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
            return result;
        }
        String body = normalized.substring(1, normalized.length() - 1).trim();
        if (body.isEmpty()) {
            return result;
        }
        for (String entryText : splitTopLevel(body, ',')) {
            ComponentEntry entry = parseEntry(entryText);
            if (entry != null) {
                result.put(entry.id(), entry);
            }
        }
        return result;
    }

    private ComponentEntry parseEntry(String rawEntry) {
        String entry = Texts.toStringSafe(rawEntry).trim();
        if (entry.isEmpty()) {
            return null;
        }
        boolean removed = entry.startsWith("!");
        if (removed) {
            entry = entry.substring(1).trim();
        }
        int assignmentIndex = findTopLevel(entry, '=');
        String rawId = assignmentIndex < 0 ? entry : entry.substring(0, assignmentIndex);
        String id = normalizeComponentId(rawId);
        if (Texts.isBlank(id)) {
            return null;
        }
        String value = assignmentIndex < 0 ? "" : entry.substring(assignmentIndex + 1).trim();
        return new ComponentEntry(id, value, removed);
    }

    private Object jsonValue(ComponentEntry entry) {
        if (entry.removed()) {
            return Map.of("removed", true);
        }
        return parseJsonishValue(entry.value());
    }

    private Map<String, Object> yamlComponents(Collection<ComponentEntry> entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> unset = new ArrayList<>();
        for (ComponentEntry entry : entries == null ? List.<ComponentEntry>of() : entries) {
            if (entry == null) {
                continue;
            }
            String yamlId = yamlComponentId(entry.id());
            if (entry.removed()) {
                unset.add(yamlId);
            } else {
                result.put(yamlId, parseJsonishValue(entry.value()));
            }
        }
        if (!unset.isEmpty()) {
            result.put("$unset", List.copyOf(unset));
        }
        return result;
    }

    private String yamlComponentId(String componentId) {
        String normalized = normalizeComponentId(componentId);
        return normalized.startsWith("minecraft:") ? normalized.substring("minecraft:".length()) : normalized;
    }

    private Object parseJsonishValue(String raw) {
        String value = Texts.toStringSafe(raw).trim();
        if (value.isEmpty()) {
            return true;
        }
        String unquoted = unquote(value);
        String candidate = unquoted == null ? value : unquoted;
        Object parsed = JsonParser.parse(candidate);
        if (parsed != JsonParser.INVALID) {
            return parsed;
        }
        if (unquoted != null) {
            return unquoted;
        }
        Object scalar = parseScalar(value);
        return scalar == JsonParser.INVALID ? value : scalar;
    }

    private static Object parseScalar(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("null".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            if (!value.isEmpty() && value.indexOf('.') < 0 && value.indexOf('e') < 0 && value.indexOf('E') < 0) {
                return Long.parseLong(value);
            }
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return JsonParser.INVALID;
        }
    }

    private String unquote(String value) {
        if (value.length() < 2) {
            return null;
        }
        char quote = value.charAt(0);
        if ((quote != '"' && quote != '\'') || value.charAt(value.length() - 1) != quote) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length() - 2);
        for (int index = 1; index < value.length() - 1; index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length() - 1) {
                builder.append(current);
                continue;
            }
            char next = value.charAt(++index);
            switch (next) {
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'u' -> {
                    if (index + 4 < value.length() - 1) {
                        try {
                            builder.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                            index += 4;
                        } catch (NumberFormatException ignored) {
                            builder.append('u');
                        }
                    } else {
                        builder.append('u');
                    }
                }
                default -> builder.append(next);
            }
        }
        return builder.toString();
    }

    private ItemMeta meta(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        return itemStack.getItemMeta();
    }

    private Map<String, ComponentEntry> paperDataComponents(ItemStack itemStack) {
        Map<String, ComponentEntry> result = new LinkedHashMap<>();
        if (itemStack == null || itemStack.getType().isAir()) {
            return result;
        }
        try {
            List<DataComponentType> types = new ArrayList<>(itemStack.getDataTypes());
            types.sort(java.util.Comparator.comparing(type -> type.getKey().toString()));
            for (DataComponentType type : types) {
                if (type == null) {
                    continue;
                }
                String id = normalizeComponentId(type.getKey().toString());
                if (Texts.isBlank(id)) {
                    continue;
                }
                boolean removed = !itemStack.hasData(type);
                result.put(id, new ComponentEntry(id, removed ? "" : paperDataValue(itemStack, type), removed));
            }
        } catch (RuntimeException | LinkageError _) {
            // Keep legacy getAsComponentString diagnostics as the stable fallback.
        }
        return result;
    }

    private String paperDataValue(ItemStack itemStack, DataComponentType type) {
        try {
            if (type instanceof DataComponentType.Valued<?> valued) {
                Object value = itemStack.getData(valued);
                return value == null ? "" : String.valueOf(value);
            }
        } catch (RuntimeException | LinkageError _) {
            return "";
        }
        return "";
    }

    private static List<String> splitTopLevel(String text, char delimiter) {
        List<String> entries = new ArrayList<>();
        int start = 0;
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '[' || current == '{' || current == '(') {
                depth++;
                continue;
            }
            if (current == ']' || current == '}' || current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (current == delimiter && depth == 0) {
                addEntry(entries, text.substring(start, index));
                start = index + 1;
            }
        }
        addEntry(entries, text.substring(start));
        return entries;
    }

    private static void addEntry(Collection<String> entries, String raw) {
        String entry = Texts.toStringSafe(raw).trim();
        if (!entry.isEmpty()) {
            entries.add(entry);
        }
    }

    private static int findTopLevel(String text, char target) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (current == '[' || current == '{' || current == '(') {
                depth++;
                continue;
            }
            if (current == ']' || current == '}' || current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (current == target && depth == 0) {
                return index;
            }
        }
        return -1;
    }

    public record ComponentEntry(String id, String value, boolean removed) {

        public ComponentEntry {
            id = id == null ? "" : id;
            value = value == null ? "" : value;
        }
    }

    private static final class JsonParser {

        private static final Object INVALID = new Object();

        private final String text;
        private int index;

        private JsonParser(String text) {
            this.text = text == null ? "" : text;
        }

        static Object parse(String text) {
            JsonParser parser = new JsonParser(text);
            Object value = parser.readValue();
            parser.skipWhitespace();
            return value == INVALID || parser.index != parser.text.length() ? INVALID : value;
        }

        private Object readValue() {
            skipWhitespace();
            if (index >= text.length()) {
                return INVALID;
            }
            char current = text.charAt(index);
            return switch (current) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"', '\'' -> readString();
                case 't', 'f' -> readBooleanOrBare();
                case 'n' -> readNullOrBare();
                default -> readNumberOrBare();
            };
        }

        private Object readObject() {
            Map<String, Object> result = new LinkedHashMap<>();
            index++;
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            while (index < text.length()) {
                Object key = readObjectKey();
                if (!(key instanceof String stringKey)) {
                    return INVALID;
                }
                skipWhitespace();
                if (!consume(':')) {
                    return INVALID;
                }
                Object value = readValue();
                if (value == INVALID) {
                    return INVALID;
                }
                result.put(stringKey, value);
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                if (!consume(',')) {
                    return INVALID;
                }
            }
            return INVALID;
        }

        private Object readArray() {
            List<Object> result = new ArrayList<>();
            index++;
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            while (index < text.length()) {
                Object value = readValue();
                if (value == INVALID) {
                    return INVALID;
                }
                result.add(value);
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                if (!consume(',')) {
                    return INVALID;
                }
            }
            return INVALID;
        }

        private Object readString() {
            if (index >= text.length()) {
                return INVALID;
            }
            char quote = text.charAt(index);
            if (quote != '"' && quote != '\'') {
                return INVALID;
            }
            index++;
            StringBuilder builder = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == quote) {
                    return builder.toString();
                }
                if (current != '\\') {
                    builder.append(current);
                    continue;
                }
                if (index >= text.length()) {
                    return INVALID;
                }
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"' -> builder.append('"');
                    case '\'' -> builder.append('\'');
                    case '\\' -> builder.append('\\');
                    case '/' -> builder.append('/');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'n' -> builder.append('\n');
                    case 'r' -> builder.append('\r');
                    case 't' -> builder.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            return INVALID;
                        }
                        try {
                            builder.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                        } catch (NumberFormatException ignored) {
                            return INVALID;
                        }
                        index += 4;
                    }
                    default -> builder.append(escaped);
                }
            }
            return INVALID;
        }

        private Object readObjectKey() {
            skipWhitespace();
            if (index >= text.length()) {
                return INVALID;
            }
            char current = text.charAt(index);
            if (current == '"' || current == '\'') {
                return readString();
            }
            int start = index;
            while (index < text.length()) {
                current = text.charAt(index);
                if (current == ':' || Character.isWhitespace(current)) {
                    break;
                }
                index++;
            }
            String key = text.substring(start, index).trim();
            return key.isEmpty() ? INVALID : key;
        }

        private Object readBooleanOrBare() {
            if (text.startsWith("true", index) && boundary(index + 4)) {
                index += 4;
                return true;
            }
            if (text.startsWith("false", index) && boundary(index + 5)) {
                index += 5;
                return false;
            }
            return readBareValue();
        }

        private Object readNullOrBare() {
            if (text.startsWith("null", index) && boundary(index + 4)) {
                index += 4;
                return null;
            }
            return readBareValue();
        }

        private Object readNumberOrBare() {
            int start = index;
            Object number = readNumber();
            if (number != INVALID && boundary(index)) {
                return number;
            }
            index = start;
            return readBareValue();
        }

        private Object readNumber() {
            int start = index;
            if (index < text.length() && text.charAt(index) == '-') {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            boolean floating = false;
            if (index < text.length() && text.charAt(index) == '.') {
                floating = true;
                index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                floating = true;
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (start == index || (start + 1 == index && text.charAt(start) == '-')) {
                return INVALID;
            }
            try {
                String raw = text.substring(start, index);
                if (floating) {
                    double parsed = Double.parseDouble(raw);
                    return Double.isFinite(parsed) ? parsed : INVALID;
                }
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                return INVALID;
            }
        }

        private Object readBareValue() {
            int start = index;
            int depth = 0;
            while (index < text.length()) {
                char current = text.charAt(index);
                if (depth == 0 && (current == ',' || current == '}' || current == ']')) {
                    break;
                }
                if (current == '[' || current == '{' || current == '(') {
                    depth++;
                } else if (current == ']' || current == '}' || current == ')') {
                    depth = Math.max(0, depth - 1);
                }
                index++;
            }
            String value = text.substring(start, index).trim();
            if (value.isEmpty()) {
                return INVALID;
            }
            Object scalar = parseScalar(value);
            return scalar == INVALID ? value : scalar;
        }

        private boolean boundary(int position) {
            return position >= text.length()
                    || Character.isWhitespace(text.charAt(position))
                    || text.charAt(position) == ','
                    || text.charAt(position) == '}'
                    || text.charAt(position) == ']';
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private boolean consume(char expected) {
            if (index < text.length() && text.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

    }

    private static final class JsonWriter {

        private JsonWriter() {
        }

        static String write(Object value) {
            StringBuilder builder = new StringBuilder();
            append(builder, value, 0);
            return builder.toString();
        }

        private static void append(StringBuilder builder, Object value, int indent) {
            if (value == null) {
                builder.append("null");
            } else if (value instanceof String string) {
                appendQuoted(builder, string);
            } else if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else if (value instanceof Map<?, ?> map) {
                appendMap(builder, map, indent);
            } else if (value instanceof Collection<?> collection) {
                appendCollection(builder, collection, indent);
            } else {
                appendQuoted(builder, String.valueOf(value));
            }
        }

        private static void appendMap(StringBuilder builder, Map<?, ?> map, int indent) {
            if (map.isEmpty()) {
                builder.append("{}");
                return;
            }
            builder.append("{\n");
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                indent(builder, indent + INDENT_SIZE);
                appendQuoted(builder, String.valueOf(entry.getKey()));
                builder.append(": ");
                append(builder, entry.getValue(), indent + INDENT_SIZE);
                if (++index < map.size()) {
                    builder.append(',');
                }
                builder.append('\n');
            }
            indent(builder, indent);
            builder.append('}');
        }

        private static void appendCollection(StringBuilder builder, Collection<?> collection, int indent) {
            if (collection.isEmpty()) {
                builder.append("[]");
                return;
            }
            builder.append("[\n");
            int index = 0;
            for (Object value : collection) {
                indent(builder, indent + INDENT_SIZE);
                append(builder, value, indent + INDENT_SIZE);
                if (++index < collection.size()) {
                    builder.append(',');
                }
                builder.append('\n');
            }
            indent(builder, indent);
            builder.append(']');
        }

        private static void appendQuoted(StringBuilder builder, String value) {
            builder.append('"');
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                switch (current) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\b' -> builder.append("\\b");
                    case '\f' -> builder.append("\\f");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (current < 0x20) {
                            builder.append(String.format("\\u%04x", (int) current));
                        } else {
                            builder.append(current);
                        }
                    }
                }
            }
            builder.append('"');
        }

        private static void indent(StringBuilder builder, int indent) {
            builder.append(" ".repeat(Math.max(0, indent)));
        }
    }
}
