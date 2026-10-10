package emaki.jiuwu.craft.accessory.editor;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import emaki.jiuwu.craft.accessory.loader.AccessoryPageLoader;
import emaki.jiuwu.craft.accessory.loader.AccessorySetLoader;
import emaki.jiuwu.craft.corelib.api.text.Texts;
import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.editor.EditorValidator;
import emaki.jiuwu.craft.corelib.editor.YamlTextDocument;

final class AccessoryEditorValidators {

    private AccessoryEditorValidators() {
    }

    static EditorValidator parts() {
        return (candidate, docId, source) -> {
            if (!parsable(candidate)) {
                return "unparseable_yaml";
            }
            Object raw = candidate.root().get("parts");
            if (!(raw instanceof Map<?, ?> parts)) {
                return "parts_missing";
            }
            for (Map.Entry<?, ?> entry : parts.entrySet()) {
                Object value = entry.getValue();
                if (!(value instanceof Map<?, ?> part)) {
                    return "part_entry_invalid";
                }
                Object count = part.get("count");
                if (count != null && !validCount(count)) {
                    return "part_count_invalid";
                }
                if (isComplex(part.get("display_name")) || isComplex(part.get("icon"))) {
                    return "part_field_invalid";
                }
                Object durability = part.get("durability_deduction");
                if (durability != null && !(durability instanceof Boolean)) {
                    return "part_durability_invalid";
                }
            }
            return null;
        };
    }

    static EditorValidator sets(AccessorySetLoader loader) {
        return (candidate, docId, source) -> {
            if (!parsable(candidate)) {
                return "unparseable_yaml";
            }
            String actual = normalize(candidate.value("id"));
            if (actual.isEmpty()) {
                return "set_id_required";
            }
            if (!docId.isEmpty() && !docId.equals(actual)) {
                return "id_changed";
            }
            Object rawPieces = candidate.value("pieces");
            if (!(rawPieces instanceof Map<?, ?> pieces) || pieces.isEmpty()) {
                return "set_pieces_required";
            }
            for (Object value : pieces.values()) {
                if (!(value instanceof Map<?, ?> piece) || normalize(piece.get("item")).isEmpty()) {
                    return "set_piece_item_required";
                }
            }
            String effectsProblem = findBadEffects(candidate.root());
            if (effectsProblem != null) {
                return effectsProblem;
            }
            return parseWith(candidate, source, section -> loader.parseCandidate(source.toFile(), section));
        };
    }

    static EditorValidator pages(AccessoryPageLoader loader, File sourceFile) {
        String expected = readId(sourceFile, "page_id");
        return (candidate, docId, source) -> {
            if (!parsable(candidate)) {
                return "unparseable_yaml";
            }
            String actual = normalize(candidate.value("page_id"));
            if (actual.isEmpty()) {
                return "page_id_required";
            }
            if (!expected.isEmpty() && !expected.equals(actual)) {
                return "id_changed";
            }
            return parseWith(candidate, source, section -> loader.parseCandidate(sourceFile, section));
        };
    }

    private static String parseWith(YamlTextDocument candidate, Path source,
            Function<YamlSection, Object> parse) {
        try {
            YamlSection section = YamlFiles.load(candidate.text());
            if (section == null) {
                return "unparseable_yaml";
            }
            return parse.apply(section) == null ? "definition_parse_failed" : null;
        } catch (RuntimeException failure) {
            return "unparseable_yaml";
        }
    }

    private static String findBadEffects(Object node) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object value = entry.getValue();
                if ("effects".equals(Texts.toStringSafe(entry.getKey())) && value instanceof List<?> list) {
                    for (Object element : list) {
                        if (!(element instanceof Map<?, ?>)) {
                            return "effects_entry_invalid";
                        }
                    }
                    continue;
                }
                String child = findBadEffects(value);
                if (child != null) {
                    return child;
                }
            }
            return null;
        }
        if (node instanceof List<?> list) {
            for (Object element : list) {
                String child = findBadEffects(element);
                if (child != null) {
                    return child;
                }
            }
        }
        return null;
    }

    private static String readId(File file, String key) {
        try {
            YamlSection section = YamlFiles.load(file);
            if (section == null) {
                return "";
            }
            return normalize(section.getString(key, ""));
        } catch (RuntimeException failure) {
            return "";
        }
    }

    private static boolean parsable(YamlTextDocument candidate) {
        try {
            return YamlFiles.load(candidate.text()) != null;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean validCount(Object count) {
        if (!(count instanceof Number number)) {
            return false;
        }
        double value = number.doubleValue();
        return value >= 1.0d && value == Math.floor(value);
    }

    private static boolean isComplex(Object value) {
        return value instanceof Map<?, ?> || value instanceof List<?>;
    }

    private static String normalize(Object value) {
        return Texts.normalizeId(Texts.toStringSafe(value));
    }
}
