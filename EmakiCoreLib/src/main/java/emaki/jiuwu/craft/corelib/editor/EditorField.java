package emaki.jiuwu.craft.corelib.editor;

import java.util.List;

public record EditorField(String id,
        Kind kind,
        String labelKey,
        String displayName,
        String description,
        String icon,
        List<String> valueLines,
        List<String> options,
        int slotIndex,
        boolean enabled) {

    public enum Kind {
        TOGGLE,
        CYCLE,
        NUMBER,
        TEXT,
        NAVIGATE,
        LIST,
        COMMAND
    }

    public EditorField {
        valueLines = valueLines == null ? List.of() : List.copyOf(valueLines);
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static EditorField toggle(String id, String labelKey, boolean value) {
        return new EditorField(id, Kind.TOGGLE, labelKey, null, null, null,
                List.of(Boolean.toString(value)), List.of(), -1, true);
    }

    public static EditorField cycle(String id, String labelKey, String current, List<String> options) {
        return new EditorField(id, Kind.CYCLE, labelKey, null, null, null,
                List.of(current), options, -1, true);
    }

    public static EditorField number(String id, String labelKey, String current) {
        return new EditorField(id, Kind.NUMBER, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public static EditorField text(String id, String labelKey, String current) {
        return new EditorField(id, Kind.TEXT, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public static EditorField navigate(String id, String labelKey, String current) {
        return new EditorField(id, Kind.NAVIGATE, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public static EditorField list(String id, String labelKey, String current) {
        return new EditorField(id, Kind.LIST, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public static EditorField command(String id, String labelKey, String current) {
        return new EditorField(id, Kind.COMMAND, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public EditorField at(int index) {
        return new EditorField(id, kind, labelKey, displayName, description, icon,
                valueLines, options, index, enabled);
    }

    public EditorField withValue(String value) {
        return new EditorField(id, kind, labelKey, displayName, description, icon,
                List.of(value), options, slotIndex, enabled);
    }

    public EditorField withValueLines(List<String> lines) {
        return new EditorField(id, kind, labelKey, displayName, description, icon,
                lines, options, slotIndex, enabled);
    }

    public EditorField disabled() {
        return new EditorField(id, kind, labelKey, displayName, description, icon,
                valueLines, options, slotIndex, false);
    }
}
