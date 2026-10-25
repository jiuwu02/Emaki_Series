package emaki.jiuwu.craft.structure.rollback;

import emaki.jiuwu.craft.structure.template.nbt.NbtWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

public final class GenerationJournal {

    public interface Entry {
    }

    public record BlockSnapshot(String world, int x, int y, int z,
                                String state, byte[] containerPayload) implements Entry {

        public BlockSnapshot {
            world = world == null ? "" : world;
            state = state == null ? "" : state;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BlockSnapshot snapshot
                    && world.equals(snapshot.world)
                    && x == snapshot.x && y == snapshot.y && z == snapshot.z
                    && state.equals(snapshot.state)
                    && Arrays.equals(containerPayload, snapshot.containerPayload);
        }

        @Override
        public int hashCode() {
            int result = world.hashCode();
            result = 31 * result + x;
            result = 31 * result + y;
            result = 31 * result + z;
            result = 31 * result + state.hashCode();
            result = 31 * result + Arrays.hashCode(containerPayload);
            return result;
        }
    }

    public record EntitySnapshot(String world, UUID id, int x, int y, int z) implements Entry {

        public EntitySnapshot {
            world = world == null ? "" : world;
        }
    }

    public static final class OverflowException extends RuntimeException {

        public OverflowException(String message) {
            super(message);
        }
    }

    static final String KIND_BLOCK = "B";
    static final String KIND_ENTITY = "E";
    static final String KIND_COMMIT = "COMMIT";
    static final String EMPTY_PAYLOAD = "-";

    private static final char FIELD_SEP = '|';
    private static final char LINE_SEP = '\t';

    private final Path file;
    private final long maxBytes;
    private final List<Entry> entries = new ArrayList<>();
    private BufferedWriter writer;
    private long bytes;
    private boolean closed;

    private GenerationJournal(Path file, long maxBytes, BufferedWriter writer) {
        this.file = file;
        this.maxBytes = maxBytes;
        this.writer = writer;
    }

    public static GenerationJournal open(Path journalDir, String txid, long maxBytes)
            throws IOException {
        Files.createDirectories(journalDir);
        Path file = journalDir.resolve(txid + ".log");
        BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return new GenerationJournal(file, maxBytes, writer);
    }

    public Path file() {
        return file;
    }

    public synchronized void appendBlocks(List<BlockSnapshot> snapshots) {
        if (closed || snapshots.isEmpty()) {
            return;
        }
        StringBuilder batch = new StringBuilder();
        for (BlockSnapshot snapshot : snapshots) {
            String payload = KIND_BLOCK + FIELD_SEP + snapshot.world() + FIELD_SEP
                    + snapshot.x() + FIELD_SEP + snapshot.y() + FIELD_SEP + snapshot.z()
                    + FIELD_SEP + snapshot.state() + FIELD_SEP + encodeContainer(snapshot);
            batch.append(encodeLine(payload)).append('\n');
        }
        byte[] raw = batch.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes + raw.length > maxBytes) {
            throw new OverflowException("journal 快照超限（上限 " + maxBytes + " 字节）");
        }
        write(raw, raw.length);
        entries.addAll(snapshots);
    }

    public synchronized void appendEntity(String world, UUID id, int x, int y, int z) {
        if (closed) {
            return;
        }
        entries.add(new EntitySnapshot(world, id, x, y, z));
        String payload = KIND_ENTITY + FIELD_SEP + world + FIELD_SEP + id
                + FIELD_SEP + x + FIELD_SEP + y + FIELD_SEP + z;
        try {
            byte[] raw = (encodeLine(payload) + "\n").getBytes(StandardCharsets.UTF_8);
            write(raw, raw.length);
        } catch (IllegalStateException ignored) {
        }
    }

    public synchronized void commit() {
        if (closed) {
            return;
        }
        try {
            String payload = KIND_COMMIT + "";
            writer.write(encodeLine(payload));
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {
        }
        closeQuietly();
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    public synchronized void closeQuietly() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (IOException ignored) {
        }
        writer = null;
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean isClosed() {
        synchronized (this) {
            return closed;
        }
    }

    private void write(byte[] raw, int length) {
        try {
            writer.write(new String(raw, StandardCharsets.UTF_8));
            writer.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("journal 写入失败: " + exception.getMessage(), exception);
        }
        bytes += length;
    }

    private static String encodeContainer(BlockSnapshot snapshot) {
        byte[] payload = snapshot.containerPayload();
        if (payload == null || payload.length == 0) {
            return EMPTY_PAYLOAD;
        }
        return Base64.getEncoder().encodeToString(payload);
    }

    static String encodeLine(String payload) {
        byte[] raw = payload.getBytes(StandardCharsets.UTF_8);
        CRC32 crc = new CRC32();
        crc.update(raw);
        return payload + LINE_SEP + raw.length + LINE_SEP + Long.toUnsignedString(crc.getValue());
    }
}
