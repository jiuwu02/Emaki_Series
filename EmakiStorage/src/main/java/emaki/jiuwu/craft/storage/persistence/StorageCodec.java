package emaki.jiuwu.craft.storage.persistence;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.bukkit.inventory.ItemStack;

/**
 * Binary framing for the storage data file.
 *
 * <p>Item payloads use Paper's {@link ItemStack#serializeAsBytes()} / {@link
 * ItemStack#deserializeBytes(byte[])} pair. Those write the {@code DataVersion} into the root
 * compound and run the payload through Mojang's DataFixer on read, so stored items survive a
 * Minecraft upgrade. The {@code serialize()} map form is deliberately not used — Paper's own
 * javadoc calls it a "dangerous serialization system" for migrations.
 *
 * <p>The batch helpers ({@code serializeItemsAsBytes}) are also unused: they wrap the whole
 * collection in one array-versioned blob, so a single corrupt record would take down the entire
 * file. Framing each record separately keeps damage local to one entry.
 */
public final class StorageCodec {

    /** File magic: {@code EMSTOR} in ASCII. */
    static final byte[] MAGIC = { 'E', 'M', 'S', 'T', 'O', 'R' };

    /** Current on-disk format version. */
    static final int FORMAT_VERSION = 1;

    /** Guards against absurd frame lengths from a truncated or corrupt file. */
    static final int MAX_PAYLOAD_LENGTH = 4 * 1024 * 1024;

    private StorageCodec() {
    }

    /**
     * Writes an unsigned variable-length integer.
     *
     * @param out   the sink
     * @param value the value; must not be negative
     * @throws IOException when the sink fails
     */
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

    /**
     * Reads an unsigned variable-length integer.
     *
     * @param in the source
     * @return the decoded value
     * @throws IOException when the stream ends mid-value or the encoding is malformed
     */
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

    /**
     * Writes a length-prefixed item payload.
     *
     * @param out      the sink
     * @param template the item to encode; its amount is irrelevant to the stored identity
     * @throws IOException when encoding or the sink fails
     */
    public static void writeItem(OutputStream out, ItemStack template) throws IOException {
        byte[] payload = template.serializeAsBytes();
        writeVarLong(out, payload.length);
        out.write(payload);
    }

    /**
     * Reads a length-prefixed item payload.
     *
     * @param in the source
     * @return the raw payload bytes, left undecoded so a failing record can be quarantined
     * @throws IOException when the stream ends early or the declared length is implausible
     */
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

    /**
     * Decodes a quarantined payload back into an item.
     *
     * @param payload the raw bytes read by {@link #readItemPayload(InputStream)}
     * @return the decoded item, migrated to the running Minecraft version when needed
     */
    public static ItemStack decodeItem(byte[] payload) {
        return ItemStack.deserializeBytes(payload);
    }

    /**
     * Writes the file header.
     *
     * @param out the sink
     * @throws IOException when the sink fails
     */
    public static void writeHeader(OutputStream out) throws IOException {
        out.write(MAGIC);
        writeVarLong(out, FORMAT_VERSION);
    }

    /**
     * Reads and validates the file header.
     *
     * @param in the source
     * @return the declared format version
     * @throws IOException when the magic does not match or the version is unsupported
     */
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

    /**
     * Copies the bytes of one framed record, used to quarantine a record that failed to decode.
     *
     * @param payload the record payload
     * @return a self-contained frame with its length prefix restored
     */
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
