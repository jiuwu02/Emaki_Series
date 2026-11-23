package emaki.jiuwu.craft.corelib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.sql.DataSource;

import emaki.jiuwu.craft.corelib.api.data.DomainSaveResult;
import emaki.jiuwu.craft.corelib.api.data.DomainSnapshot;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;

final class JdbcPlayerDomainRepository implements PlayerDomainRepository {

    private final Supplier<DataSource> dataSource;
    private final ExecutorService executor;
    private final String serverId;
    private final DatabaseDialect dialect;
    private final SingleWriterGate writeGate;

    JdbcPlayerDomainRepository(Supplier<DataSource> dataSource, ExecutorService executor, String serverId,
            DatabaseDialect dialect, SingleWriterGate writeGate) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.serverId = serverId;
        this.dialect = dialect;
        this.writeGate = writeGate;
    }

    @Override
    public CompletableFuture<DomainSnapshot> load(UUID player, String domain) {
        return CompletableFuture.supplyAsync(() -> loadBlocking(player, domain), executor);
    }

    @Override
    public CompletableFuture<DomainSaveResult> save(UUID player, String domain, String playerName,
            byte[] payload, int schemaVersion, long expectedRevision) {
        return CompletableFuture.supplyAsync(
                () -> saveBlocking(player, domain, playerName, payload, schemaVersion, expectedRevision),
                executor);
    }

    @Override
    public CompletableFuture<List<UUID>> listPlayers(String domain) {
        return CompletableFuture.supplyAsync(() -> listBlocking(domain), executor);
    }

    private DomainSnapshot loadBlocking(UUID player, String domain) {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT revision, schema_version, payload FROM emaki_player_data WHERE player_uuid = ? AND domain = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(player));
            statement.setString(2, domain);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new DomainSnapshot(resultSet.getLong(1), resultSet.getInt(2), resultSet.getBytes(3));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("database load failed: " + player + "/" + domain, exception);
        }
    }

    private DomainSaveResult saveBlocking(UUID player, String domain, String playerName,
            byte[] payload, int schemaVersion, long expectedRevision) {
        long now = System.currentTimeMillis();
        try (SingleWriterGate.Scope ignored = writeGate.enter();
                Connection connection = dataSource.get().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                touchPlayer(connection, player, playerName, now);
                if (expectedRevision <= 0) {
                    try {
                        insertRow(connection, player, domain, payload, schemaVersion, now);
                    } catch (SQLException conflict) {
                        connection.rollback();
                        long observed = readRevision(connection, player, domain, -1L);
                        if (observed >= 0L) {
                            return DomainSaveResult.stale(observed);
                        }
                        throw conflict;
                    }
                    connection.commit();
                    return DomainSaveResult.committed(1L);
                }
                int rows = updateRow(connection, player, domain, payload, schemaVersion, now, expectedRevision);
                if (rows == 1) {
                    connection.commit();
                    return DomainSaveResult.committed(expectedRevision + 1);
                }
                connection.rollback();
                return DomainSaveResult.stale(readRevision(connection, player, domain, expectedRevision));
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            return DomainSaveResult.rejected(expectedRevision);
        }
    }

    private void touchPlayer(Connection connection, UUID player, String playerName, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO emaki_player (player_uuid, current_name, first_seen, last_seen) VALUES (?, ?, ?, ?) "
                        + dialect.upsertTail("emaki_player", "player_uuid", "current_name = " + dialect.excluded("current_name")
                                + ", last_seen = " + dialect.excluded("last_seen")))) {
            statement.setBytes(1, UuidBytes.toBytes(player));
            statement.setString(2, playerName == null ? "" : playerName);
            statement.setLong(3, now);
            statement.setLong(4, now);
            statement.executeUpdate();
        }
        if (playerName != null && !playerName.isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    dialect.insertIgnoreStart() + " INTO emaki_player_name (player_uuid, name, seen_at) VALUES (?, ?, ?)"
                            + dialect.insertIgnoreTail())) {
                statement.setBytes(1, UuidBytes.toBytes(player));
                statement.setString(2, playerName);
                statement.setLong(3, now);
                statement.executeUpdate();
            }
        }
    }

    private void insertRow(Connection connection, UUID player, String domain, byte[] payload,
            int schemaVersion, long now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO emaki_player_data (player_uuid, domain, revision, schema_version, payload, updated_at, updated_by) "
                        + "VALUES (?, ?, 1, ?, ?, ?, ?)")) {
            statement.setBytes(1, UuidBytes.toBytes(player));
            statement.setString(2, domain);
            statement.setInt(3, schemaVersion);
            statement.setBytes(4, payload);
            statement.setLong(5, now);
            statement.setString(6, serverId);
            statement.executeUpdate();
        }
    }

    private int updateRow(Connection connection, UUID player, String domain, byte[] payload,
            int schemaVersion, long now, long expectedRevision) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE emaki_player_data SET revision = revision + 1, schema_version = ?, payload = ?, "
                        + "updated_at = ?, updated_by = ? WHERE player_uuid = ? AND domain = ? AND revision = ?")) {
            statement.setInt(1, schemaVersion);
            statement.setBytes(2, payload);
            statement.setLong(3, now);
            statement.setString(4, serverId);
            statement.setBytes(5, UuidBytes.toBytes(player));
            statement.setString(6, domain);
            statement.setLong(7, expectedRevision);
            return statement.executeUpdate();
        }
    }

    private long readRevision(Connection connection, UUID player, String domain, long fallback) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT revision FROM emaki_player_data WHERE player_uuid = ? AND domain = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(player));
            statement.setString(2, domain);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : fallback;
            }
        } catch (SQLException exception) {
            return fallback;
        }
    }

    private List<UUID> listBlocking(String domain) {
        try (Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT player_uuid FROM emaki_player_data WHERE domain = ?")) {
            statement.setString(1, domain);
            List<UUID> players = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    players.add(UuidBytes.fromBytes(resultSet.getBytes(1)));
                }
            }
            return players;
        } catch (SQLException exception) {
            throw new IllegalStateException("database list failed: " + domain, exception);
        }
    }
}
