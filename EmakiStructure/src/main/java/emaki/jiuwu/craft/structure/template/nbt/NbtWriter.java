package emaki.jiuwu.craft.structure.template.nbt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public final class NbtWriter {

    private NbtWriter() {
    }

    public static byte[] write(NbtValue root) {
        return write(root, "");
    }

    public static byte[] write(NbtValue root, String rootName) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(rootName, "rootName");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeByte(root.tagId());
            writeString(output, rootName);
            writeValuePayload(output, root);
        } catch (IOException exception) {
            throw new NbtFormatException(NbtFormatException.Issue.IO_FAILURE, "nbt write failed", exception);
        }
        return buffer.toByteArray();
    }

    private static void writeValuePayload(DataOutputStream output, NbtValue value) throws IOException {
        switch (value) {
            case NbtValue.EndTag ignored -> {
            }
            case NbtValue.ByteTag tag -> output.writeByte(tag.value());
            case NbtValue.ShortTag tag -> output.writeShort(tag.value());
            case NbtValue.IntTag tag -> output.writeInt(tag.value());
            case NbtValue.LongTag tag -> output.writeLong(tag.value());
            case NbtValue.FloatTag tag -> output.writeFloat(tag.value());
            case NbtValue.DoubleTag tag -> output.writeDouble(tag.value());
            case NbtValue.StringTag tag -> writeString(output, tag.value());
            case NbtValue.BytesTag tag -> writeByteArray(output, tag);
            case NbtValue.ListTag tag -> writeList(output, tag);
            case NbtValue.CompoundTag tag -> writeCompound(output, tag);
            case NbtValue.IntsTag tag -> writeIntArray(output, tag);
            case NbtValue.LongsTag tag -> writeLongArray(output, tag);
        }
    }

    private static void writeCompound(DataOutputStream output, NbtValue.CompoundTag compound) throws IOException {
        for (Map.Entry<String, NbtValue> entry : compound.entries().entrySet()) {
            if (entry.getValue() instanceof NbtValue.EndTag) {
                throw new NbtFormatException(NbtFormatException.Issue.PARSE_BROKEN, "end tag inside compound");
            }
            output.writeByte(entry.getValue().tagId());
            writeString(output, entry.getKey());
            writeValuePayload(output, entry.getValue());
        }
        output.writeByte(0);
    }

    private static void writeList(DataOutputStream output, NbtValue.ListTag list) throws IOException {
        int elementTag = 0;
        for (NbtValue element : list.values()) {
            if (element instanceof NbtValue.EndTag) {
                throw new NbtFormatException(NbtFormatException.Issue.PARSE_BROKEN, "end tag inside list");
            }
            if (elementTag == 0) {
                elementTag = element.tagId();
            } else if (element.tagId() != elementTag) {
                throw new NbtFormatException(NbtFormatException.Issue.PARSE_BROKEN, "mixed list element types");
            }
        }
        output.writeByte(elementTag);
        output.writeInt(list.values().size());
        for (NbtValue element : list.values()) {
            writeValuePayload(output, element);
        }
    }

    private static void writeByteArray(DataOutputStream output, NbtValue.BytesTag tag) throws IOException {
        output.writeInt(tag.value().length);
        output.write(tag.value());
    }

    private static void writeIntArray(DataOutputStream output, NbtValue.IntsTag tag) throws IOException {
        output.writeInt(tag.value().length);
        for (int component : tag.value()) {
            output.writeInt(component);
        }
    }

    private static void writeLongArray(DataOutputStream output, NbtValue.LongsTag tag) throws IOException {
        output.writeInt(tag.value().length);
        for (long component : tag.value()) {
            output.writeLong(component);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 0xFFFF) {
            throw new NbtFormatException(NbtFormatException.Issue.PARSE_BROKEN, "string longer than " + encoded.length + " bytes");
        }
        output.writeShort(encoded.length);
        output.write(encoded);
    }
}
