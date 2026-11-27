package emaki.jiuwu.craft.dungeon.random.gen;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import emaki.jiuwu.craft.dungeon.random.layout.Placement.CorridorRun;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.Layout;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.LinkRecord;
import emaki.jiuwu.craft.dungeon.random.layout.Placement.RoomPlacement;
import emaki.jiuwu.craft.dungeon.random.model.TemplateMeta;
import emaki.jiuwu.craft.dungeon.random.nbt.StructureNbt;

public final class GenerationFingerprint {

    public static String templatePool(Map<String, TemplateMeta> metas,
            Map<String, StructureNbt.StructureData> templates) {
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(metas).forEach((id, meta) -> {
            canonical.append(id).append(':');
            write(canonical, meta.toMap());
            StructureNbt.StructureData data = templates.get(id);
            if (data != null) {
                write(canonical, data);
            }
        });
        return digest(canonical.toString());
    }

    public static String layout(Layout layout) {
        StringBuilder canonical = new StringBuilder();
        for (RoomPlacement room : layout.rooms()) {
            write(canonical, List.of(room.nodeId(), room.typeId(), room.variant().key(),
                    room.originX(), room.originY(), room.originZ()));
        }
        for (CorridorRun corridor : layout.corridors()) {
            List<Integer> cells = new ArrayList<>();
            for (var cell : corridor.cells()) {
                cells.add(cell.x());
                cells.add(cell.y());
                cells.add(cell.z());
                cells.add(cell.floorCell() ? 1 : 0);
            }
            write(canonical, cells);
        }
        for (LinkRecord link : layout.links()) {
            write(canonical, List.of(link.parentNodeId(), link.childNodeId(),
                    link.parentAnchorId(), link.childAnchorId(), link.form(), link.heightDiff()));
        }
        write(canonical, new TreeMap<>(layout.logicalFloors()));
        return digest(canonical.toString());
    }

    public static void write(StringBuilder output, Object value) {
        if (value == null) {
            output.append("n;");
        } else if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            output.append("m").append(sorted.size()).append('{');
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                write(output, entry.getKey());
                write(output, entry.getValue());
            }
            output.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            iterable.forEach(items::add);
            output.append('l').append(items.size()).append('[');
            for (Object item : items) {
                write(output, item);
            }
            output.append(']');
        } else if (value instanceof Number number) {
            String text = number instanceof Double || number instanceof Float
                    ? Double.toString(number.doubleValue()) : Long.toString(number.longValue());
            output.append('d').append(text.length()).append(':').append(text);
        } else if (value instanceof Boolean bool) {
            output.append(bool ? "b1" : "b0");
        } else if (value instanceof StructureNbt.StructureData data) {
            output.append('t').append(data.sizeX()).append(',').append(data.sizeY()).append(',').append(data.sizeZ()).append('{');
            for (var entry : data.palette()) {
                write(output, entry.blockDataString());
            }
            for (var entry : data.blocks()) {
                write(output, List.of(entry.x(), entry.y(), entry.z(), entry.state()));
            }
            output.append('}');
        } else if (value instanceof long[] array) {
            output.append("a").append(array.length).append('[');
            for (long item : array) {
                output.append(item).append(';');
            }
            output.append(']');
        } else {
            String text = String.valueOf(value);
            output.append('s').append(text.length()).append(':').append(text);
        }
    }

    public static String digest(String canonical) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) {
                result.append(Character.forDigit((value >> 4) & 15, 16));
                result.append(Character.forDigit(value & 15, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private GenerationFingerprint() {
    }
}
