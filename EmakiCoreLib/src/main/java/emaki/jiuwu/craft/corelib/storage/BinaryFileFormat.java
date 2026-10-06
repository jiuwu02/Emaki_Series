package emaki.jiuwu.craft.corelib.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

final class BinaryFileFormat {

    private static final int MAX_KEY_BYTES = 1 << 20;
    private static final int MAX_ENTRY_BYTES = 1 << 28;

    private BinaryFileFormat() {
    }

    static Map<String, byte[]> read(Path path) {
        if (path == null || !Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try (InputStream inputStream = Files.newInputStream(path);
                DataInputStream input = new DataInputStream(inputStream)) {
            StoreHeader.verify(input);
            int count = input.readInt();
            if (count < 0) {
                throw new StorageException("存储条目数量无效: " + count);
            }
            Map<String, byte[]> entries = new LinkedHashMap<>();
            for (int index = 0; index < count; index++) {
                String key = new String(readChunk(input, MAX_KEY_BYTES), StandardCharsets.UTF_8);
                long rawLength = input.readLong();
                long checksum = input.readLong();
                byte[] compressed = readChunk(input, MAX_ENTRY_BYTES);
                entries.put(key, inflate(key, compressed, rawLength, checksum));
            }
            return entries;
        } catch (StorageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new StorageException("读取二进制存储失败: " + path, exception);
        }
    }

    static void write(Path path, Map<String, byte[]> entries) {
        Map<String, byte[]> ordered = new TreeMap<>(entries == null ? Map.of() : entries);
        try {
            Path absolute = path.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
            boolean moved = false;
            try {
                try (OutputStream outputStream = Files.newOutputStream(temp);
                        DataOutputStream output = new DataOutputStream(outputStream)) {
                    StoreHeader.write(output);
                    output.writeInt(ordered.size());
                    for (Map.Entry<String, byte[]> entry : ordered.entrySet()) {
                        byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                        byte[] raw = entry.getValue() == null ? new byte[0] : entry.getValue();
                        CRC32 checksum = new CRC32();
                        checksum.update(raw);
                        byte[] compressed = deflate(raw);
                        output.writeInt(keyBytes.length);
                        output.write(keyBytes);
                        output.writeLong(raw.length);
                        output.writeLong(checksum.getValue());
                        output.writeInt(compressed.length);
                        output.write(compressed);
                    }
                }
                moveReplacing(temp, absolute);
                moved = true;
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException exception) {
            throw new StorageException("写入二进制存储失败: " + path, exception);
        }
    }

    private static byte[] readChunk(DataInputStream input, int limit) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > limit) {
            throw new StorageException("存储区块长度无效: " + length);
        }
        byte[] chunk = input.readNBytes(length);
        if (chunk.length != length) {
            throw new StorageException("存储区块数据不完整");
        }
        return chunk;
    }

    private static byte[] inflate(String key, byte[] compressed, long rawLength, long checksum) {
        byte[] raw;
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(compressed))) {
            raw = inflater.readAllBytes();
        } catch (IOException exception) {
            throw new StorageException("存储条目解压失败: " + key, exception);
        }
        if (raw.length != rawLength) {
            throw new StorageException("存储条目长度校验失败: " + key);
        }
        CRC32 actual = new CRC32();
        actual.update(raw);
        if (actual.getValue() != checksum) {
            throw new StorageException("存储条目校验失败: " + key);
        }
        return raw;
    }

    private static byte[] deflate(byte[] value) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream deflaterStream = new DeflaterOutputStream(buffer, deflater)) {
            deflaterStream.write(value);
        } finally {
            deflater.end();
        }
        return buffer.toByteArray();
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
