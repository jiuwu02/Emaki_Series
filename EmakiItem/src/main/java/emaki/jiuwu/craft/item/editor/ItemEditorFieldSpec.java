package emaki.jiuwu.craft.item.editor;

import java.util.List;

public record ItemEditorFieldSpec(String id,
        ItemEditorField.Kind kind,
        String labelKey,
        String[] path,
        List<String> options,
        String targetMenu,
        String listPath) {

    public ItemEditorFieldSpec {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static ItemEditorFieldSpec toggle(String id, String labelKey, String... path) {
        return new ItemEditorFieldSpec(id, ItemEditorField.Kind.TOGGLE, labelKey, path, List.of(), null, null);
    }

    public static ItemEditorFieldSpec cycle(String id, String labelKey, List<String> options, String... path) {
        return new ItemEditorFieldSpec(id, ItemEditorField.Kind.CYCLE, labelKey, path, options, null, null);
    }

    public static ItemEditorFieldSpec number(String id, String labelKey, String... path) {
        return new ItemEditorFieldSpec(id, ItemEditorField.Kind.NUMBER, labelKey, path, List.of(), null, null);
    }

    public static ItemEditorFieldSpec text(String id, String labelKey, String... path) {
        return new ItemEditorFieldSpec(id, ItemEditorField.Kind.TEXT, labelKey, path, List.of(), null, null);
    }

    public static ItemEditorFieldSpec navigate(String id, String labelKey, String targetMenu) {
        return new ItemEditorFieldSpec(id, ItemEditorField.Kind.NAVIGATE, labelKey, new String[0], List.of(),
                targetMenu, null);
    }

    public static ItemEditorFieldSpec list(String id, String labelKey, String targetMenu, String listPath) {
        return new ItemEditorFieldSpec(id, ItemEditorField.Kind.LIST, labelKey, new String[0], List.of(),
                targetMenu, listPath);
    }
}
