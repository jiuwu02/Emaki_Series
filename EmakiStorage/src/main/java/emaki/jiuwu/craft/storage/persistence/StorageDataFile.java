package emaki.jiuwu.craft.storage.persistence;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

/**
 * Reads and writes one player's {@code storage.dat}.
 *
 * <p>The file is rewritten in full on every save rather than appended to. That is a deliberate
 * simplification over an append-log plus compactor: a player's file is written roughly once per
 * {@code autosave_interval} (300s by default) on an async file lane, so write amplification is
 * irrelevant here, while an append log would require a compactor, garbage-ratio bookkeeping,
 * dangling-id repair and two-file consistency — the highest-risk surface in the module. Every
 * write produces an already-compact file, so none of that machinery is needed.
 *
 * <p>Writes go to a sibling {@code .tmp} first, are re-read and validated, and only then replace
 * the live file atomically. A failed write leaves the previous file untouched and removes the tmp.
 *
 * <p>All methods here perform blocking IO and must be called from an async file lane, never from
 * an entity or global thread.
 */
public final class StorageDataFile {

    private static final String DATA_FILE_NAME = "storage.dat";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final DateTimeFormatter QUARANTINE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    /**
     * One decoded record.
     *
     * @param template   the stored item, amount already normalised to one
     * @param amount     how many units are stored
     * @param stackLimit the persisted per-entry ceiling, {@code 0} meaning inherit
     */
    public record Record(ItemStack template, long amount, long stackLimit) {
    }

    /**
     * Outcome of a load.
     *
     * @param records          successfully decoded records in file order
     * @param corruptRecords   how many records failed to decode and were quarantined
     * @param quarantineTarget where the corrupt bytes were written, {@code null} when none were
     */
    public record LoadResult(List<Record> records, int corruptRecords, Path quarantineTarget) {

        public static LoadResult empty() {
            return new LoadResult(List.of(), 0, null);
        }

        public boolean hasCorruption() {
            return corruptRecords > 0;
        }
    }

    private final Path dataRoot;
    private final Path quarantineRoot;

    /**
     * @param dataRoot       {@code plugins/EmakiStorage/data}
     * @param quarantineRoot {@code plugins/EmakiStorage/corrupt}
     */
    public StorageDataFile(Path dataRoot, Path quarantineRoot) {
        this.dataRoot = dataRoot;
        this.quarantineRoot = quarantineRoot;
    }

    /** {@return the directory holding one player's files} */
    public Path playerDirectory(UUID playerId) {
        return dataRoot.resolve(playerId.toString());
    }

    /** {@return the live data file for a player} */
    public Path dataFile(UUID playerId) {
        return playerDirectory(playerId).resolve(DATA_FILE_NAME);
    }

    /**
     * Loads every decodable record, quarantining the ones that fail.
     *
     * <p>A single unreadable record never fails the whole load: its raw bytes are moved to
     * {@code corrupt/<uuid>-<timestamp>.dat} and the remaining records load normally.
     *
     * @param playerId the storage owner
     * @return the decoded records plus corruption bookkeeping
     * @throws IOException when the file exists but its header is unusable, or IO itself fails
     */
    public LoadResult load(UUID playerId) throws IOException {
        Path file = dataFile(playerId);
        if (!Files.isRegularFile(file)) {
            return LoadResult.empty();
        }
        List<Record> records = new ArrayList<>();
        List<byte[]> corrupt = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            StorageCodec.readHeader(in);
            long declared = StorageCodec.readVarLong(in);
            for (long index = 0; index < declared; index++) {
                byte[] payload;
                long amount;
                long stackLimit;
                try {
                    payload = StorageCodec.readItemPayload(in);
                    amount = StorageCodec.readVarLong(in);
                    stackLimit = StorageCodec.readVarLong(in);
                } catch (IOException framingFailure) {
                    // Framing itself broke: nothing after this point can be trusted.
                    break;
                }
                try {
                    ItemStack template = StorageCodec.decodeItem(payload);
                    if (template == null || template.getType().isAir()) {
                        corrupt.add(StorageCodec.reframe(payload));
                        continue;
                    }
                    template.setAmount(1);
                    records.add(new Record(template, amount, stackLimit));
                } catch (RuntimeException decodeFailure) {
                    corrupt.add(StorageCodec.reframe(payload));
                }
            }
        }
        Path quarantine = corrupt.isEmpty() ? null : quarantine(playerId, corrupt);
        return new LoadResult(records, corrupt.size(), quarantine);
    }

    /**
     * Rewrites a player's data file in full.
     *
     * <p>Sequence: write tmp, re-read tmp and compare the record count, then atomically replace.
     * Any failure keeps the previous file and deletes the tmp.
     *
     * @param playerId the storage owner
     * @param records  the complete record set to persist
     * @throws IOException when writing, validating or replacing fails
     */
    public void save(UUID playerId, List<Record> records) throws IOException {
        Path directory = playerDirectory(playerId);
        Files.createDirectories(directory);
        Path target = directory.resolve(DATA_FILE_NAME);
        Path temp = directory.resolve(DATA_FILE_NAME + TEMP_SUFFIX);
        boolean replaced = false;
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp))) {
                StorageCodec.writeHeader(out);
                StorageCodec.writeVarLong(out, records.size());
                for (Record record : records) {
                    StorageCodec.writeItem(out, record.template());
                    StorageCodec.writeVarLong(out, Math.max(0L, record.amount()));
                    StorageCodec.writeVarLong(out, Math.max(0L, record.stackLimit()));
                }
            }
            verify(temp, records.size());
            moveReplacing(temp, target);
            replaced = true;
        } finally {
            if (!replaced) {
                Files.deleteIfExists(temp);
            }
        }
    }

    /**
     * Re-reads a freshly written file and confirms it decodes to the expected record count.
     *
     * @param file     the temporary file
     * @param expected how many records were written
     * @throws IOException when the file cannot be read back or the count differs
     */
    private void verify(Path file, int expected) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            StorageCodec.readHeader(in);
            long declared = StorageCodec.readVarLong(in);
            if (declared != expected) {
                throw new IOException("Verification failed: header declares " + declared
                        + " records but " + expected + " were written");
            }
            for (long index = 0; index < declared; index++) {
                StorageCodec.readItemPayload(in);
                StorageCodec.readVarLong(in);
                StorageCodec.readVarLong(in);
            }
            if (in.read() >= 0) {
                throw new IOException("Verification failed: trailing bytes after " + declared + " records");
            }
        }
    }

    /**
     * Writes corrupt record bytes into the quarantine directory.
     *
     * @param playerId the storage owner
     * @param frames   the reframed record payloads
     * @return the quarantine file that was written
     * @throws IOException when the quarantine directory or file cannot be created
     */
    private Path quarantine(UUID playerId, List<byte[]> frames) throws IOException {
        Files.createDirectories(quarantineRoot);
        String stamp = LocalDateTime.now().format(QUARANTINE_STAMP);
        Path target = quarantineRoot.resolve(playerId + "-" + stamp + ".dat");
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        StorageCodec.writeHeader(buffer);
        StorageCodec.writeVarLong(buffer, frames.size());
        for (byte[] frame : frames) {
            buffer.write(frame);
        }
        Files.write(target, buffer.toByteArray());
        return target;
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
