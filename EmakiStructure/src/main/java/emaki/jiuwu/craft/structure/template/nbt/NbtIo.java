package emaki.jiuwu.craft.structure.template.nbt;

import static emaki.jiuwu.craft.structure.template.nbt.NbtFormatException.Issue.GZIP_HEADER;
import static emaki.jiuwu.craft.structure.template.nbt.NbtFormatException.Issue.IO_FAILURE;
import static emaki.jiuwu.craft.structure.template.nbt.NbtFormatException.Issue.OUTPUT_LIMIT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class NbtIo {

    public static final long DEFAULT_MAX_OUTPUT_BYTES = 256L * 1024 * 1024;

    private static final int GZIP_MAGIC_FIRST = 0x1F;
    private static final int GZIP_MAGIC_SECOND = 0x8B;

    private NbtIo() {
    }

    public static NbtReadResult readGzip(byte[] data) {
        return readGzip(data, DEFAULT_MAX_OUTPUT_BYTES, NbtReader.DEFAULT_MAX_DEPTH);
    }

    public static NbtReadResult readGzip(byte[] data, long maxOutputBytes, int maxDepth) {
        Objects.requireNonNull(data, "data");
        if (data.length < 2 || (data[0] & 0xFF) != GZIP_MAGIC_FIRST || (data[1] & 0xFF) != GZIP_MAGIC_SECOND) {
            throw new NbtFormatException(GZIP_HEADER, "missing gzip magic header");
        }
        byte[] inflated;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            inflated = inflate(gzip, maxOutputBytes);
        } catch (IOException exception) {
            throw new NbtFormatException(IO_FAILURE, "gzip decompression failed", exception);
        }
        return NbtReader.readRoot(inflated, maxDepth);
    }

    public static byte[] writeGzip(NbtValue root) {
        byte[] raw = NbtWriter.write(root);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.max(64, raw.length / 2));
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
            gzip.write(raw);
        } catch (IOException exception) {
            throw new NbtFormatException(IO_FAILURE, "gzip compression failed", exception);
        }
        return buffer.toByteArray();
    }

    private static byte[] inflate(GZIPInputStream gzip, long maxOutputBytes) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
        byte[] chunk = new byte[8192];
        long total = 0;
        int read;
        while ((read = gzip.read(chunk)) != -1) {
            total += read;
            if (total > maxOutputBytes) {
                throw new NbtFormatException(OUTPUT_LIMIT, "inflated size exceeds " + maxOutputBytes + " bytes");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
