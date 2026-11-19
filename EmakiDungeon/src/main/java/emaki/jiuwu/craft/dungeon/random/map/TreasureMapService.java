package emaki.jiuwu.craft.dungeon.random.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import net.kyori.adventure.text.Component;

public final class TreasureMapService {

    private static final int BACKGROUND = 0x101010;
    private static final int OUTLINE = 0x8A8A8A;
    private static final int EXPLORED = 0xE6E6E6;
    private static final int LINE = 0x5A5A5A;
    private static final int ICON = 0xFFC83C;
    private static final int[] FLOOR_COLORS = {
        0xB0B0B0, 0x5AA0E0, 0x55C070, 0xE0B040,
        0xD07050, 0x9060D0, 0x40C0C0, 0xE070A0
    };

    public record RoomCell(int nodeId, int cellX, int cellZ, int floor, String icon) {
    }

    public static final class InstanceMap {

        private final int[][] pixels = new int[128][128];
        private final List<RoomCell> rooms;
        private final List<int[]> links;
        private final Set<Integer> explored = new HashSet<>();
        private final Map<Player, MapView> holders = new HashMap<>();
        private final boolean showOutline;
        private final boolean floorMarkers;
        private final int floors;
        private volatile boolean dirty = true;

        InstanceMap(List<RoomCell> rooms, List<int[]> links, boolean showOutline,
                boolean floorMarkers, int floors) {
            this.rooms = rooms;
            this.links = links;
            this.showOutline = showOutline;
            this.floorMarkers = floorMarkers;
            this.floors = floors;
            for (int[] column : pixels) {
                java.util.Arrays.fill(column, BACKGROUND);
            }
            renderBase();
        }

        private void renderBase() {
            for (int[] link : links) {
                RoomCell from = room(link[0]);
                RoomCell to = room(link[1]);
                if (from == null || to == null) {
                    continue;
                }
                drawLine(from.cellX() * 2 + 1, from.cellZ() * 2 + 1, to.cellX() * 2 + 1, to.cellZ() * 2 + 1);
            }
            for (RoomCell room : rooms) {
                drawRoom(room, false);
            }
        }

        private void drawRoom(RoomCell room, boolean exploredNow) {
            int px = room.cellX() * 2;
            int pz = room.cellZ() * 2;
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    boolean edge = showOutline && (dx == 0 || dz == 0);
                    put(px + dx, pz + dz, exploredNow ? EXPLORED : (edge ? OUTLINE : BACKGROUND));
                }
            }
            if (exploredNow && room.icon() != null) {
                String[] icon = MapIcons.icon(room.icon());
                if (icon != null) {
                    for (int row = 0; row < 8; row++) {
                        for (int col = 0; col < 8; col++) {
                            if (icon[row].charAt(col) == 'X') {
                                put(px - 3 + col, pz - 3 + row, ICON);
                            }
                        }
                    }
                }
            }
            if (floorMarkers && floors > 1 && exploredNow) {
                int marker = floorColor(room.floor());
                put(px + 2, pz - 1, marker);
                put(px + 3, pz - 1, marker);
                put(px + 2, pz - 2, marker);
                put(px + 3, pz - 2, marker);
            }
        }

        private int floorColor(int floor) {
            return FLOOR_COLORS[Math.floorMod(Math.max(1, floor) - 1, FLOOR_COLORS.length)];
        }

        private void drawLine(int x0, int z0, int x1, int z1) {
            int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
            for (int i = 0; i <= steps; i++) {
                int x = x0 + (steps == 0 ? 0 : (x1 - x0) * i / steps);
                int z = z0 + (steps == 0 ? 0 : (z1 - z0) * i / steps);
                put(x, z, LINE);
            }
        }

        private void put(int x, int z, int rgb) {
            if (x >= 0 && x < 128 && z >= 0 && z < 128) {
                pixels[x][z] = rgb;
            }
        }

        private RoomCell room(int nodeId) {
            for (RoomCell room : rooms) {
                if (room.nodeId() == nodeId) {
                    return room;
                }
            }
            return null;
        }

        public void explore(int nodeId) {
            if (explored.add(nodeId)) {
                RoomCell room = room(nodeId);
                if (room != null) {
                    drawRoom(room, true);
                    dirty = true;
                }
            }
        }

        public ItemStack createItem(String displayName) {
            ItemStack item = new ItemStack(Material.FILLED_MAP);
            MapMeta meta = (MapMeta) item.getItemMeta();
            if (meta != null) {
                if (displayName != null && !displayName.isBlank()) {
                    meta.displayName(Component.text(displayName));
                }
                item.setItemMeta(meta);
            }
            return item;
        }

        void attach(Player player, MapView view, MapRenderer renderer) {
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(renderer);
            holders.put(player, view);
        }

        MapView viewOf(Player player) {
            return holders.get(player);
        }

        void detach(Player player) {
            holders.remove(player);
        }

        int pixel(int x, int z) {
            return pixels[x][z];
        }

        Set<Integer> exploredSet() {
            return explored;
        }
    }

    private static final class BufferRenderer extends MapRenderer {

        private final InstanceMap state;

        BufferRenderer(InstanceMap state) {
            this.state = state;
        }

        @Override
        public void render(MapView view, MapCanvas canvas, Player player) {
            for (int x = 0; x < 128; x++) {
                for (int z = 0; z < 128; z++) {
                    canvas.setPixelColor(x, z, new java.awt.Color(state.pixel(x, z)));
                }
            }
        }
    }

    private final Map<String, InstanceMap> instances = new HashMap<>();

    public InstanceMap create(String instanceId, List<RoomCell> rooms, List<int[]> links,
            boolean showOutline, boolean floorMarkers, int floors) {
        InstanceMap map = new InstanceMap(rooms, links, showOutline, floorMarkers, floors);
        instances.put(instanceId, map);
        return map;
    }

    public void bind(String instanceId, Player player, MapView view, String displayName) {
        InstanceMap map = instances.get(instanceId);
        if (map == null || view == null) {
            return;
        }
        map.attach(player, view, new BufferRenderer(map));
    }

    public void explore(String instanceId, int nodeId) {
        InstanceMap map = instances.get(instanceId);
        if (map != null) {
            map.explore(nodeId);
        }
    }

    public void remove(String instanceId) {
        InstanceMap map = instances.remove(instanceId);
        if (map != null) {
            for (Player player : Set.copyOf(map.holders.keySet())) {
                MapView view = map.viewOf(player);
                if (view != null) {
                    view.getRenderers().forEach(view::removeRenderer);
                }
                map.detach(player);
            }
        }
    }

    public Set<Integer> exploredOf(String instanceId) {
        InstanceMap map = instances.get(instanceId);
        return map == null ? Set.of() : Set.copyOf(map.exploredSet());
    }
}
