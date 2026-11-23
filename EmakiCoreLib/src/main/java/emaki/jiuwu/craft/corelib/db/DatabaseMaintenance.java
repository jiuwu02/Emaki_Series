package emaki.jiuwu.craft.corelib.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.sql.DataSource;

import emaki.jiuwu.craft.corelib.api.yaml.YamlFiles;
import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;
import emaki.jiuwu.craft.corelib.api.util.Jsons;

public final class DatabaseMaintenance {

    private static final List<String> TRANSFER_TABLES = List.of(
            "emaki_player", "emaki_player_name", "emaki_player_data",
            "emaki_shared_state", "emaki_shared_counter", "emaki_shared_seq");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    private final Supplier<DataSource> dataSource;
    private final DatabaseDialect dialect;
    private final DbEngine engine;
    private final DatabaseConfig config;
    private final SingleWriterGate writeGate;

    DatabaseMaintenance(Supplier<DataSource> dataSource, DatabaseDialect dialect, DbEngine engine,
            DatabaseConfig config, SingleWriterGate writeGate) {
        this.dataSource = dataSource;
        this.dialect = dialect;
        this.engine = engine;
        this.config = config;
        this.writeGate = writeGate;
    }

    public record TableCount(String table, long rows) {
    }

    public record CheckReport(String engine, String configuredEngine, boolean connected,
            int appliedSteps, int totalSteps, List<TableCount> counts, String error) {
    }

    public record ExportReport(Path file, List<TableCount> counts) {
    }

    public record ImportReport(Path file, List<TableCount> counts) {
    }

    public record CopyReport(String sourceEngine, String targetEngine, List<TableCount> counts) {
    }

    private record TableDump(byte[] bytes, long rows) {
    }

    public CheckReport check() {
        int totalSteps = SchemaMigrator.totalSteps(dialect);
        try (Connection connection = dataSource.get().getConnection()) {
            int appliedSteps = 0;
            try {
                appliedSteps = SchemaMigrator.appliedSteps(connection).size();
            } catch (SQLException schemaFailure) {
                appliedSteps = 0;
            }
            List<TableCount> counts = new ArrayList<>();
            for (String table : checkTables()) {
                counts.add(new TableCount(table, countRows(connection, table)));
            }
            return new CheckReport(engine.id(), config.engine(), true, appliedSteps, totalSteps, counts, null);
        } catch (SQLException exception) {
            return new CheckReport(engine.id(), config.engine(), false, 0, totalSteps, List.of(),
                    String.valueOf(exception));
        }
    }

    public ExportReport exportTo(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建导出目录: " + directory, exception);
        }
        Path target = directory.resolve("db-export-" + TIMESTAMP_FORMAT.format(LocalDateTime.now()) + ".zip");
        List<TableCount> counts = new ArrayList<>();
        List<Map<String, Object>> manifestTables = new ArrayList<>();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (Connection connection = dataSource.get().getConnection()) {
            for (String table : TRANSFER_TABLES) {
                TableDump dump = dumpTable(connection, table);
                byte[] bytes = dump.bytes();
                entries.put(table + ".jsonl", bytes);
                counts.add(new TableCount(table, dump.rows()));
                Map<String, Object> manifestTable = new LinkedHashMap<>();
                manifestTable.put("table", table);
                manifestTable.put("file", table + ".jsonl");
                manifestTable.put("rows", dump.rows());
                manifestTable.put("sha256", sha256Hex(bytes));
                manifestTables.add(manifestTable);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("数据库导出读取失败", exception);
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("format", "emaki-db-export");
        manifest.put("format_version", 1L);
        manifest.put("exported_at", System.currentTimeMillis());
        manifest.put("source_engine", engine.id());
        manifest.put("tables", manifestTables);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            writeEntry(zip, "manifest.json", Jsons.stringify(manifest).getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                writeEntry(zip, entry.getKey(), entry.getValue());
            }
        } catch (IOException exception) {
            throw new IllegalStateException("导出包写入失败: " + target, exception);
        }
        return new ExportReport(target, counts);
    }

    public ImportReport importFrom(Path zipFile) {
        List<TableCount> counts = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Map<String, Object> manifest = readManifest(zip);
            List<?> manifestTables = (List<?>) manifest.get("tables");
            if (manifestTables == null) {
                throw new IllegalStateException("导出包缺少 tables 清单: " + zipFile);
            }
            try (Connection connection = dataSource.get().getConnection()) {
                SchemaMigrator.run(connection, dialect, writeGate);
                for (Object item : manifestTables) {
                    Map<?, ?> entry = (Map<?, ?>) item;
                    String table = String.valueOf(entry.get("table"));
                    if (!TRANSFER_TABLES.contains(table)) {
                        throw new IllegalStateException("导出包含未知数据表: " + table);
                    }
                    String file = String.valueOf(entry.get("file"));
                    long expectedRows = asLong(entry.get("rows"));
                    String expectedSha = String.valueOf(entry.get("sha256"));
                    byte[] bytes = readEntry(zip, file);
                    String actualSha = sha256Hex(bytes);
                    if (!actualSha.equalsIgnoreCase(expectedSha)) {
                        throw new IllegalStateException("导出包校验和不匹配: " + file + " expected=" + expectedSha
                                + " actual=" + actualSha);
                    }
                    long applied = applyTable(connection, table, bytes, expectedRows);
                    counts.add(new TableCount(table, applied));
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("数据库导入写入失败", exception);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取导出包: " + zipFile, exception);
        }
        return new ImportReport(zipFile, counts);
    }

    public CopyReport copyFrom(Path sourceConfigFile) {
        DatabaseConfig sourceConfig = loadSourceConfig(sourceConfigFile);
        DbEngine sourceEngine = DbEngine.resolve(sourceConfig.engine(), sourceConfig.host());
        DatabaseDialect sourceDialect = DatabaseDialect.forEngine(sourceEngine);
        SingleWriterGate sourceGate = SingleWriterGate.forEngine(sourceEngine);
        try {
            JdbcDrivers.register(sourceEngine);
        } catch (Exception exception) {
            throw new IllegalStateException("源数据库驱动注册失败: " + sourceEngine.id(), exception);
        }
        Path baseDirectory = sourceConfigFile.toAbsolutePath().getParent();
        String url = JdbcDrivers.jdbcUrl(sourceEngine, sourceConfig, baseDirectory);
        List<TableCount> counts = new ArrayList<>();
        try (Connection source = openSource(sourceEngine, url, sourceConfig)) {
            SchemaMigrator.run(source, sourceDialect, sourceGate);
            Map<String, TableDump> dump = new LinkedHashMap<>();
            for (String table : TRANSFER_TABLES) {
                dump.put(table, dumpTable(source, table));
            }
            try (Connection target = dataSource.get().getConnection()) {
                SchemaMigrator.run(target, dialect, writeGate);
                for (String table : TRANSFER_TABLES) {
                    TableDump tableDump = dump.get(table);
                    long applied = applyTable(target, table, tableDump.bytes(), tableDump.rows());
                    counts.add(new TableCount(table, applied));
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("目标数据库写入失败", exception);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("源数据库读取失败: " + sourceConfigFile, exception);
        }
        return new CopyReport(sourceEngine.id(), engine.id(), counts);
    }

    private DatabaseConfig loadSourceConfig(Path sourceConfigFile) {
        if (!Files.isRegularFile(sourceConfigFile)) {
            throw new IllegalStateException("源 database.yml 不存在: " + sourceConfigFile);
        }
        try {
            YamlSection section = YamlFiles.load(sourceConfigFile.toFile());
            return DatabaseConfig.fromConfig(section);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("源 database.yml 解析失败: " + sourceConfigFile, exception);
        }
    }

    private Connection openSource(DbEngine sourceEngine, String url, DatabaseConfig sourceConfig) throws SQLException {
        if (sourceEngine == DbEngine.SQLITE) {
            return DriverManager.getConnection(url);
        }
        return DriverManager.getConnection(url, sourceConfig.username(), sourceConfig.password());
    }

    private List<String> checkTables() {
        List<String> tables = new ArrayList<>(TRANSFER_TABLES);
        tables.add("emaki_server");
        tables.add("emaki_data_lease");
        return tables;
    }

    private long countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private TableDump dumpTable(Connection connection, String table) throws SQLException {
        StringBuilder builder = new StringBuilder();
        long rows = 0L;
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(selectSql(table))) {
            while (resultSet.next()) {
                builder.append(Jsons.stringify(rowValues(table, resultSet))).append('\n');
                rows++;
            }
        }
        return new TableDump(builder.toString().getBytes(StandardCharsets.UTF_8), rows);
    }

    private String selectSql(String table) {
        switch (table) {
            case "emaki_player":
                return "SELECT player_uuid, current_name, first_seen, last_seen FROM emaki_player";
            case "emaki_player_name":
                return "SELECT player_uuid, name, seen_at FROM emaki_player_name";
            case "emaki_player_data":
                return "SELECT player_uuid, domain, revision, schema_version, payload, updated_at, updated_by FROM emaki_player_data";
            case "emaki_shared_state":
                return "SELECT namespace, k, v, revision, updated_at, updated_by FROM emaki_shared_state ORDER BY row_id";
            case "emaki_shared_counter":
                return "SELECT namespace, k, val FROM emaki_shared_counter";
            default:
                return "SELECT name, val FROM emaki_shared_seq";
        }
    }

    private Map<String, Object> rowValues(String table, ResultSet resultSet) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        switch (table) {
            case "emaki_player":
                row.put("player_uuid", uuidString(resultSet, 1));
                row.put("current_name", resultSet.getString(2));
                row.put("first_seen", resultSet.getLong(3));
                row.put("last_seen", resultSet.getLong(4));
                break;
            case "emaki_player_name":
                row.put("player_uuid", uuidString(resultSet, 1));
                row.put("name", resultSet.getString(2));
                row.put("seen_at", resultSet.getLong(3));
                break;
            case "emaki_player_data":
                row.put("player_uuid", uuidString(resultSet, 1));
                row.put("domain", resultSet.getString(2));
                row.put("revision", resultSet.getLong(3));
                row.put("schema_version", resultSet.getLong(4));
                row.put("payload", Base64.getEncoder().encodeToString(resultSet.getBytes(5)));
                row.put("updated_at", resultSet.getLong(6));
                row.put("updated_by", resultSet.getString(7));
                break;
            case "emaki_shared_state":
                row.put("namespace", resultSet.getString(1));
                row.put("k", resultSet.getString(2));
                row.put("v", resultSet.getString(3));
                row.put("revision", resultSet.getLong(4));
                row.put("updated_at", resultSet.getLong(5));
                row.put("updated_by", resultSet.getString(6));
                break;
            case "emaki_shared_counter":
                row.put("namespace", resultSet.getString(1));
                row.put("k", resultSet.getString(2));
                row.put("val", resultSet.getLong(3));
                break;
            default:
                row.put("name", resultSet.getString(1));
                row.put("val", resultSet.getLong(2));
                break;
        }
        return row;
    }

    private String uuidString(ResultSet resultSet, int column) throws SQLException {
        byte[] bytes = resultSet.getBytes(column);
        return bytes == null ? "" : UuidBytes.fromBytes(bytes).toString();
    }

    private long applyTable(Connection connection, String table, byte[] bytes, long expectedRows) {
        String[] lines = new String(bytes, StandardCharsets.UTF_8).split("\n");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            Object parsed = Jsons.parse(line);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalStateException("导出数据行不是 JSON 对象: " + table);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            ((Map<?, ?>) parsed).forEach((key, value) -> row.put(String.valueOf(key), value));
            rows.add(row);
        }
        if (rows.size() != expectedRows) {
            throw new IllegalStateException("行数校验失败: " + table + " expected=" + expectedRows + " actual=" + rows.size());
        }
        try {
            return applyRows(connection, table, rows);
        } catch (SQLException exception) {
            throw new IllegalStateException("数据回灌失败: " + table, exception);
        }
    }

    private long applyRows(Connection connection, String table, List<Map<String, Object>> rows) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        long applied = 0L;
        try (SingleWriterGate.Scope ignored = writeGate.enter()) {
            connection.setAutoCommit(false);
            try {
                for (Map<String, Object> row : rows) {
                    upsertRow(connection, table, row);
                    applied++;
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        return applied;
    }

    private void upsertRow(Connection connection, String table, Map<String, Object> row) throws SQLException {
        String sql;
        switch (table) {
            case "emaki_player":
                sql = "INSERT INTO emaki_player (player_uuid, current_name, first_seen, last_seen) VALUES (?, ?, ?, ?)"
                        + dialect.upsertTail("emaki_player", "player_uuid",
                                "current_name = " + dialect.excluded("current_name")
                                        + ", last_seen = " + dialect.excluded("last_seen"));
                break;
            case "emaki_player_name":
                sql = dialect.insertIgnoreStart()
                        + " INTO emaki_player_name (player_uuid, name, seen_at) VALUES (?, ?, ?)"
                        + dialect.insertIgnoreTail();
                break;
            case "emaki_player_data":
                sql = "INSERT INTO emaki_player_data (player_uuid, domain, revision, schema_version, payload, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?)"
                        + dialect.upsertTail("emaki_player_data", "player_uuid, domain",
                                "revision = " + dialect.excluded("revision")
                                        + ", schema_version = " + dialect.excluded("schema_version")
                                        + ", payload = " + dialect.excluded("payload")
                                        + ", updated_at = " + dialect.excluded("updated_at")
                                        + ", updated_by = " + dialect.excluded("updated_by"));
                break;
            case "emaki_shared_state":
                sql = "INSERT INTO emaki_shared_state (namespace, k, v, revision, updated_at, updated_by) VALUES (?, ?, ?, ?, ?, ?)"
                        + dialect.upsertTail("emaki_shared_state", "namespace, k",
                                "v = " + dialect.excluded("v")
                                        + ", revision = " + dialect.excluded("revision")
                                        + ", updated_at = " + dialect.excluded("updated_at")
                                        + ", updated_by = " + dialect.excluded("updated_by"));
                break;
            case "emaki_shared_counter":
                sql = "INSERT INTO emaki_shared_counter (namespace, k, val) VALUES (?, ?, ?)"
                        + dialect.upsertTail("emaki_shared_counter", "namespace, k",
                                "val = " + dialect.excluded("val"));
                break;
            default:
                sql = "INSERT INTO emaki_shared_seq (name, val) VALUES (?, ?)"
                        + dialect.upsertTail("emaki_shared_seq", "name",
                                "val = " + dialect.maxExpr("emaki_shared_seq.val", dialect.excluded("val")));
                break;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            switch (table) {
                case "emaki_player":
                    statement.setBytes(1, uuidBytes(row, "player_uuid"));
                    statement.setString(2, asString(row, "current_name"));
                    statement.setLong(3, asLong(row.get("first_seen")));
                    statement.setLong(4, asLong(row.get("last_seen")));
                    break;
                case "emaki_player_name":
                    statement.setBytes(1, uuidBytes(row, "player_uuid"));
                    statement.setString(2, asString(row, "name"));
                    statement.setLong(3, asLong(row.get("seen_at")));
                    break;
                case "emaki_player_data":
                    statement.setBytes(1, uuidBytes(row, "player_uuid"));
                    statement.setString(2, asString(row, "domain"));
                    statement.setLong(3, asLong(row.get("revision")));
                    statement.setInt(4, (int) asLong(row.get("schema_version")));
                    statement.setBytes(5, Base64.getDecoder().decode(asString(row, "payload")));
                    statement.setLong(6, asLong(row.get("updated_at")));
                    statement.setString(7, asString(row, "updated_by"));
                    break;
                case "emaki_shared_state":
                    statement.setString(1, asString(row, "namespace"));
                    statement.setString(2, asString(row, "k"));
                    statement.setString(3, asString(row, "v"));
                    statement.setLong(4, asLong(row.get("revision")));
                    statement.setLong(5, asLong(row.get("updated_at")));
                    statement.setString(6, asString(row, "updated_by"));
                    break;
                case "emaki_shared_counter":
                    statement.setString(1, asString(row, "namespace"));
                    statement.setString(2, asString(row, "k"));
                    statement.setLong(3, asLong(row.get("val")));
                    break;
                default:
                    statement.setString(1, asString(row, "name"));
                    statement.setLong(2, asLong(row.get("val")));
                    break;
            }
            statement.executeUpdate();
        }
    }

    private Map<String, Object> readManifest(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("manifest.json");
        if (entry == null) {
            throw new IllegalStateException("导出包缺少 manifest.json");
        }
        byte[] bytes;
        try (InputStream inputStream = zip.getInputStream(entry)) {
            bytes = inputStream.readAllBytes();
        }
        Object parsed = Jsons.parse(new String(bytes, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalStateException("导出包 manifest.json 不是 JSON 对象");
        }
        if (!"emaki-db-export".equals(String.valueOf(((Map<?, ?>) parsed).get("format")))) {
            throw new IllegalStateException("导出包格式不匹配: " + ((Map<?, ?>) parsed).get("format"));
        }
        Map<String, Object> manifest = new LinkedHashMap<>();
        ((Map<?, ?>) parsed).forEach((key, value) -> manifest.put(String.valueOf(key), value));
        return manifest;
    }

    private byte[] readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IllegalStateException("导出包缺少数据文件: " + name);
        }
        try (InputStream inputStream = zip.getInputStream(entry)) {
            return inputStream.readAllBytes();
        }
    }

    private void writeEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private byte[] uuidBytes(Map<String, Object> row, String key) {
        String value = asString(row, key);
        if (value.isEmpty()) {
            return UuidBytes.toBytes(new UUID(0L, 0L));
        }
        return UuidBytes.toBytes(UUID.fromString(value));
    }

    private String asString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
