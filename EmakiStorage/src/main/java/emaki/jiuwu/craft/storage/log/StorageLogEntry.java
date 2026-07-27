package emaki.jiuwu.craft.storage.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

/**
 * One immutable flow-log record.
 *
 * <p>The item is identified by its {@link org.bukkit.Material} key or CoreLib ItemSource id —
 * never by a full NBT dump. The log does not carry data-recovery duty (that is the data file's
 * job), and an NBT blob would push a single line into the kilobytes while making it unreadable.
 *
 * <p>Records are built on the owning entity thread and handed to the async writer, so every field
 * must already be a plain immutable value.
 */
public record StorageLogEntry(UUID playerId,
        LocalDateTime timestamp,
        StorageOperationType type,
        String itemId,
        String deltaText,
        long balanceAfter,
        StorageOperationSource source,
        @Nullable String note) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);
    private static final String PLACEHOLDER = "-";

    /**
     * Builds a deposit or withdrawal record.
     *
     * @param playerId     the storage owner
     * @param type         the operation class
     * @param itemId       the material key or ItemSource id
     * @param delta        signed change; rendered with an explicit sign
     * @param balanceAfter the entry amount after the operation
     * @param source       the originating surface
     * @param note         optional trailing note, such as a unique-item marker
     * @return the record
     */
    public static StorageLogEntry of(UUID playerId,
            StorageOperationType type,
            String itemId,
            long delta,
            long balanceAfter,
            StorageOperationSource source,
            @Nullable String note) {
        return new StorageLogEntry(playerId, LocalDateTime.now(), type,
                itemId, signed(delta), balanceAfter, source, note);
    }

    /**
     * Builds a record whose change is not a plain signed amount, such as {@code +10slots}
     * or an absolute {@code =0} assignment.
     *
     * @param playerId     the storage owner
     * @param type         the operation class
     * @param itemId       the material key or ItemSource id, or {@code null} for slot operations
     * @param deltaText    the pre-rendered change text
     * @param balanceAfter the resulting balance
     * @param source       the originating surface
     * @param note         optional trailing note
     * @return the record
     */
    public static StorageLogEntry raw(UUID playerId,
            StorageOperationType type,
            @Nullable String itemId,
            String deltaText,
            long balanceAfter,
            StorageOperationSource source,
            @Nullable String note) {
        return new StorageLogEntry(playerId, LocalDateTime.now(), type,
                itemId == null || itemId.isBlank() ? PLACEHOLDER : itemId,
                deltaText, balanceAfter, source, note);
    }

    /** {@return the file name this record belongs to, {@code yyyy-MM-dd.log}} */
    public String fileName() {
        return timestamp.format(DATE) + ".log";
    }

    /**
     * {@return the tab-separated single-line form}
     *
     * <p>Tab separation keeps the file greppable and readable in a plain editor. The date lives in
     * the file name, so only the time of day is written per line.
     */
    public String render() {
        StringBuilder line = new StringBuilder(64);
        line.append(timestamp.format(TIME)).append('\t')
                .append(type.name()).append('\t')
                .append(blankToPlaceholder(itemId)).append('\t')
                .append(blankToPlaceholder(deltaText)).append('\t')
                .append(balanceAfter).append('\t')
                .append(source.id());
        if (note != null && !note.isBlank()) {
            line.append('\t').append(note.trim());
        }
        return line.toString();
    }

    private static String signed(long delta) {
        return delta >= 0L ? "+" + delta : String.valueOf(delta);
    }

    private static String blankToPlaceholder(String value) {
        return value == null || value.isBlank() ? PLACEHOLDER : value;
    }
}
