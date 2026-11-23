package emaki.jiuwu.craft.corelib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.sql.DataSource;

import emaki.jiuwu.craft.corelib.api.data.SharedEntry;
import emaki.jiuwu.craft.corelib.api.data.SharedStateRepository;

final class JdbcSharedStateRepository implements SharedStateRepository {

    private final Supplier<DataSource> dataSource;
    private final ExecutorService executor;
    private final DatabaseDialect dialect;
    private final String serverId;
    private final SingleWriterGate writeGate;

    JdbcSharedStateRepository(Supplier<DataSource> dataSource, ExecutorService executor,
            DatabaseDialect dialect, String serverId, SingleWriterGate writeGate) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.dialect = dialect;
        this.serverId = serverId;
        this.writeGate = writeGate;
    }

    @Override
    public CompletableFuture<Boolean> claimOnce(String namespace, String key) {
        return CompletableFuture.supplyAsync(() -> claimOnceBlocking(namespace, key), executor);
    }

    @Override
    public CompletableFuture<Long> incrementCounter(String namespace, String key, long delta) {
        return CompletableFuture.supplyAsync(() -> incrementCounterBlocking(namespace, key, delta), executor);
    }

    @Override
    public CompletableFuture<Long> getCounter(String namespace, String key) {
        return CompletableFuture.supplyAsync(() -> getCounterBlocking(namespace, key), executor);
    }

    @Override
    public CompletableFuture<Void> put(String namespace, String key, String value) {
        return CompletableFuture.runAsync(() -> putBlocking(namespace, key, value), executor);
    }

    @Override
    public CompletableFuture<String> get(String namespace, String key) {
        return CompletableFuture.supplyAsync(() -> getBlocking(namespace, key), executor);
    }

    @Override
    public CompletableFuture<List<SharedEntry>> list(String namespace) {
        return CompletableFuture.supplyAsync(() -> listBlocking(namespace), executor);
    }

    @Override
    public CompletableFuture<Boolean> remove(String namespace, String key) {
        return CompletableFuture.supplyAsync(() -> removeBlocking(namespace, key), executor);
    }

    @Override
    public CompletableFuture<Long> nextSequence(String name) {
        return CompletableFuture.supplyAsync(() -> nextSequenceBlocking(name), executor);
    }

    private boolean claimOnceBlocking(String namespace, String key) {
        String sql = dialect.insertIgnoreStart()
                + " INTO emaki_shared_state (namespace, k, v, revision, updated_at, updated_by) VALUES (?, ?, ?, 1, ?, ?)"
                + dialect.insertIgnoreTail();
        try (SingleWriterGate.Scope ignored = writeGate.enter();
                Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            statement.setString(3, "");
            statement.setLong(4, System.currentTimeMillis());
            statement.setString(5, serverId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("database claim failed: " + namespace + "/" + key, exception);
        }
    }

    private long incrementCounterBlocking(String namespace, String key, long delta) {
        String sql = "INSERT INTO emaki_shared_counter (namespace, k, val) VALUES (?, ?, ?)"
                + dialect.upsertTail("emaki_shared_counter", "namespace, k",
                        "val = emaki_shared_counter.val + " + dialect.excluded("val"));
        try (SingleWriterGate.Scope ignored = writeGate.enter();
                Connection connection = dataSource.get().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, namespace);
                    statement.setString(2, key);
                    statement.setLong(3, delta);
                    statement.executeUpdate();
                }
                long value = readCounter(connection, namespace, key);
                connection.commit();
                return value;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("database counter increment failed: " + namespace + "/" + key, exception);
        }
    }

    private long getCounterBlocking(String namespace, String key) {
        try (Connection connection = dataSource.get().getConnection()) {
            return readCounter(connection, namespace, key);
        } catch (SQLException exception) {
            throw new IllegalStateException("database counter read failed: " + namespace + "/" + key, exception);
        }
    }

    private long readCounter(Connection connection, String namespace, String key) throws SQLException {
        try (PreparedStatement statement = connection
                .prepareStatement("SELECT val FROM emaki_shared_counter WHERE namespace = ? AND k = ?")) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    private void putBlocking(String namespace, String key, String value) {
        String sql = "INSERT INTO emaki_shared_state (namespace, k, v, revision, updated_at, updated_by) VALUES (?, ?, ?, 1, ?, ?)"
                + dialect.upsertTail("emaki_shared_state", "namespace, k",
                        "v = " + dialect.excluded("v")
                                + ", revision = emaki_shared_state.revision + 1"
                                + ", updated_at = " + dialect.excluded("updated_at")
                                + ", updated_by = " + dialect.excluded("updated_by"));
        try (SingleWriterGate.Scope ignored = writeGate.enter();
                Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            statement.setString(3, value == null ? "" : value);
            statement.setLong(4, System.currentTimeMillis());
            statement.setString(5, serverId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("database put failed: " + namespace + "/" + key, exception);
        }
    }

    private String getBlocking(String namespace, String key) {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection
                        .prepareStatement("SELECT v FROM emaki_shared_state WHERE namespace = ? AND k = ?")) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("database get failed: " + namespace + "/" + key, exception);
        }
    }

    private List<SharedEntry> listBlocking(String namespace) {
        Map<String, SharedEntry> entries = new LinkedHashMap<>();
        try (Connection connection = dataSource.get().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT k, v FROM emaki_shared_state WHERE namespace = ? ORDER BY row_id")) {
                statement.setString(1, namespace);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String key = resultSet.getString(1);
                        entries.put(key, new SharedEntry(key, resultSet.getString(2), 0L));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT k, val FROM emaki_shared_counter WHERE namespace = ? ORDER BY k")) {
                statement.setString(1, namespace);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        String key = resultSet.getString(1);
                        long counter = resultSet.getLong(2);
                        SharedEntry state = entries.get(key);
                        entries.put(key, new SharedEntry(key, state == null ? "" : state.value(), counter));
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("database list failed: " + namespace, exception);
        }
        return List.copyOf(entries.values());
    }

    private boolean removeBlocking(String namespace, String key) {
        try (SingleWriterGate.Scope ignored = writeGate.enter();
                Connection connection = dataSource.get().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                boolean removed = deleteRow(connection, "emaki_shared_state", namespace, key);
                removed |= deleteRow(connection, "emaki_shared_counter", namespace, key);
                connection.commit();
                return removed;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("database remove failed: " + namespace + "/" + key, exception);
        }
    }

    private boolean deleteRow(Connection connection, String table, String namespace, String key) throws SQLException {
        try (PreparedStatement statement = connection
                .prepareStatement("DELETE FROM " + table + " WHERE namespace = ? AND k = ?")) {
            statement.setString(1, namespace);
            statement.setString(2, key);
            return statement.executeUpdate() > 0;
        }
    }

    private long nextSequenceBlocking(String name) {
        String sql = "INSERT INTO emaki_shared_seq (name, val) VALUES (?, 1)"
                + dialect.upsertTail("emaki_shared_seq", "name", "val = emaki_shared_seq.val + 1");
        try (SingleWriterGate.Scope ignored = writeGate.enter();
                Connection connection = dataSource.get().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, name);
                    statement.executeUpdate();
                }
                long value;
                try (PreparedStatement statement = connection
                        .prepareStatement("SELECT val FROM emaki_shared_seq WHERE name = ?")) {
                    statement.setString(1, name);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        value = resultSet.next() ? resultSet.getLong(1) : 0L;
                    }
                }
                connection.commit();
                return value;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("database sequence failed: " + name, exception);
        }
    }
}
