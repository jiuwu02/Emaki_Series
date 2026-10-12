package emaki.jiuwu.craft.structure.rollback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

public final class JournalReader {

    public record Parsed(List<GenerationJournal.Entry> entries, boolean committed) {
    }

    public static final class CorruptedException extends RuntimeException {

        public CorruptedException(String message) {
            super(message);
        }
    }

    private JournalReader() {
    }

    public static Parsed read(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<GenerationJournal.Entry> entries = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isEmpty()) {
                continue;
            }
            boolean last = index == lines.size() - 1;
            String payload = validate(line, last, index);
            if (payload == null) {
                break;
            }
            if (GenerationJournal.KIND_COMMIT.equals(payload)) {
                return new Parsed(List.copyOf(entries), true);
            }
            entries.add(parse(payload, index));
        }
        return new Parsed(List.copyOf(entries), false);
    }

    private static String validate(String line, boolean last, int index) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 3) {
            return truncatedOrCorrupt(last, index);
        }
        int expectedLength;
        long expectedCrc;
        try {
            expectedLength = Integer.parseInt(parts[1]);
            expectedCrc = Long.parseUnsignedLong(parts[2]);
        } catch (NumberFormatException exception) {
            return truncatedOrCorrupt(last, index);
        }
        byte[] raw = parts[0].getBytes(StandardCharsets.UTF_8);
        if (raw.length != expectedLength) {
            return truncatedOrCorrupt(last, index);
        }
        CRC32 crc = new CRC32();
        crc.update(raw);
        if (crc.getValue() != expectedCrc) {
            return truncatedOrCorrupt(last, index);
        }
        return parts[0];
    }

    private static String truncatedOrCorrupt(boolean last, int index) {
        if (last) {
            return null;
        }
        throw new CorruptedException("journal 第 " + (index + 1) + " 行校验失败");
    }

    private static GenerationJournal.Entry parse(String payload, int index) {
        String[] fields = payload.split("\\|", -1);
        if (fields.length == 7 && GenerationJournal.KIND_BLOCK.equals(fields[0])) {
            int x;
            int y;
            int z;
            try {
                x = Integer.parseInt(fields[2]);
                y = Integer.parseInt(fields[3]);
                z = Integer.parseInt(fields[4]);
            } catch (NumberFormatException exception) {
                throw new CorruptedException("journal 第 " + (index + 1) + " 行坐标非法");
            }
            byte[] container = null;
            if (!GenerationJournal.EMPTY_PAYLOAD.equals(fields[6])) {
                try {
                    container = Base64.getDecoder().decode(fields[6]);
                } catch (IllegalArgumentException exception) {
                    throw new CorruptedException("journal 第 " + (index + 1) + " 行载荷非法");
                }
            }
            return new GenerationJournal.BlockSnapshot(fields[1], x, y, z, fields[5], container);
        }
        if (fields.length == 6 && GenerationJournal.KIND_ENTITY.equals(fields[0])) {
            int x;
            int y;
            int z;
            UUID id;
            try {
                id = UUID.fromString(fields[2]);
                x = Integer.parseInt(fields[3]);
                y = Integer.parseInt(fields[4]);
                z = Integer.parseInt(fields[5]);
            } catch (IllegalArgumentException exception) {
                throw new CorruptedException("journal 第 " + (index + 1) + " 行实体记录非法");
            }
            return new GenerationJournal.EntitySnapshot(fields[1], id, x, y, z);
        }
        throw new CorruptedException("journal 第 " + (index + 1) + " 行无法识别");
    }
}
