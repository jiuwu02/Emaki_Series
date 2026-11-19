package emaki.jiuwu.craft.dungeon.random.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class StructureNbt {

    public record PaletteEntry(String name, Map<String, String> properties) {

        public String blockDataString() {
            if (properties == null || properties.isEmpty()) {
                return name;
            }
            StringBuilder builder = new StringBuilder(name).append('[');
            boolean first = true;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
                first = false;
            }
            return builder.append(']').toString();
        }

        public static PaletteEntry parse(String blockDataString) {
            String trimmed = blockDataString.trim();
            int open = trimmed.indexOf('[');
            if (open < 0) {
                return new PaletteEntry(trimmed, Map.of());
            }
            String name = trimmed.substring(0, open);
            int close = trimmed.lastIndexOf(']');
            String body = close > open ? trimmed.substring(open + 1, close) : trimmed.substring(open + 1);
            Map<String, String> properties = new LinkedHashMap<>();
            for (String part : body.split(",")) {
                int eq = part.indexOf('=');
                if (eq > 0) {
                    properties.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
            }
            return new PaletteEntry(name, properties);
        }
    }

    public record BlockEntry(int x, int y, int z, int state) {
    }

    public record StructureData(int sizeX, int sizeY, int sizeZ,
            List<PaletteEntry> palette, List<BlockEntry> blocks) {
    }

    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;

    public static void write(Path target, StructureData data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (OutputStream gzip = new GZIPOutputStream(bytes);
                DataOutputStream out = new DataOutputStream(gzip)) {
            out.writeByte(TAG_COMPOUND);
            writeString(out, "");
            writeInt(out, "size", new int[] {data.sizeX(), data.sizeY(), data.sizeZ()});
            out.writeByte(TAG_LIST);
            writeString(out, "palette");
            out.writeByte(TAG_COMPOUND);
            out.writeInt(data.palette().size());
            for (PaletteEntry entry : data.palette()) {
                out.writeByte(TAG_STRING);
                writeString(out, "Name");
                writeString(out, entry.name());
                out.writeByte(TAG_COMPOUND);
                writeString(out, "Properties");
                for (Map.Entry<String, String> property : entry.properties().entrySet()) {
                    out.writeByte(TAG_STRING);
                    writeString(out, property.getKey());
                    writeString(out, property.getValue());
                }
                out.writeByte(TAG_END);
                out.writeByte(TAG_END);
            }
            out.writeByte(TAG_LIST);
            writeString(out, "blocks");
            out.writeByte(TAG_COMPOUND);
            out.writeInt(data.blocks().size());
            for (BlockEntry entry : data.blocks()) {
                out.writeByte(TAG_INT_ARRAY);
                writeString(out, "pos");
                out.writeInt(3);
                out.writeInt(entry.x());
                out.writeInt(entry.y());
                out.writeInt(entry.z());
                out.writeByte(TAG_INT);
                writeString(out, "state");
                out.writeInt(entry.state());
                out.writeByte(TAG_END);
            }
            out.writeByte(TAG_LIST);
            writeString(out, "entities");
            out.writeByte(TAG_COMPOUND);
            out.writeInt(0);
            out.writeByte(TAG_END);
        }
        Files.write(target, bytes.toByteArray());
    }

    public static StructureData read(Path source) throws IOException {
        byte[] raw = Files.readAllBytes(source);
        try (InputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw));
                DataInputStream in = new DataInputStream(gzip)) {
            int rootType = in.readByte();
            if (rootType != TAG_COMPOUND) {
                throw new IOException("structure root is not a compound tag");
            }
            in.readUTF();
            int sizeX = 1;
            int sizeY = 1;
            int sizeZ = 1;
            List<PaletteEntry> palette = new ArrayList<>();
            List<BlockEntry> blocks = new ArrayList<>();
            while (true) {
                int type = in.readByte();
                if (type == TAG_END) {
                    break;
                }
                String name = readString(in);
                switch (type) {
                    case TAG_INT_ARRAY -> {
                        int[] values = readIntArrayPayload(in);
                        if ("size".equals(name) && values.length >= 3) {
                            sizeX = values[0];
                            sizeY = values[1];
                            sizeZ = values[2];
                        }
                    }
                    case TAG_LIST -> readListPayload(in, name, palette, blocks);
                    default -> skipPayload(in, type);
                }
            }
            return new StructureData(sizeX, sizeY, sizeZ, palette, blocks);
        }
    }

    private static void readListPayload(DataInputStream in, String name,
            List<PaletteEntry> palette, List<BlockEntry> blocks) throws IOException {
        int elementType = in.readByte();
        int count = in.readInt();
        if ("palette".equals(name) && elementType == TAG_COMPOUND) {
            for (int i = 0; i < count; i++) {
                palette.add(readPaletteEntry(in));
            }
            return;
        }
        if ("blocks".equals(name) && elementType == TAG_COMPOUND) {
            for (int i = 0; i < count; i++) {
                blocks.add(readBlockEntry(in));
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            skipPayload(in, elementType);
        }
    }

    private static PaletteEntry readPaletteEntry(DataInputStream in) throws IOException {
        String paletteName = "";
        Map<String, String> properties = new LinkedHashMap<>();
        while (true) {
            int type = in.readByte();
            if (type == TAG_END) {
                break;
            }
            String name = readString(in);
            if (type == TAG_STRING && "Name".equals(name)) {
                paletteName = readString(in);
            } else if (type == TAG_COMPOUND && "Properties".equals(name)) {
                while (true) {
                    int propertyType = in.readByte();
                    if (propertyType == TAG_END) {
                        break;
                    }
                    String key = readString(in);
                    String value = readString(in);
                    properties.put(key, value);
                }
            } else {
                skipPayload(in, type);
            }
        }
        return new PaletteEntry(paletteName, properties);
    }

    private static BlockEntry readBlockEntry(DataInputStream in) throws IOException {
        int x = 0;
        int y = 0;
        int z = 0;
        int state = 0;
        while (true) {
            int type = in.readByte();
            if (type == TAG_END) {
                break;
            }
            String name = readString(in);
            if (type == TAG_INT_ARRAY && "pos".equals(name)) {
                int[] values = readIntArrayPayload(in);
                if (values.length >= 3) {
                    x = values[0];
                    y = values[1];
                    z = values[2];
                }
            } else if (type == TAG_INT && "state".equals(name)) {
                state = in.readInt();
            } else {
                skipPayload(in, type);
            }
        }
        return new BlockEntry(x, y, z, state);
    }

    private static void skipPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case TAG_BYTE -> in.readByte();
            case TAG_SHORT -> in.readShort();
            case TAG_INT -> in.readInt();
            case TAG_LONG -> in.readLong();
            case TAG_FLOAT -> in.readFloat();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_BYTE_ARRAY -> in.skipBytes(in.readInt());
            case TAG_STRING -> readString(in);
            case TAG_LIST -> {
                int elementType = in.readByte();
                int count = in.readInt();
                for (int i = 0; i < count; i++) {
                    skipPayload(in, elementType);
                }
            }
            case TAG_COMPOUND -> {
                while (true) {
                    int childType = in.readByte();
                    if (childType == TAG_END) {
                        break;
                    }
                    readString(in);
                    skipPayload(in, childType);
                }
            }
            case TAG_INT_ARRAY -> in.skipBytes(in.readInt() * 4);
            default -> {
            }
        }
    }

    private static int[] readIntArrayPayload(DataInputStream in) throws IOException {
        int count = in.readInt();
        int[] values = new int[count];
        for (int i = 0; i < count; i++) {
            values[i] = in.readInt();
        }
        return values;
    }

    private static void writeInt(DataOutputStream out, String name, int[] values) throws IOException {
        out.writeByte(TAG_INT_ARRAY);
        writeString(out, name);
        out.writeInt(values.length);
        for (int value : values) {
            out.writeInt(value);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
