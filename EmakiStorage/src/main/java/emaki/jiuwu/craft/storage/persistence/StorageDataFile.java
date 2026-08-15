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

public final class StorageDataFile {

    private static final String DATA_FILE_NAME = "storage.dat";
    private static final String TEMP_SUFFIX = ".tmp";
    private static final DateTimeFormatter QUARANTINE_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    public record Record(ItemStack template, long amount, long stackLimit) {
    }

    public record ReservationOpRecord(ItemStack template, long delta) {
    }

    public record ReservationRecord(UUID reservationId, long expiresAtMillis, List<ReservationOpRecord> ops) {
    }

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

    public StorageDataFile(Path dataRoot, Path quarantineRoot) {
        this.dataRoot = dataRoot;
        this.quarantineRoot = quarantineRoot;
    }

    public Path playerDirectory(UUID playerId) {
        return dataRoot.resolve(playerId.toString());
    }

    public Path dataFile(UUID playerId) {
        return playerDirectory(playerId).resolve(DATA_FILE_NAME);
    }

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
