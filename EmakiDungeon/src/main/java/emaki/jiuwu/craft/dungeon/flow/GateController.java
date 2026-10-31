package emaki.jiuwu.craft.dungeon.flow;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import emaki.jiuwu.craft.corelib.debug.DebugLogger;
import emaki.jiuwu.craft.dungeon.instance.DungeonInstance;
import emaki.jiuwu.craft.dungeon.instance.WorldCommand;
import emaki.jiuwu.craft.dungeon.instance.WorldCommandTarget;
import org.bukkit.block.data.BlockData;

public final class GateController {

    private static final int MAX_BLOCKS_PER_GATE = 8192;

    private final Plugin plugin;
    private final DungeonInstance instance;
    private final DebugLogger debug;
    private volatile WaveController waveController;

    public GateController(Plugin plugin, DungeonInstance instance, DebugLogger debug) {
        this.plugin = plugin;
        this.instance = instance;
        this.debug = debug;
    }

    public void bindWaveController(WaveController waveController) {
        this.waveController = waveController;
    }

    public boolean openGate(FlowRuntime runtime, String gateId, UUID playerId) {
        FlowModules.GateDef gate = runtime.definition().gates().get(gateId);
        if (gate == null) {
            return false;
        }
        debug.logRaw("dungeon", (UUID) null, "gate open | gate=" + gate.gateId());
        if (waveController != null) {
            waveController.notifyGateOpened(gate.gateId(), gate.logic());
        }
        if ("redstone".equalsIgnoreCase(gate.openMode()) && gate.redstonePoint() != null) {
            submitBlock(gate.redstonePoint(), Material.REDSTONE_BLOCK.createBlockData(), 1);
            return true;
        }
        int cost = 0;
        for (FlowRegion region : gate.doorBlocks()) {
            int volume = regionVolume(region);
            if (cost + volume > MAX_BLOCKS_PER_GATE) {
                break;
            }
            cost += volume;
            submitAir(region);
        }
        return true;
    }

    private void submitAir(FlowRegion region) {
        FlowPoint center = region.center();
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return regionVolume(region);
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.LOCATION;
            }

            @Override
            public String worldName() {
                return instance.instanceId();
            }

            @Override
            public double[] anchor() {
                return new double[] {center.x(), center.y(), center.z()};
            }

            @Override
            public void execute() {
                World world = Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return;
                }
                int minX = (int) Math.floor(Math.min(region.minX(), region.maxX()));
                int maxX = (int) Math.floor(Math.max(region.minX(), region.maxX()));
                int minY = (int) Math.floor(Math.min(region.minY(), region.maxY()));
                int maxY = (int) Math.floor(Math.max(region.minY(), region.maxY()));
                int minZ = (int) Math.floor(Math.min(region.minZ(), region.maxZ()));
                int maxZ = (int) Math.floor(Math.max(region.minZ(), region.maxZ()));
                var air = Material.AIR.createBlockData();
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            world.getBlockAt(x, y, z).setBlockData(air, false);
                        }
                    }
                }
            }
        });
    }

    private void submitBlock(FlowPoint point, BlockData blockData, int cost) {
        instance.commands().submit(new WorldCommand() {
            @Override
            public int blockCost() {
                return cost;
            }

            @Override
            public WorldCommandTarget target() {
                return WorldCommandTarget.LOCATION;
            }

            @Override
            public String worldName() {
                return instance.instanceId();
            }

            @Override
            public double[] anchor() {
                return new double[] {point.x(), point.y(), point.z()};
            }

            @Override
            public void execute() {
                World world = Bukkit.getWorld(instance.instanceId());
                if (world == null) {
                    return;
                }
                world.getBlockAt((int) Math.floor(point.x()),
                        (int) Math.floor(point.y()),
                        (int) Math.floor(point.z())).setBlockData(blockData, false);
            }
        });
    }

    private static int regionVolume(FlowRegion region) {
        int width = (int) Math.floor(Math.max(region.minX(), region.maxX())
                - Math.floor(Math.min(region.minX(), region.maxX()))) + 1;
        int height = (int) Math.floor(Math.max(region.minY(), region.maxY())
                - Math.floor(Math.min(region.minY(), region.maxY()))) + 1;
        int depth = (int) Math.floor(Math.max(region.minZ(), region.maxZ())
                - Math.floor(Math.min(region.minZ(), region.maxZ()))) + 1;
        return Math.max(1, width * height * depth);
    }
}
