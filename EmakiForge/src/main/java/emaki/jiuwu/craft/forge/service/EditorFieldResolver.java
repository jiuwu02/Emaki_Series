package emaki.jiuwu.craft.forge.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import emaki.jiuwu.craft.corelib.text.Texts;
import emaki.jiuwu.craft.forge.EmakiForgePlugin;
import emaki.jiuwu.craft.forge.model.Blueprint;
import emaki.jiuwu.craft.forge.model.ForgeMaterial;
import emaki.jiuwu.craft.forge.model.Recipe;

final class EditorFieldResolver {

    record Entry(String label, String token, Object value, List<String> path) {}

    record FieldInput(String key, Object value) {}

    private final EmakiForgePlugin plugin;

    EditorFieldResolver(EmakiForgePlugin plugin) {
        this.plugin = plugin;
    }

    List<String> ids(EditableResourceType type) {
        List<String> out = switch (type) {
            case BLUEPRINT -> new ArrayList<>(plugin.blueprintLoader().all().keySet());
            case MATERIAL -> new ArrayList<>(plugin.materialLoader().all().keySet());
            case RECIPE -> new ArrayList<>(plugin.recipeLoader().all().keySet());
        };
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    boolean exists(EditableResourceType type, String id) {
        return switch (type) {
            case BLUEPRINT -> plugin.blueprintLoader().get(id) != null;
            case MATERIAL -> plugin.materialLoader().get(id) != null;
            case RECIPE -> plugin.recipeLoader().get(id) != null;
        };
    }

    List<Entry> entries(EditorSession session, Object node) {
        List<Entry> out = new ArrayList<>();
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = String.valueOf(entry.getKey());
                out.add(new Entry(key, key, entry.getValue(), append(session.currentPath(), key)));
            }
            out.sort((left, right) -> left.label().compareToIgnoreCase(right.label()));
        } else if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                out.add(new Entry("[" + i + "]", "#" + i, list.get(i), append(session.currentPath(), "#" + i)));
            }
        }
        return out;
    }

    String resourceName(EditableResourceType type, String id) {
        return switch (type) {
            case BLUEPRINT -> {
                Blueprint value = plugin.blueprintLoader().get(id);
                yield value == null ? id : Texts.stripMiniTags(value.displayName());
            }
            case MATERIAL -> {
                ForgeMaterial value = plugin.materialLoader().get(id);
                yield value == null ? id : Texts.stripMiniTags(value.displayName());
            }
            case RECIPE -> {
                Recipe value = plugin.recipeLoader().get(id);
                yield value == null ? id : Texts.stripMiniTags(value.displayName());
            }
        };
    }

    String sourceField(Object node) {
        if (!(node instanceof Map<?, ?> map)) {
            return null;
        }
        if (map.containsKey("item")) {
            return "item";
        }
        if (map.containsKey("output_item")) {
            return "output_item";
        }
        return null;
    }

    List<Integer> toIntList(Object node) {
        List<Integer> out = new ArrayList<>();
        if (!(node instanceof List<?> list)) {
            return out;
        }
        for (Object entry : list) {
            if (entry instanceof Number number) {
                out.add(number.intValue());
                continue;
            }
            try {
                out.add(Integer.parseInt(Texts.toStringSafe(entry)));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    String nodeType(Object value) {
        if (value instanceof Map<?, ?>) {
            return "map";
        }
        if (value instanceof List<?>) {
            return "list";
        }
        return "scalar";
    }

    String path(List<String> path) {
        return path == null || path.isEmpty() ? "<root>" : String.join(".", path);
    }

    String brief(String text) {
        String value = Texts.normalizeWhitespace(text);
        return value.length() <= 80 ? value : value.substring(0, 77) + "...";
    }

    String titlePart(String text) {
        String value = Texts.stripMiniTags(text);
        return value.length() <= 18 ? value : value.substring(0, 18);
    }

    List<String> append(List<String> path, String token) {
        List<String> out = new ArrayList<>(path == null ? List.of() : path);
        out.add(token);
        return out;
    }

    int parseIndex(String token) {
        try {
            return Integer.parseInt(token.substring(1));
        } catch (Exception ignored) {
            return -1;
        }
    }

    FieldInput parseFieldInput(String input) {
        if (Texts.isBlank(input)) {
            return null;
        }
        String raw = input.trim();
        int split = raw.indexOf('=');
        if (split < 0) {
            split = raw.indexOf(':');
        }
        String key = split < 0 ? raw : raw.substring(0, split).trim();
        String valueText = split < 0 ? "" : raw.substring(split + 1).trim();
        if (Texts.isBlank(key)) {
            return null;
        }
        Object value = switch (valueText) {
            case "{}" -> new LinkedHashMap<String, Object>();
            case "[]" -> new ArrayList<Object>();
            default -> valueText;
        };
        return new FieldInput(key, value);
    }
}
