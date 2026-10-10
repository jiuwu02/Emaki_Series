package emaki.jiuwu.craft.corelib.editor;

public record EditorMenuMeta(String id,
        String titleKey,
        String parent,
        String[] structuralRoot,
        boolean paginated) {
}
