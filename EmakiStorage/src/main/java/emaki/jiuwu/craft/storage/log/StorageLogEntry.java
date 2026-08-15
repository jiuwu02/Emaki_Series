package emaki.jiuwu.craft.storage.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

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

    public String fileName() {
        return timestamp.format(DATE) + ".log";
    }

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
