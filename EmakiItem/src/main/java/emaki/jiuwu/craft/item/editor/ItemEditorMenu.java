package emaki.jiuwu.craft.item.editor;

import java.util.List;

public record ItemEditorMenu(String id,
        String titleKey,
        String parentMenuId,
        List<ItemEditorField> fields,
        boolean paginated) {

    public ItemEditorMenu {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static ItemEditorMenu of(String id, String titleKey, String parentMenuId, List<ItemEditorField> fields) {
        return new ItemEditorMenu(id, titleKey, parentMenuId, fields, false);
    }

    public static ItemEditorMenu paged(String id, String titleKey, String parentMenuId, List<ItemEditorField> fields) {
        return new ItemEditorMenu(id, titleKey, parentMenuId, fields, true);
    }
}
