package emaki.jiuwu.craft.structure.template.nbt;

import static emaki.jiuwu.craft.structure.template.nbt.NbtFormatException.Issue.BAD_ROOT_TYPE;
import static emaki.jiuwu.craft.structure.template.nbt.NbtFormatException.Issue.DEPTH_EXCEEDED;
import static emaki.jiuwu.craft.structure.template.nbt.NbtFormatException.Issue.OUTPUT_LIMIT;
import static emaki.jiuwu.craft.structure.template.nbt.NbtFormatException.Issue.PARSE_BROKEN;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NbtReader {

    public static final int DEFAULT_MAX_DEPTH = 64;

    private static final int TAG_END = 0;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int FIRST_TAG = 1;
    private static final int LAST_TAG = 12;

    private final byte[] payload;
    private final int maxDepth;
    private int cursor;
    private int skippedTagCount;

    private NbtReader(byte[] payload, int maxDepth) {
        this.payload = payload;
        this.maxDepth = Math.max(maxDepth, 0);
    }

    public static NbtReadResult readRoot(byte[] data) {
        return readRoot(data, DEFAULT_MAX_DEPTH);
    }

    public static NbtReadResult readRoot(byte[] data, int maxDepth) {
        Objects.requireNonNull(data, "data");
        NbtReader reader = new NbtReader(data, maxDepth);
        return reader.parseRoot();
    }

    public static NbtReadResult readRoot(InputStream source, int maxDepth, long maxBytes) throws IOException {
        Objects.requireNonNull(source, "source");
        return readRoot(readBounded(source, Math.max(maxBytes, 0)), maxDepth);
    }

    private static byte[] readBounded(InputStream source, long maxBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = source.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new NbtFormatException(OUTPUT_LIMIT, "stream exceeds " + maxBytes + " bytes");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private NbtReadResult parseRoot() {
        int rootTag = readUnsignedByte();
        if (rootTag != TAG_COMPOUND) {
            throw new NbtFormatException(BAD_ROOT_TYPE, "root tag type " + rootTag);
        }
        readString();
        return new NbtReadResult(readCompound(1), skippedTagCount);
    }

    private NbtValue.CompoundTag readCompound(int depth) {
        if (depth > maxDepth) {
            throw new NbtFormatException(DEPTH_EXCEEDED, "depth " + depth + " exceeds " + maxDepth);
        }
        Map<String, NbtValue> entries = new LinkedHashMap<>();
        while (true) {
            if (remaining() < 1) {
                throw new NbtFormatException(PARSE_BROKEN, "compound truncated at " + cursor);
            }
            int tag = readUnsignedByte();
            if (tag == TAG_END) {
                break;
            }
            if (tag < FIRST_TAG || tag > LAST_TAG) {
                skippedTagCount++;
                break;
            }
            String name = readString();
            entries.put(name, readValue(tag, depth));
        }
        return new NbtValue.CompoundTag(entries);
    }

    private NbtValue.ListTag readList(int depth) {
        if (depth > maxDepth) {
            throw new NbtFormatException(DEPTH_EXCEEDED, "depth " + depth + " exceeds " + maxDepth);
        }
        int elementTag = readUnsignedByte();
        int count = readInt();
        if (count < 0 || count > remaining()) {
            throw new NbtFormatException(PARSE_BROKEN, "list count " + count + " exceeds remaining " + remaining());
        }
        if (count == 0) {
            return new NbtValue.ListTag(List.of());
        }
        if (elementTag == TAG_END) {
            throw new NbtFormatException(PARSE_BROKEN, "non empty list of end tags");
        }
        if (elementTag < FIRST_TAG || elementTag > LAST_TAG) {
            skippedTagCount++;
            return new NbtValue.ListTag(List.of());
        }
        List<NbtValue> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(readValue(elementTag, depth));
        }
        return new NbtValue.ListTag(values);
    }

    private NbtValue readValue(int tag, int parentDepth) {
        return switch (tag) {
            case 1 -> new NbtValue.ByteTag((byte) readUnsignedByte());
            case 2 -> new NbtValue.ShortTag((short) readUnsignedShort());
            case 3 -> new NbtValue.IntTag(readInt());
            case 4 -> new NbtValue.LongTag(readLong());
            case 5 -> new NbtValue.FloatTag(Float.intBitsToFloat(readInt()));
            case 6 -> new NbtValue.DoubleTag(Double.longBitsToDouble(readLong()));
            case 7 -> readBytes();
            case 8 -> new NbtValue.StringTag(readString());
            case TAG_LIST -> readList(parentDepth + 1);
            case TAG_COMPOUND -> readCompound(parentDepth + 1);
            case 11 -> readInts();
            case 12 -> readLongs();
            default -> throw new NbtFormatException(PARSE_BROKEN, "unexpected tag type " + tag);
        };
    }

    private NbtValue.BytesTag readBytes() {
        int length = readLength(1);
        byte[] value = new byte[length];
        System.arraycopy(payload, cursor, value, 0, length);
        cursor += length;
        return new NbtValue.BytesTag(value);
    }

    private NbtValue.IntsTag readInts() {
        int length = readLength(4);
        int[] value = new int[length];
        for (int index = 0; index < length; index++) {
            value[index] = readInt();
        }
        return new NbtValue.IntsTag(value);
    }

    private NbtValue.LongsTag readLongs() {
        int length = readLength(8);
        long[] value = new long[length];
        for (int index = 0; index < length; index++) {
            value[index] = readLong();
        }
        return new NbtValue.LongsTag(value);
    }

    private int readLength(int bytesPerElement) {
        int length = readInt();
        if (length < 0 || length > remaining() / bytesPerElement) {
            throw new NbtFormatException(PARSE_BROKEN, "array length " + length + " exceeds remaining " + remaining());
        }
        return length;
    }

    private String readString() {
        int length = readUnsignedShort();
        if (length > remaining()) {
            throw new NbtFormatException(PARSE_BROKEN, "string length " + length + " exceeds remaining " + remaining());
        }
        String value = new String(payload, cursor, length, StandardCharsets.UTF_8);
        cursor += length;
        return value;
    }

    private int readUnsignedByte() {
        require(1);
        return payload[cursor++] & 0xFF;
    }

    private int readUnsignedShort() {
        require(2);
        int value = ((payload[cursor] & 0xFF) << 8) | (payload[cursor + 1] & 0xFF);
        cursor += 2;
        return value;
    }

    private int readInt() {
        require(4);
        int value = ((payload[cursor] & 0xFF) << 24)
                | ((payload[cursor + 1] & 0xFF) << 16)
                | ((payload[cursor + 2] & 0xFF) << 8)
                | (payload[cursor + 3] & 0xFF);
        cursor += 4;
        return value;
    }

    private long readLong() {
        require(8);
        long value = 0;
        for (int index = 0; index < 8; index++) {
            value = (value << 8) | (payload[cursor + index] & 0xFFL);
        }
        cursor += 8;
        return value;
    }

    private void require(int bytes) {
        if (bytes > remaining()) {
            throw new NbtFormatException(PARSE_BROKEN, "need " + bytes + " bytes at " + cursor);
        }
    }

    private int remaining() {
        return payload.length - cursor;
    }
}
