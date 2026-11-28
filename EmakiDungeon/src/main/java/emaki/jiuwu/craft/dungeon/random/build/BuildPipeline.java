package emaki.jiuwu.craft.dungeon.random.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.api.scheduling.TaskToken;
import emaki.jiuwu.craft.corelib.execution.ExecutionDispatcher;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.RoomPlacement;
import emaki.jiuwu.craft.dungeon.random.layout.Transforms;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.layout.AnchorGeometry;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel;
import emaki.jiuwu.craft.dungeon.random.model.TemplateModel.DoorAnchor;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt.BlockEntry;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt.PaletteEntry;

public final class BuildPipeline {

    public record PlannedBlock(int x, int y, int z, BlockData data) {
    }

    public record Plan(List<PlannedBlock> rooms, List<PlannedBlock> connections,
            List<PlannedBlock> seals, int totalBlocks) {
    }

    private static final long YIELD_NANOS = 2_000_000L;

    public static Plan plan(Layout layout,
            List<java.util.Map.Entry<String, StructureNbt.StructureData>> orderedTemplates,
            java.util.Map<String, TemplateMeta> metas,
            java.util.Map<String, StructureNbt.StructureData> templates,
            String floorBlock, String defaultSealBlock) {
        return plan(layout, orderedTemplates, metas, templates, floorBlock, defaultSealBlock,
                null, null, 0);
    }

    /**
     * @param corridorTypeId 走廊模板 id, null 表示纯程序化地板
     * @param corridorMeta   走廊模板元数据
     * @param tileGap        相邻模板段之间的空块数
     */
    public static Plan plan(Layout layout,
            List<java.util.Map.Entry<String, StructureNbt.StructureData>> orderedTemplates,
            java.util.Map<String, TemplateMeta> metas,
            java.util.Map<String, StructureNbt.StructureData> templates,
            String floorBlock, String defaultSealBlock,
            String corridorTypeId, TemplateMeta corridorMeta, int tileGap) {
        List<PlannedBlock> rooms = new ArrayList<>();
        for (RoomPlacement room : layout.rooms()) {
            StructureNbt.StructureData data = templates.get(room.typeId());
            if (data == null) {
                continue;
            }
            List<BlockData> palette = paletteOf(data);
            for (BlockEntry entry : data.blocks()) {
                if (entry.state() < 0 || entry.state() >= palette.size()) {
                    continue;
                }
                int[] local = Transforms.transform(room.meta(), room.variant(), entry.x(), entry.y(), entry.z());
                rooms.add(new PlannedBlock(room.originX() + local[0], room.originY() + local[1],
                        room.originZ() + local[2], palette.get(entry.state())));
            }
        }
        BlockData floor = blockData(floorBlock, Material.STONE.createBlockData());
        List<PlannedBlock> connections = new ArrayList<>();
        StructureNbt.StructureData corridorData = corridorTypeId == null ? null : templates.get(corridorTypeId);
        List<BlockData> corridorPalette = corridorData == null ? List.of() : paletteOf(corridorData);
        for (var run : layout.corridors()) {
            Set<String> tiled = new HashSet<>();
            List<PlannedBlock> templateBlocks = new ArrayList<>();
            // 每段走廊用自己的模板 (多模板按权重抽样时各段可能不同)
            String runTypeId = run.templateId() != null ? run.templateId()
                    : corridorTypeId;
            StructureNbt.StructureData runData = runTypeId == null ? null : templates.get(runTypeId);
            TemplateMeta runMeta = runTypeId == null ? null : metas.get(runTypeId);
            List<BlockData> runPalette = runData == null ? List.of() : paletteOf(runData);
            if (runData != null) {
                for (PlannedBlock block : CorridorAssembler.assemble(run, runMeta, runData,
                        runPalette, tileGap)) {
                    templateBlocks.add(block);
                    tiled.add(TemplateModel.packCell(
                            block.x(), block.y(), block.z()) + "");
                }
            }
            // 先清空走廊净空, 再铺地板与模板, 否则模板墙体会被后续 AIR 覆盖挖穿
            for (var cell : run.cells()) {
                for (int y = 1; y <= run.sectionHeight(); y++) {
                    connections.add(new PlannedBlock(cell.x(), cell.y() + y, cell.z(),
                            Material.AIR.createBlockData()));
                }
            }
            for (var cell : run.cells()) {
                if (!tiled.contains(TemplateModel.packCell(
                        cell.x(), cell.y(), cell.z()) + "")) {
                    connections.add(new PlannedBlock(cell.x(), cell.y(), cell.z(), floor));
                }
            }
            connections.addAll(templateBlocks);
        }
        List<PlannedBlock> seals = new ArrayList<>();
        for (var unconnected : layout.unconnected()) {
            if (!TemplateModel.POLICY_SEAL.equals(unconnected.policy())) {
                continue;
            }
            TemplateMeta meta = metas.get(unconnected.typeId());
            DoorAnchor anchor = meta == null ? null : meta.anchor(unconnected.anchorId());
            String sealName = anchor != null && anchor.sealBlock() != null && !anchor.sealBlock().isBlank()
                    ? anchor.sealBlock()
                    : (meta != null && meta.sealBlock() != null && !meta.sealBlock().isBlank()
                            ? meta.sealBlock()
                            : defaultSealBlock);
            BlockData sealData = blockData(sealName, Material.STONE_BRICKS.createBlockData());
            if (TemplateModel.CONNECTION_LADDER.equals(unconnected.connectionKind())) {
                for (int k = 0; k < unconnected.width(); k++) {
                    for (int y = 0; y < unconnected.height(); y++) {
                        seals.add(new PlannedBlock(unconnected.worldPos()[0], unconnected.worldPos()[1] + y,
                                unconnected.worldPos()[2], blockData("minecraft:ladder", Material.LADDER.createBlockData())));
                    }
                }
                continue;
            }
            if (TemplateModel.CONNECTION_ELEVATOR.equals(unconnected.connectionKind())) {
                for (int k = 0; k < unconnected.width(); k++) {
                    for (int y = 0; y < unconnected.height(); y++) {
                        seals.add(new PlannedBlock(unconnected.worldPos()[0], unconnected.worldPos()[1] + y,
                                unconnected.worldPos()[2], blockData("minecraft:bubble_column", Material.BUBBLE_COLUMN.createBlockData())));
                    }
                }
                continue;
            }
            for (int[] cell : AnchorGeometry.openingCells(
                    new DoorAnchor(unconnected.anchorId(), unconnected.worldPos()[0], unconnected.worldPos()[1],
                            unconnected.worldPos()[2], unconnected.facing(), unconnected.width(), unconnected.height(),
                            unconnected.worldPos()[1], 1, unconnected.policy(), ""))) {
                seals.add(new PlannedBlock(cell[0], cell[1], cell[2], sealData));
            }
        }
        int total = rooms.size() + connections.size() + seals.size();
        return new Plan(List.copyOf(rooms), List.copyOf(connections), List.copyOf(seals), total);
    }

    private static List<BlockData> paletteOf(StructureNbt.StructureData data) {
        List<BlockData> palette = new ArrayList<>(data.palette().size());
        for (PaletteEntry entry : data.palette()) {
            palette.add(blockData(entry.blockDataString(), Material.AIR.createBlockData()));
        }
        return palette;
    }

    private static BlockData blockData(String raw, BlockData fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Bukkit.createBlockData(raw);
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    public TaskToken start(Plugin plugin, ExecutionDispatcher dispatcher, World world,
            Plan plan, int blocksPerTick, Runnable onComplete, Consumer<String> onFailed) {
        ArrayDeque<PlannedBlock> pending = new ArrayDeque<>();
        pending.addAll(plan.rooms());
        pending.addAll(plan.connections());
        pending.addAll(plan.seals());
        TaskToken[] holder = new TaskToken[1];
        long[] startedAt = {System.currentTimeMillis()};
        holder[0] = dispatcher.runGlobalTimer(plugin, () -> {
            long tickStart = System.nanoTime();
            int blocks = 0;
            while (!pending.isEmpty() && blocks < blocksPerTick) {
                PlannedBlock block = pending.poll();
                if (block == null) {
                    break;
                }
                world.getBlockAt(block.x(), block.y(), block.z()).setBlockData(block.data(), false);
                blocks++;
                if (System.nanoTime() - tickStart > YIELD_NANOS) {
                    break;
                }
            }
            if (pending.isEmpty()) {
                holder[0].cancel();
                if (System.currentTimeMillis() - startedAt[0] > 10_000L) {
                    onFailed.accept("build-window-extended");
                }
                onComplete.run();
            }
        }, 1L, 1L);
        return holder[0];
    }

    public static void prewarm(World world, int centerX, int centerZ, int radiusChunks) {
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                world.getChunkAt((centerX >> 4) + dx, (centerZ >> 4) + dz);
            }
        }
    }
}
