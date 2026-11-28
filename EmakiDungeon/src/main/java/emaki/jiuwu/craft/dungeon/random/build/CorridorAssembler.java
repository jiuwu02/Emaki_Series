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
public final class CorridorAssembler {

    private CorridorAssembler() {
    }

    /** 走廊模板沿其自身轴的块长度。 */
    public static int tileLength(TemplateMeta meta) {
        if (meta == null || meta.anchors().size() != 2) {
            return 0;
        }
        DoorAnchor first = meta.anchors().get(0);
        DoorAnchor second = meta.anchors().get(1);
        boolean xAxis = !"north".equals(first.facing()) && !"south".equals(first.facing());
        return xAxis ? meta.sizeX() : meta.sizeZ();
    }

    /** 两个门锚点必须朝向相反且同轴, 否则无法作为可拼接的直走廊段。 */
    public static boolean isStraightCorridor(TemplateMeta meta) {
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
     * 走廊模板在一段走廊上的铺装锚点: 每段的起点原点和对应变体。
     *
     * <p>节点烘焙与方块铺设必须用同一份锚点, 否则模块节点会与模板实体错位。
     *
     * @return {index, originX, originY, originZ, rotations, span, dirX, dirZ};
     *         index 为该块在 run.cells() 中的起点下标, span 为该块在本段内允许占用的格数
     *         (段尾放不下整块时小于模板长度), dirX/dirZ 为该段行进方向;
     *         调用方据此裁剪越过段尾的方块, 节点烘焙则只需前 5 项
     */
    public static List<int[]> tileOrigins(CorridorRun run, TemplateMeta meta, int tileGap) {
        List<int[]> out = new ArrayList<>();
        if (!isStraightCorridor(meta)) {
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
            for (int index = startIndex; index < endIndex; index += stride) {
                CorridorCell cell = run.cells().get(index);
                // 段尾剩不下一个整块模板时仍然放一块, 只保留本段范围内的方块。整块跳过会让
                // 该段退化成纯程序化地板 (裸石条), 看起来就是"这段走廊没有模板"。
                int span = Math.min(length, endIndex - index);
                // cell.y 是地板方块层, 模板本地 y=0 是门的可行走面层, 原点必须抬一格,
                // 否则模板门洞比房间门洞低一层, 与房间对不上
                out.add(new int[] {index, cell.x() - entryLocal[0], cell.y() + 1 - entryLocal[1],
                        cell.z() - entryLocal[2], variant.rotations(), span, dirX, dirZ});
            }
        }
        return out;
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
        for (int[] tile : tileOrigins(run, meta, tileGap)) {
            int originX = tile[1];
            int originY = tile[2];
            int originZ = tile[3];
            int span = tile[5];
            int dirX = tile[6];
            int dirZ = tile[7];
            CorridorCell head = run.cells().get(tile[0]);
            Transforms.Variant variant = new Transforms.Variant(tile[4], false);
            for (StructureNbt.BlockEntry entryBlock : data.blocks()) {
                if (entryBlock.state() < 0 || entryBlock.state() >= palette.size()) {
                    continue;
                }
                int[] local = Transforms.transform(meta, variant,
                        entryBlock.x(), entryBlock.y(), entryBlock.z());
                int worldX = originX + local[0];
                int worldZ = originZ + local[2];
                // 只保留落在本段行程内的方块: 段尾那块的尾部会越过段止点, 不裁剪就直接
                // 盖进后面的转向点或相邻房间, 表现为走廊与房间错位与多余方块
                int along = (worldX - head.x()) * dirX + (worldZ - head.z()) * dirZ;
                if (along < 0 || along >= span) {
                    continue;
                }
                out.add(new PlannedBlock(worldX, originY + local[1], worldZ,
                        palette.get(entryBlock.state())));
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
            if (!straight) {
                // 楼梯或断点: 当前 cell 不并入任何直线段, 直线段在此处收尾
                if (start >= 0) {
                    segments.add(new int[] {start, i, dirX, dirZ});
                    start = -1;
                }
                continue;
            }
            if (start >= 0 && stepX == dirX && stepZ == dirZ) {
                continue;
            }
            // 转向, 或刚从楼梯段恢复到平走: 先收尾旧段, 再以当前 cell 起新段
            if (start >= 0) {
                segments.add(new int[] {start, i, dirX, dirZ});
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
