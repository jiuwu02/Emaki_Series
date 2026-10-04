package emaki.jiuwu.craft.item.editor;

import java.util.List;

public record ItemEditorField(String id,
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

    public ItemEditorField {
        valueLines = valueLines == null ? List.of() : List.copyOf(valueLines);
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static ItemEditorField toggle(String id, String labelKey, boolean value) {
        return new ItemEditorField(id, Kind.TOGGLE, labelKey, null, null, null,
                List.of(Boolean.toString(value)), List.of(), -1, true);
    }

    public static ItemEditorField cycle(String id, String labelKey, String current, List<String> options) {
        return new ItemEditorField(id, Kind.CYCLE, labelKey, null, null, null,
                List.of(current), options, -1, true);
    }

    public static ItemEditorField number(String id, String labelKey, String current) {
        return new ItemEditorField(id, Kind.NUMBER, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public static ItemEditorField text(String id, String labelKey, String current) {
        return new ItemEditorField(id, Kind.TEXT, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public static ItemEditorField navigate(String id, String labelKey, String current) {
        return new ItemEditorField(id, Kind.NAVIGATE, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public static ItemEditorField list(String id, String labelKey, String current) {
        return new ItemEditorField(id, Kind.LIST, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public static ItemEditorField command(String id, String labelKey, String current) {
        return new ItemEditorField(id, Kind.COMMAND, labelKey, null, null, null,
                List.of(current), List.of(), -1, true);
    }

    public ItemEditorField at(int index) {
        return new ItemEditorField(id, kind, labelKey, displayName, description, icon,
                valueLines, options, index, enabled);
    }

    public ItemEditorField withValue(String value) {
        return new ItemEditorField(id, kind, labelKey, displayName, description, icon,
                List.of(value), options, slotIndex, enabled);
    }

    public ItemEditorField disabled() {
        return new ItemEditorField(id, kind, labelKey, displayName, description, icon,
                valueLines, options, slotIndex, false);
    }
}
