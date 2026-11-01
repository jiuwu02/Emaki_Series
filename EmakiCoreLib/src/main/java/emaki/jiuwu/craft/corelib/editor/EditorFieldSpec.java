package emaki.jiuwu.craft.corelib.editor;

import java.util.List;

import org.bukkit.Material;

public record EditorFieldSpec(String id,
        EditorField.Kind kind,
        String labelKey,
        String[] path,
        List<String> options,
        String targetMenu,
        String listPath,
        Material icon) {

    public EditorFieldSpec {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static EditorFieldSpec toggle(String id, String labelKey, String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.TOGGLE, labelKey, path, List.of(), null, null, null);
    }

    public static EditorFieldSpec cycle(String id, String labelKey, List<String> options, String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.CYCLE, labelKey, path, options, null, null, null);
    }

    public static EditorFieldSpec number(String id, String labelKey, String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.NUMBER, labelKey, path, List.of(), null, null, null);
    }

    public static EditorFieldSpec text(String id, String labelKey, String... path) {
        return new EditorFieldSpec(id, EditorField.Kind.TEXT, labelKey, path, List.of(), null, null, null);
    }

    public static EditorFieldSpec navigate(String id, String labelKey, String targetMenu) {
        return new EditorFieldSpec(id, EditorField.Kind.NAVIGATE, labelKey, new String[0], List.of(),
                targetMenu, null, null);
    }

    public static EditorFieldSpec list(String id, String labelKey, String targetMenu, String listPath) {
        return new EditorFieldSpec(id, EditorField.Kind.LIST, labelKey, new String[0], List.of(),
                targetMenu, listPath, null);
    }

    public EditorFieldSpec withIcon(Material material) {
        return new EditorFieldSpec(id, kind, labelKey, path, options, targetMenu, listPath, material);
    }
}
