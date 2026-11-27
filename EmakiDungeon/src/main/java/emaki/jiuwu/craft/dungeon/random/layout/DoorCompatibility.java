package emaki.jiuwu.craft.dungeon.random.layout;

import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;

public record DoorCompatibility(String kind, int width, int height) {

    public static DoorCompatibility from(DoorAnchor anchor) {
        return new DoorCompatibility(anchor.connectionKind(), anchor.width(), anchor.height());
    }

    public boolean accepts(DoorCompatibility other) {
        return kind.equals(other.kind) && width == other.width && height == other.height;
    }
}
