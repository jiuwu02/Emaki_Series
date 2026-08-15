package emaki.jiuwu.craft.storage.persistence;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

public final class StorageCodec {

    static final byte[] MAGIC = { 'E', 'M', 'S', 'T', 'O', 'R' };

    static final int FORMAT_VERSION = 2;

    static final int RESERVATION_FORMAT_VERSION = 2;

    static final int MAX_PAYLOAD_LENGTH = 4 * 1024 * 1024;

    private StorageCodec() {
    }

    public static void writeVarLong(OutputStream out, long value) throws IOException {
        if (value < 0L) {
            throw new IOException("Refusing to encode negative varint: " + value);
        }
        long remaining = value;
        while (true) {
            int chunk = (int) (remaining & 0x7FL);
            remaining >>>= 7;
            if (remaining == 0L) {
                out.write(chunk);
                return;
            }
            out.write(chunk | 0x80);
        }
    }

    public static long readVarLong(InputStream in) throws IOException {
        long result = 0L;
        int shift = 0;
        while (shift < 64) {
            int read = in.read();
            if (read < 0) {
                throw new EOFException("Truncated varint");
            }
            result |= (long) (read & 0x7F) << shift;
            if ((read & 0x80) == 0) {
                return result;
            }
            shift += 7;
        }
        throw new IOException("Varint longer than 64 bits");
    }

    public static void writeItem(OutputStream out, ItemStack template) throws IOException {
        byte[] payload = template.serializeAsBytes();
        writeVarLong(out, payload.length);
        out.write(payload);
    }

    public static byte[] readItemPayload(InputStream in) throws IOException {
        long length = readVarLong(in);
        if (length <= 0L || length > MAX_PAYLOAD_LENGTH) {
            throw new IOException("Implausible item payload length: " + length);
        }
        byte[] payload = in.readNBytes((int) length);
        if (payload.length != length) {
            throw new EOFException("Truncated item payload: expected " + length + " bytes, got " + payload.length);
        }
        return payload;
    }

    public static ItemStack decodeItem(byte[] payload) {
        return ItemStack.deserializeBytes(payload);
    }

    public static void writeUuid(OutputStream out, UUID value) throws IOException {
        writeLongRaw(out, value.getMostSignificantBits());
        writeLongRaw(out, value.getLeastSignificantBits());
    }

    public static UUID readUuid(InputStream in) throws IOException {
        return new UUID(readLongRaw(in), readLongRaw(in));
    }

    private static void writeLongRaw(OutputStream out, long value) throws IOException {
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) ((value >>> shift) & 0xFFL));
        }
    }

    private static long readLongRaw(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(Long.BYTES);
        if (bytes.length != Long.BYTES) {
            throw new EOFException("Truncated 64-bit value");
        }
        long value = 0L;
        for (byte part : bytes) {
            value = (value << 8) | (part & 0xFFL);
        }
        return value;
    }

    public static void writeHeader(OutputStream out) throws IOException {
        out.write(MAGIC);
        writeVarLong(out, FORMAT_VERSION);
    }

    public static int readHeader(InputStream in) throws IOException {
        byte[] magic = in.readNBytes(MAGIC.length);
        if (magic.length != MAGIC.length) {
            throw new EOFException("Truncated header");
        }
        for (int index = 0; index < MAGIC.length; index++) {
            if (magic[index] != MAGIC[index]) {
                throw new IOException("Bad storage file magic");
            }
        }
        long version = readVarLong(in);
        if (version <= 0L || version > FORMAT_VERSION) {
            throw new IOException("Unsupported storage format version: " + version);
        }
        return (int) version;
    }

    public static byte[] reframe(byte[] payload) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(payload.length + 8);
        try {
            writeVarLong(buffer, payload.length);
            buffer.write(payload);
        } catch (IOException impossible) {
            return payload;
        }
        return buffer.toByteArray();
    }
}
