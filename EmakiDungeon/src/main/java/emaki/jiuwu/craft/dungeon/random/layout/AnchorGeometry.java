package emaki.jiuwu.craft.dungeon.random.layout;

import java.util.ArrayList;
import java.util.List;

import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;

public final class AnchorGeometry {

    public static List<int[]> openingCells(DoorAnchor anchor) {
        List<int[]> result = new ArrayList<>();
        boolean vertical = TemplateModel.isVertical(anchor.facing());
        int dx = vertical || TemplateModel.facingDx(anchor.facing()) == 0 ? 1 : 0;
        int dz = vertical || TemplateModel.facingDz(anchor.facing()) == 0 ? 1 : 0;
        for (int width = 0; width < anchor.width(); width++) {
            for (int height = 0; height < anchor.height(); height++) {
                int y = anchor.y() + (vertical ? 0 : height);
                int z = anchor.z() + (vertical ? height : dz * width);
                result.add(new int[] {anchor.x() + dx * width, y, z});
            }
        }
        return List.copyOf(result);
    }

    private AnchorGeometry() {
    }
}
