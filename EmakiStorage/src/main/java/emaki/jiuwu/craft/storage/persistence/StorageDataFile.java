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
     * One signed increment inside a persisted reservation.
     *
     * @param template the stored item, amount already normalised to one
     * @param delta    signed unit count; negative withdraws, positive deposits
     */
    public record ReservationOpRecord(ItemStack template, long delta) {
    }

    /**
     * One persisted hold.
     *
     * @param reservationId   the reservation identity
     * @param expiresAtMillis wall-clock expiry in epoch milliseconds
     * @param ops             the signed increments the hold will apply on commit
     */
    public record ReservationRecord(UUID reservationId, long expiresAtMillis, List<ReservationOpRecord> ops) {
    }

    /**
     * Outcome of a load.
     *
     * @param records          successfully decoded records in file order
     * @param reservations     successfully decoded holds; always empty for a format {@code 1} file
     * @param corruptRecords   how many records failed to decode and were quarantined
     * @param quarantineTarget where the corrupt bytes were written, {@code null} when none were
     */
    public record LoadResult(List<Record> records, List<ReservationRecord> reservations, int corruptRecords,
            Path quarantineTarget) {

        public static LoadResult empty() {
            return new LoadResult(List.of(), List.of(), 0, null);
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
        List<ReservationRecord> reservations = new ArrayList<>();
        List<byte[]> corrupt = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            int version = StorageCodec.readHeader(in);
            long declared = StorageCodec.readVarLong(in);
            boolean framingIntact = true;
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
                    framingIntact = false;
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
            if (framingIntact && version >= StorageCodec.RESERVATION_FORMAT_VERSION) {
                reservations.addAll(readReservations(in));
            }
        }
        Path quarantine = corrupt.isEmpty() ? null : quarantine(playerId, corrupt);
        return new LoadResult(records, reservations, corrupt.size(), quarantine);
    }

    /**
     * Reads the reservation section.
     *
     * <p>A hold that cannot be decoded is dropped rather than quarantined. A reservation is a
     * short-lived promise, not player property: losing one releases stock back to the player, while
     * keeping an undecodable one would freeze that stock forever.
     */
    private List<ReservationRecord> readReservations(InputStream in) throws IOException {
        long declared;
        try {
            declared = StorageCodec.readVarLong(in);
        } catch (IOException missingSection) {
            return List.of();
        }
        List<ReservationRecord> reservations = new ArrayList<>();
        for (long index = 0; index < declared; index++) {
            try {
                UUID reservationId = StorageCodec.readUuid(in);
                long expiresAt = StorageCodec.readVarLong(in);
                long opCount = StorageCodec.readVarLong(in);
                List<ReservationOpRecord> ops = new ArrayList<>((int) Math.min(opCount, 2048L));
                boolean usable = true;
                for (long opIndex = 0; opIndex < opCount; opIndex++) {
                    byte[] payload = StorageCodec.readItemPayload(in);
                    int sign = in.read();
                    long magnitude = StorageCodec.readVarLong(in);
                    if (sign < 0) {
                        throw new IOException("Truncated reservation op sign");
                    }
                    try {
                        ItemStack template = StorageCodec.decodeItem(payload);
                        if (template == null || template.getType().isAir()) {
                            usable = false;
                            continue;
                        }
                        template.setAmount(1);
                        ops.add(new ReservationOpRecord(template, sign == 1 ? -magnitude : magnitude));
                    } catch (RuntimeException decodeFailure) {
                        usable = false;
                    }
                }
                if (usable && !ops.isEmpty()) {
                    reservations.add(new ReservationRecord(reservationId, expiresAt, ops));
                }
            } catch (IOException framingFailure) {
                break;
            }
        }
        return reservations;
    }

    /**
     * Rewrites a player's data file in full.
     *
     * <p>Sequence: write tmp, re-read tmp and compare the record count, then atomically replace.
     * Any failure keeps the previous file and deletes the tmp.
     *
     * @param playerId     the storage owner
     * @param records      the complete record set to persist
     * @param reservations the outstanding holds to persist
     * @throws IOException when writing, validating or replacing fails
     */
    public void save(UUID playerId, List<Record> records, List<ReservationRecord> reservations) throws IOException {
        Path directory = playerDirectory(playerId);
        Files.createDirectories(directory);
        Path target = directory.resolve(DATA_FILE_NAME);
        Path temp = directory.resolve(DATA_FILE_NAME + TEMP_SUFFIX);
        List<ReservationRecord> holds = reservations == null ? List.of() : reservations;
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
                StorageCodec.writeVarLong(out, holds.size());
                for (ReservationRecord reservation : holds) {
                    StorageCodec.writeUuid(out, reservation.reservationId());
                    StorageCodec.writeVarLong(out, Math.max(0L, reservation.expiresAtMillis()));
                    StorageCodec.writeVarLong(out, reservation.ops().size());
                    for (ReservationOpRecord op : reservation.ops()) {
                        StorageCodec.writeItem(out, op.template());
                        out.write(op.delta() < 0L ? 1 : 0);
                        StorageCodec.writeVarLong(out, Math.abs(op.delta()));
                    }
                }
            }
            verify(temp, records.size(), holds.size());
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
     * @param file             the temporary file
     * @param expected         how many records were written
     * @param expectedHolds    how many reservations were written
     * @throws IOException when the file cannot be read back or a count differs
     */
    private void verify(Path file, int expected, int expectedHolds) throws IOException {
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
            long declaredHolds = StorageCodec.readVarLong(in);
            if (declaredHolds != expectedHolds) {
                throw new IOException("Verification failed: header declares " + declaredHolds
                        + " reservations but " + expectedHolds + " were written");
            }
            for (long index = 0; index < declaredHolds; index++) {
                StorageCodec.readUuid(in);
                StorageCodec.readVarLong(in);
                long opCount = StorageCodec.readVarLong(in);
                for (long opIndex = 0; opIndex < opCount; opIndex++) {
                    StorageCodec.readItemPayload(in);
                    if (in.read() < 0) {
                        throw new IOException("Verification failed: truncated reservation op sign");
                    }
                    StorageCodec.readVarLong(in);
                }
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
