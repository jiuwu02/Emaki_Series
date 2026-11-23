package emaki.jiuwu.craft.corelib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import javax.sql.DataSource;

import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.log.LogTag;
import emaki.jiuwu.craft.corelib.text.LogMessages;

final class JdbcPlayerDataLease implements PlayerDataLease {

    private final Supplier<DataSource> dataSource;
    private final ExecutorService executor;
    private final String serverId;
    private final long ttlMillis;
    private final long takeoverMillis;
    private final Set<UUID> heldLeases;
    private final LogMessages messages;
    private final DatabaseDialect dialect;
    private final SingleWriterGate writeGate;

    JdbcPlayerDataLease(Supplier<DataSource> dataSource, ExecutorService executor, String serverId,
            long ttlMillis, long takeoverMillis, Set<UUID> heldLeases, LogMessages messages,
            DatabaseDialect dialect, SingleWriterGate writeGate) {
        this.dataSource = dataSource;
        this.executor = executor;
        this.serverId = serverId;
        this.ttlMillis = ttlMillis;
        this.takeoverMillis = takeoverMillis;
        this.heldLeases = heldLeases;
        this.messages = messages;
        this.dialect = dialect;
        this.writeGate = writeGate;
    }

    @Override
    public CompletableFuture<Boolean> acquire(UUID player) {
        return CompletableFuture.supplyAsync(() -> acquireBlocking(player), executor);
    }

    @Override
    public CompletableFuture<Boolean> release(UUID player) {
        return CompletableFuture.supplyAsync(() -> releaseBlocking(player), executor);
    }

    private boolean acquireBlocking(UUID player) {
        long now = System.currentTimeMillis();
        long until = now + ttlMillis;
        try (SingleWriterGate.Scope ignored = writeGate.enter();
                Connection connection = dataSource.get().getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String owner = null;
                long leaseUntil = 0L;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT server_id, lease_until FROM emaki_data_lease WHERE player_uuid = ?"
                                + dialect.lockTail())) {
                    select.setBytes(1, UuidBytes.toBytes(player));
                    try (ResultSet resultSet = select.executeQuery()) {
                        if (resultSet.next()) {
                            owner = resultSet.getString(1);
                            leaseUntil = resultSet.getLong(2);
                        }
                    }
                }
                boolean acquired;
                boolean takeover = false;
                if (owner == null) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO emaki_data_lease (player_uuid, server_id, epoch, lease_until) VALUES (?, ?, 1, ?)")) {
                        insert.setBytes(1, UuidBytes.toBytes(player));
                        insert.setString(2, serverId);
                        insert.setLong(3, until);
                        insert.executeUpdate();
                    }
                    acquired = true;
                } else if (serverId.equals(owner)) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE emaki_data_lease SET lease_until = ? WHERE player_uuid = ?")) {
                        update.setLong(1, until);
                        update.setBytes(2, UuidBytes.toBytes(player));
                        update.executeUpdate();
                    }
                    acquired = true;
                } else if (now > leaseUntil + takeoverMillis) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE emaki_data_lease SET server_id = ?, epoch = epoch + 1, lease_until = ? WHERE player_uuid = ?")) {
                        update.setString(1, serverId);
                        update.setLong(2, until);
                        update.setBytes(3, UuidBytes.toBytes(player));
                        update.executeUpdate();
                    }
                    acquired = true;
                    takeover = true;
                } else {
                    acquired = false;
                }
                connection.commit();
                if (acquired) {
                    heldLeases.add(player);
                    if (takeover) {
                        warn("console.database_lease_takeover",
                                Map.of("player", String.valueOf(player), "server_id", serverId));
                    }
                }
                return acquired;
            } catch (SQLException exception) {
                connection.rollback();
                return false;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    private boolean releaseBlocking(UUID player) {
        try (SingleWriterGate.Scope ignored = writeGate.enter();
                Connection connection = dataSource.get().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM emaki_data_lease WHERE player_uuid = ? AND server_id = ?")) {
            statement.setBytes(1, UuidBytes.toBytes(player));
            statement.setString(2, serverId);
            boolean removed = statement.executeUpdate() > 0;
            heldLeases.remove(player);
            return removed;
        } catch (SQLException exception) {
            return false;
        }
    }

    private void warn(String key, Map<String, ?> replacements) {
        if (messages != null) {
            messages.warning(LogTag.PERSISTENCE, key, replacements);
        }
    }
}
