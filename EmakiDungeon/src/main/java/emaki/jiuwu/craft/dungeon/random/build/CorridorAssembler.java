package emaki.jiuwu.craft.dungeon.random.build;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.data.BlockData;

import emaki.jiuwu.craft.dungeon.random.build.BuildPipeline.PlannedBlock;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.CorridorCell;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.CorridorRun;
import emaki.jiuwu.craft.dungeon.random.layout.Transforms;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt;

/**
 * 走廊模板拼装。
 *
 * <p>把一段走廊按直线方向切分成若干直段, 每个直段用走廊模板 NBT 逐段平铺; 不足一段的余量
 * 仍由调用方的程序化地板补齐。模板必须满足:
 * <ul>
 *   <li>structure-type = corridor</li>
 *   <li>恰好两个门锚点, 朝向相反, 且落在同一条轴上</li>
 * </ul>
 * 不满足时返回空列表, 调用方回退纯程序化地板。
 */
final class CorridorAssembler {

    private CorridorAssembler() {
    }

    /** 走廊模板沿其自身轴的块长度。 */
    static int tileLength(TemplateMeta meta) {
        if (meta == null || meta.anchors().size() != 2) {
            return 0;
        }
        DoorAnchor first = meta.anchors().get(0);
        DoorAnchor second = meta.anchors().get(1);
        boolean xAxis = !"north".equals(first.facing()) && !"south".equals(first.facing());
        return xAxis ? meta.sizeX() : meta.sizeZ();
    }

    /** 两个门锚点必须朝向相反且同轴, 否则无法作为可拼接的直走廊段。 */
    static boolean isStraightCorridor(TemplateMeta meta) {
        if (meta == null || !TemplateModel.TYPE_CORRIDOR.equals(meta.structureType())) {
            return false;
        }
        if (meta.anchors().size() != 2) {
            return false;
        }
        DoorAnchor first = meta.anchors().get(0);
        DoorAnchor second = meta.anchors().get(1);
        return TemplateModel.oppositeFacing(first.facing()).equals(second.facing());
    }

    /**
     * 把一段走廊铺成模板块。返回的列表可能为空 (无可用模板或全程不足一段)。
     */
    static List<PlannedBlock> assemble(CorridorRun run, TemplateMeta meta,
            StructureNbt.StructureData data, List<BlockData> palette, int tileGap) {
        List<PlannedBlock> out = new ArrayList<>();
        if (!isStraightCorridor(meta) || data == null || palette.isEmpty()) {
            return out;
        }
        int length = tileLength(meta);
        if (length <= 0) {
            return out;
        }
        int stride = length + Math.max(0, tileGap);
        for (int[] segment : straightSegments(run)) {
            int startIndex = segment[0];
            int endIndex = segment[1];
            int dirX = segment[2];
            int dirZ = segment[3];
            Transforms.Variant variant = variantFor(dirX, dirZ);
            if (variant == null) {
                continue;
            }
            DoorAnchor entry = entryAnchor(meta, variant, dirX, dirZ);
            if (entry == null) {
                continue;
            }
            int[] entryLocal = Transforms.transform(meta, variant, entry.x(), entry.y(), entry.z());
            for (int index = startIndex; index + length <= endIndex; index += stride) {
                CorridorCell cell = run.cells().get(index);
                int originX = cell.x() - entryLocal[0];
                int originY = cell.y() - entryLocal[1];
                int originZ = cell.z() - entryLocal[2];
                for (StructureNbt.BlockEntry entryBlock : data.blocks()) {
                    if (entryBlock.state() < 0 || entryBlock.state() >= palette.size()) {
                        continue;
                    }
                    int[] local = Transforms.transform(meta, variant,
                            entryBlock.x(), entryBlock.y(), entryBlock.z());
                    out.add(new PlannedBlock(originX + local[0], originY + local[1], originZ + local[2],
                            palette.get(entryBlock.state())));
                }
            }
        }
        return out;
    }

    /** 连续同向的水平行进段: {起, 止(不含), dx, dz}。楼梯段与转向点不参与模板平铺。 */
    private static List<int[]> straightSegments(CorridorRun run) {
        List<int[]> segments = new ArrayList<>();
        List<CorridorCell> cells = run.cells();
        if (cells.size() < 2) {
            return segments;
        }
        CorridorCell first = cells.get(0);
        CorridorCell second = cells.get(1);
        int start = 0;
        int dirX = Integer.signum(second.x() - first.x());
        int dirZ = Integer.signum(second.z() - first.z());
        if (second.y() != first.y() || (dirX == 0 && dirZ == 0)) {
            return segments;
        }
        for (int i = 1; i < cells.size(); i++) {
            CorridorCell previous = cells.get(i - 1);
            CorridorCell current = cells.get(i);
            int stepX = Integer.signum(current.x() - previous.x());
            int stepZ = Integer.signum(current.z() - previous.z());
            boolean sameY = current.y() == previous.y();
            boolean straight = sameY && (stepX != 0 || stepZ != 0);
            if (straight && stepX == dirX && stepZ == dirZ) {
                continue;
            }
            segments.add(new int[] {start, i, dirX, dirZ});
            if (!straight) {
                // 楼梯或断点: 当前 cell 不并入任何直线段
                start = -1;
                continue;
            }
            start = i;
            dirX = stepX;
            dirZ = stepZ;
        }
        if (start >= 0) {
            segments.add(new int[] {start, cells.size(), dirX, dirZ});
        }
        return segments;
    }

    private static Transforms.Variant variantFor(int dirX, int dirZ) {
        if (dirX > 0) {
            return new Transforms.Variant(0, false);
        }
        if (dirZ > 0) {
            return new Transforms.Variant(90, false);
        }
        if (dirX < 0) {
            return new Transforms.Variant(180, false);
        }
        if (dirZ < 0) {
            return new Transforms.Variant(270, false);
        }
        return null;
    }

    /**
     * 行进方向的反向那个锚点, 作为这一段与上一段的接缝。
     * 必须比较**变换后**的朝向: 变体旋转会改变锚点在世界中的朝向。
     */
    private static DoorAnchor entryAnchor(TemplateMeta meta, Transforms.Variant variant,
            int dirX, int dirZ) {
        String travelFacing = dirX > 0 ? TemplateModel.FACING_E
                : dirX < 0 ? TemplateModel.FACING_W
                : dirZ > 0 ? TemplateModel.FACING_S : TemplateModel.FACING_N;
        String tail = TemplateModel.oppositeFacing(travelFacing);
        for (DoorAnchor anchor : meta.anchors()) {
            if (tail.equals(Transforms.transformFacing(variant, anchor.facing()))) {
                return anchor;
            }
        }
        return null;
    }
}
