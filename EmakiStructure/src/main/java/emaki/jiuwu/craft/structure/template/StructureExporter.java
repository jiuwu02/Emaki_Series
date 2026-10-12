package emaki.jiuwu.craft.structure.template;

import emaki.jiuwu.craft.structure.furniture.FurnitureSidecarParser;
import emaki.jiuwu.craft.structure.integration.FurnitureIdAdapter;
import emaki.jiuwu.craft.structure.selection.SelectionBox;
import emaki.jiuwu.craft.structure.template.nbt.NbtIo;
import emaki.jiuwu.craft.structure.template.nbt.NbtValue;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StructureExporter {

    public record Captured(int sizeX, int sizeY, int sizeZ,
                           List<CapturedBlock> blocks,
                           List<CapturedEntity> entities) {
    }

    public record CapturedBlock(int x, int y, int z, String state, NbtValue.CompoundTag nbt) {
    }

    public record CapturedEntity(String type, double x, double y, double z, float yaw, float pitch) {
    }

    public record CapturedFurniture(String id, int x, int y, int z, double yaw, String facing) {
    }

    public record EntityCapture(List<CapturedEntity> entities,
                                List<CapturedFurniture> furniture,
                                int outOfSelection) {
    }

    private StructureExporter() {
    }

    public static List<CapturedBlock> captureBlocks(World world, SelectionBox subBox, SelectionBox fullBox) {
        List<CapturedBlock> blocks = new ArrayList<>();
        for (int y = subBox.minY(); y <= subBox.maxY(); y++) {
            for (int z = subBox.minZ(); z <= subBox.maxZ(); z++) {
                for (int x = subBox.minX(); x <= subBox.maxX(); x++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(new CapturedBlock(
                            x - fullBox.minX(), y - fullBox.minY(), z - fullBox.minZ(),
                            block.getBlockData().getAsString(),
                            readContainer(block)));
                }
            }
        }
        return blocks;
    }

    public static EntityCapture captureEntities(World world, SelectionBox subBox, SelectionBox fullBox,
            FurnitureIdAdapter adapter) {
        List<CapturedEntity> entities = new ArrayList<>();
        List<CapturedFurniture> furniture = new ArrayList<>();
        int outOfSelection = 0;
        BoundingBox bounds = new BoundingBox(subBox.minX(), subBox.minY(), subBox.minZ(),
                subBox.maxX() + 1, subBox.maxY() + 1, subBox.maxZ() + 1);
        boolean anySource = adapter.anySourcePresent();
        for (Entity entity : world.getNearbyEntities(bounds, candidate -> !(candidate instanceof Player))) {
            Location location = entity.getLocation();
            if (!withinSlice(location, subBox)) {
                continue;
            }
            String furnitureId = anySource ? adapter.identify(entity).orElse(null) : null;
            if (furnitureId != null) {
                if (!withinSelection(entity.getBoundingBox(), fullBox)) {
                    outOfSelection++;
                    continue;
                }
                Block anchor = resolveAnchor(world, location);
                if (!withinSelection(anchor, fullBox)) {
                    outOfSelection++;
                    continue;
                }
                double yaw = FurnitureSidecarParser.normalizeYaw(location.getYaw());
                String canonical = canonicalId(furnitureId,
                        adapter.identifySource(entity).orElse(null));
                furniture.add(new CapturedFurniture(
                        canonical.toLowerCase(Locale.ROOT),
                        anchor.getX() - fullBox.minX(),
                        anchor.getY() - fullBox.minY(),
                        anchor.getZ() - fullBox.minZ(),
                        yaw, FurnitureSidecarParser.facingOf(yaw)));
                continue;
            }
            entities.add(new CapturedEntity(
                    entityTypeKey(entity),
                    location.getX() - fullBox.minX(),
                    location.getY() - fullBox.minY(),
                    location.getZ() - fullBox.minZ(),
                    location.getYaw(),
                    location.getPitch()));
        }
        return new EntityCapture(entities, furniture, outOfSelection);
    }

    private static String canonicalId(String identified, String source) {
        if (source == null) {
            return identified;
        }
        String namespace = source.toLowerCase(Locale.ROOT);
        if (identified.startsWith(namespace + ":")) {
            return identified;
        }
        return namespace + ":" + identified;
    }

    private static Block resolveAnchor(World world, Location location) {
        return world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ());
    }

    private static boolean withinSelection(BoundingBox bounds, SelectionBox box) {
        return bounds.getMinX() >= box.minX() && bounds.getMaxX() <= box.maxX() + 1
                && bounds.getMinY() >= box.minY() && bounds.getMaxY() <= box.maxY() + 1
                && bounds.getMinZ() >= box.minZ() && bounds.getMaxZ() <= box.maxZ() + 1;
    }

    private static boolean withinSelection(Block block, SelectionBox box) {
        return block.getX() >= box.minX() && block.getX() <= box.maxX()
                && block.getY() >= box.minY() && block.getY() <= box.maxY()
                && block.getZ() >= box.minZ() && block.getZ() <= box.maxZ();
    }

    private static boolean withinSlice(Location location, SelectionBox subBox) {
        int blockX = location.getBlockX();
        int blockY = location.getBlockY();
        int blockZ = location.getBlockZ();
        return blockX >= subBox.minX() && blockX <= subBox.maxX()
                && blockY >= subBox.minY() && blockY <= subBox.maxY()
                && blockZ >= subBox.minZ() && blockZ <= subBox.maxZ();
    }

    private static String entityTypeKey(Entity entity) {
        var key = entity.getType().getKey();
        return key.getNamespace() + ":" + key.getKey();
    }

    public static NbtValue.CompoundTag readContainer(Block block) {
        if (!(block.getState() instanceof Container container)) {
            return null;
        }
        List<NbtValue> items = new ArrayList<>();
        var inventory = container.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            var stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Map<String, NbtValue> item = new LinkedHashMap<>();
            item.put("id", new NbtValue.StringTag(stack.getType().getKey().toString()));
            item.put("Slot", new NbtValue.ByteTag((byte) slot));
            item.put("count", new NbtValue.ByteTag((byte) Math.min(99, stack.getAmount())));
            items.add(new NbtValue.CompoundTag(item));
        }
        if (items.isEmpty()) {
            return null;
        }
        Map<String, NbtValue> payload = new LinkedHashMap<>();
        payload.put("id", new NbtValue.StringTag(block.getType().getKey().toString()));
        payload.put("Items", new NbtValue.ListTag(items));
        return new NbtValue.CompoundTag(payload);
    }

    public static NbtValue.CompoundTag encode(Captured captured) {
        Map<String, NbtValue> root = new LinkedHashMap<>();
        root.put("size", new NbtValue.IntsTag(new int[] {
                captured.sizeX(), captured.sizeY(), captured.sizeZ() }));
        Map<String, Integer> paletteIndex = new LinkedHashMap<>();
        List<NbtValue> palette = new ArrayList<>();
        List<NbtValue> blockEntries = new ArrayList<>();
        for (CapturedBlock block : captured.blocks()) {
            int state = paletteIndex.computeIfAbsent(block.state(), key -> {
                int assigned = palette.size();
                palette.add(paletteEntry(key));
                return assigned;
            });
            Map<String, NbtValue> entry = new LinkedHashMap<>();
            entry.put("pos", new NbtValue.IntsTag(new int[] { block.x(), block.y(), block.z() }));
            entry.put("state", new NbtValue.IntTag(state));
            if (block.nbt() != null) {
                entry.put("nbt", block.nbt());
            }
            blockEntries.add(new NbtValue.CompoundTag(entry));
        }
        List<NbtValue> entityEntries = new ArrayList<>();
        for (CapturedEntity entity : captured.entities()) {
            Map<String, NbtValue> entry = new LinkedHashMap<>();
            entry.put("id", new NbtValue.StringTag(entity.type()));
            entry.put("pos", new NbtValue.ListTag(List.of(
                    new NbtValue.DoubleTag(entity.x()),
                    new NbtValue.DoubleTag(entity.y()),
                    new NbtValue.DoubleTag(entity.z()))));
            entry.put("rot", new NbtValue.ListTag(List.of(
                    new NbtValue.FloatTag(entity.yaw()),
                    new NbtValue.FloatTag(entity.pitch()))));
            entityEntries.add(new NbtValue.CompoundTag(entry));
        }
        root.put("palette", new NbtValue.ListTag(palette));
        root.put("blocks", new NbtValue.ListTag(blockEntries));
        root.put("entities", new NbtValue.ListTag(entityEntries));
        root.put("dataVersion", new NbtValue.IntTag(Bukkit.getUnsafe().getDataVersion()));
        return new NbtValue.CompoundTag(root);
    }

    private static NbtValue.CompoundTag paletteEntry(String state) {
        int bracket = state.indexOf('[');
        Map<String, NbtValue> entry = new LinkedHashMap<>();
        if (bracket < 0) {
            entry.put("Name", new NbtValue.StringTag(state));
            return new NbtValue.CompoundTag(entry);
        }
        entry.put("Name", new NbtValue.StringTag(state.substring(0, bracket)));
        String body = state.substring(bracket + 1, state.lastIndexOf(']'));
        Map<String, NbtValue> properties = new LinkedHashMap<>();
        for (String pair : body.split(",")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            properties.put(pair.substring(0, equals).trim(),
                    new NbtValue.StringTag(pair.substring(equals + 1).trim()));
        }
        entry.put("Properties", new NbtValue.CompoundTag(properties));
        return new NbtValue.CompoundTag(entry);
    }

    public static byte[] toGzip(NbtValue.CompoundTag root) {
        return NbtIo.writeGzip(root);
    }
}
