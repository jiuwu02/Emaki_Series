package emaki.jiuwu.craft.structure.rollback;

import emaki.jiuwu.craft.structure.template.nbt.NbtWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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

    public static final class JournalWriteException extends RuntimeException {

        public JournalWriteException(String message, Throwable cause) {
            super(message, cause);
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
    private FileChannel channel;
    private long bytes;
    private boolean closed;
    private IOException writeFailure;

    private GenerationJournal(Path file, long maxBytes, FileChannel channel) {
        this.file = file;
        this.maxBytes = maxBytes;
        this.channel = channel;
    }

    public static GenerationJournal open(Path journalDir, String txid, long maxBytes)
            throws IOException {
        Files.createDirectories(journalDir);
        Path file = journalDir.resolve(txid + ".log");
        FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return new GenerationJournal(file, maxBytes, channel);
    }

    public Path file() {
        return file;
    }

    public synchronized void appendBlocks(List<BlockSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        if (closed) {
            throw new JournalWriteException("journal 已关闭, 快照无法写入: " + file,
                    new IllegalStateException("journal closed"));
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
        write(raw);
        entries.addAll(snapshots);
    }

    public synchronized void appendEntity(String world, UUID id, int x, int y, int z) {
        if (closed) {
            throw new JournalWriteException("journal 已关闭, 实体记录无法写入: " + file,
                    new IllegalStateException("journal closed"));
        }
        String payload = KIND_ENTITY + FIELD_SEP + world + FIELD_SEP + id
                + FIELD_SEP + x + FIELD_SEP + y + FIELD_SEP + z;
        byte[] raw = (encodeLine(payload) + "\n").getBytes(StandardCharsets.UTF_8);
        write(raw);
        entries.add(new EntitySnapshot(world, id, x, y, z));
    }

    public synchronized boolean commit() {
        if (closed) {
            throw new JournalWriteException("journal 已关闭, 无法写入提交标记: " + file,
                    writeFailure != null ? writeFailure : new IllegalStateException("journal closed"));
        }
        if (writeFailure != null) {
            close();
            throw new JournalWriteException(
                    "journal 存在未落盘的记录, 拒绝提交并保留日志: " + file, writeFailure);
        }
        try {
            write((encodeLine(KIND_COMMIT) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (JournalWriteException exception) {
            close();
            throw exception;
        }
        if (!close()) {
            throw new JournalWriteException(
                    "journal 未可靠落盘, 拒绝删除日志: " + file, writeFailure);
        }
        try {
            Files.deleteIfExists(file);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public synchronized boolean close() {
        if (closed) {
            return writeFailure == null;
        }
        closed = true;
        IOException failure = null;
        try {
            if (channel != null) {
                channel.force(true);
            }
        } catch (IOException exception) {
            failure = exception;
        }
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        channel = null;
        if (failure != null) {
            recordFailure(failure);
        }
        return writeFailure == null;
    }

    public synchronized List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean isClosed() {
        synchronized (this) {
            return closed;
        }
    }

    public synchronized boolean isHealthy() {
        return !closed && writeFailure == null;
    }

    private void write(byte[] raw) {
        if (channel == null) {
            throw new JournalWriteException("journal 已关闭, 无法写入: " + file,
                    new IllegalStateException("journal closed"));
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(raw);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            bytes += raw.length;
        } catch (IOException exception) {
            recordFailure(exception);
            throw new JournalWriteException("journal 写入失败: " + exception.getMessage(), exception);
        }
    }

    private void recordFailure(IOException exception) {
        if (writeFailure == null) {
            writeFailure = exception;
        } else {
            writeFailure.addSuppressed(exception);
        }
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
