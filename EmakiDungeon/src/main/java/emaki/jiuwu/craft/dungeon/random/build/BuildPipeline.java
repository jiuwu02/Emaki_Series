package emaki.jiuwu.craft.dungeon.random.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
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
        for (var run : layout.corridors()) {
            for (var cell : run.cells()) {
                connections.add(new PlannedBlock(cell.x(), cell.y(), cell.z(), floor));
                connections.add(new PlannedBlock(cell.x(), cell.y() + 1, cell.z(), Material.AIR.createBlockData()));
                connections.add(new PlannedBlock(cell.x(), cell.y() + 2, cell.z(), Material.AIR.createBlockData()));
                connections.add(new PlannedBlock(cell.x(), cell.y() + 3, cell.z(), Material.AIR.createBlockData()));
            }
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
            int pdx = TemplateModel.facingDx(unconnected.facing()) == 0 ? 1 : 0;
            int pdz = TemplateModel.facingDz(unconnected.facing()) == 0 ? 1 : 0;
            for (int k = 0; k < unconnected.width(); k++) {
                for (int y = unconnected.worldPos()[1]; y < unconnected.worldPos()[1] + unconnected.height(); y++) {
                    seals.add(new PlannedBlock(
                            unconnected.worldPos()[0] + pdx * k,
                            y,
                            unconnected.worldPos()[2] + pdz * k,
                            sealData));
                }
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
