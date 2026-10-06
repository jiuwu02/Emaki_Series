package emaki.jiuwu.craft.corelib.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

final class SqliteFileFormat {

    private static final int VERSION = 1;
    private static final String TABLE_META = "store_meta";
    private static final String TABLE_ENTRIES = "store_entries";
    private static final String KEY_VERSION = "format_version";
    private static final String KEY_MAGIC = "magic";

    private SqliteFileFormat() {
    }

    static Map<String, byte[]> read(Path path) {
        if (path == null || !Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        try (Connection connection = open(path)) {
            requireVersion(connection);
            Map<String, byte[]> entries = new LinkedHashMap<>();
            try (Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery(
                            "SELECT entry_key, payload FROM " + TABLE_ENTRIES + " ORDER BY entry_key")) {
                while (result.next()) {
                    entries.put(result.getString(1), result.getBytes(2));
                }
            }
            return entries;
        } catch (StorageException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new StorageException("读取 SQLite 存储失败: " + path, exception);
        }
    }

    static void write(Path path, Map<String, byte[]> entries) {
        try {
            Path absolute = path.toAbsolutePath();
            Path parent = absolute.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = open(absolute)) {
                initialize(connection);
                connection.setAutoCommit(false);
                try {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("DELETE FROM " + TABLE_ENTRIES);
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT OR REPLACE INTO " + TABLE_ENTRIES + " (entry_key, payload) VALUES (?, ?)")) {
                        for (Map.Entry<String, byte[]> entry : (entries == null ? Map.<String, byte[]>of() : entries).entrySet()) {
                            statement.setString(1, entry.getKey());
                            statement.setBytes(2, entry.getValue() == null ? new byte[0] : entry.getValue());
                            statement.addBatch();
                        }
                        statement.executeBatch();
                    }
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            }
        } catch (StorageException exception) {
            throw exception;
        } catch (SQLException | IOException exception) {
            throw new StorageException("写入 SQLite 存储失败: " + path, exception);
        }
    }

    private static Connection open(Path path) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath());
    }

    private static void initialize(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE_META
                    + " (meta_key TEXT PRIMARY KEY, meta_value TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + TABLE_ENTRIES
                    + " (entry_key TEXT PRIMARY KEY, payload BLOB NOT NULL)");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR REPLACE INTO " + TABLE_META + " (meta_key, meta_value) VALUES (?, ?)")) {
            statement.setString(1, KEY_VERSION);
            statement.setString(2, String.valueOf(VERSION));
            statement.addBatch();
            statement.setString(1, KEY_MAGIC);
            statement.setString(2, String.valueOf(StoreHeader.MAGIC));
            statement.addBatch();
            statement.executeBatch();
        }
    }

    private static void requireVersion(Connection connection) throws SQLException {
        String version = null;
        String magic = null;
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT meta_key, meta_value FROM " + TABLE_META)) {
            while (result.next()) {
                String key = result.getString(1);
                if (KEY_VERSION.equals(key)) {
                    version = result.getString(2);
                } else if (KEY_MAGIC.equals(key)) {
                    magic = result.getString(2);
                }
            }
        }
        if (magic == null || !String.valueOf(StoreHeader.MAGIC).equals(magic)) {
            throw new StorageException("存储文件标识无效");
        }
        int parsed;
        try {
            parsed = Integer.parseInt(version);
        } catch (NumberFormatException exception) {
            throw new StorageException("存储格式版本无效", exception);
        }
        if (parsed != VERSION) {
            throw new StorageException("不支持的存储格式版本: " + parsed);
        }
    }
}
