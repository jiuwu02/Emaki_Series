package emaki.jiuwu.craft.corelib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SchemaMigrator {

    private SchemaMigrator() {
    }

    static List<String> steps(DatabaseDialect dialect) {
        List<String> steps = new ArrayList<>();
        steps.add("CREATE TABLE IF NOT EXISTS emaki_player (player_uuid " + dialect.uuidType()
                + " PRIMARY KEY, current_name VARCHAR(16) NOT NULL, first_seen BIGINT NOT NULL, last_seen BIGINT NOT NULL"
                + dialect.inlineIndex("idx_emaki_player_name", "current_name") + ")" + dialect.tableOptions());
        steps.add("CREATE TABLE IF NOT EXISTS emaki_player_name (player_uuid " + dialect.uuidType()
                + " NOT NULL, name VARCHAR(16) NOT NULL, seen_at BIGINT NOT NULL, PRIMARY KEY (player_uuid, name))"
                + dialect.tableOptions());
        steps.add("CREATE TABLE IF NOT EXISTS emaki_server (server_id VARCHAR(32) PRIMARY KEY, display_name VARCHAR(64) NOT NULL, last_heartbeat BIGINT NOT NULL)"
                + dialect.tableOptions());
        steps.add("CREATE TABLE IF NOT EXISTS emaki_data_lease (player_uuid " + dialect.uuidType()
                + " PRIMARY KEY, server_id VARCHAR(32) NOT NULL, epoch BIGINT NOT NULL, lease_until BIGINT NOT NULL"
                + dialect.inlineIndex("idx_emaki_lease_server", "server_id")
                + dialect.inlineIndex("idx_emaki_lease_expiry", "lease_until") + ")" + dialect.tableOptions());
        steps.add("CREATE TABLE IF NOT EXISTS emaki_player_data (player_uuid " + dialect.uuidType()
                + " NOT NULL, domain VARCHAR(32) NOT NULL, revision BIGINT NOT NULL, schema_version INT NOT NULL, payload "
                + dialect.payloadType()
                + " NOT NULL, updated_at BIGINT NOT NULL, updated_by VARCHAR(32) NOT NULL, PRIMARY KEY (player_uuid, domain))"
                + dialect.tableOptions());
        steps.add("CREATE TABLE IF NOT EXISTS emaki_shared_state (namespace VARCHAR(64) NOT NULL, k VARCHAR(191) NOT NULL, v "
                + dialect.valueType()
                + " NOT NULL, revision BIGINT NOT NULL, updated_at BIGINT NOT NULL, updated_by VARCHAR(32) NOT NULL, "
                + dialect.autoIncrementColumn("row_id") + ", " + dialect.sharedStateKey()
                + dialect.autoIncrementUniqueKey("uq_emaki_shared_state_row", "row_id") + ")" + dialect.tableOptions());
        steps.add("CREATE TABLE IF NOT EXISTS emaki_shared_counter (namespace VARCHAR(64) NOT NULL, k VARCHAR(191) NOT NULL, val BIGINT NOT NULL, PRIMARY KEY (namespace, k))"
                + dialect.tableOptions());
        steps.add("CREATE TABLE IF NOT EXISTS emaki_shared_seq (name VARCHAR(191) PRIMARY KEY, val BIGINT NOT NULL)"
                + dialect.tableOptions());
        return steps;
    }

    static List<String> indexSteps(DatabaseDialect dialect) {
        List<String> steps = new ArrayList<>();
        addIndex(steps, dialect.indexDdl("emaki_player", "idx_emaki_player_name", "current_name"));
        addIndex(steps, dialect.indexDdl("emaki_data_lease", "idx_emaki_lease_server", "server_id"));
        addIndex(steps, dialect.indexDdl("emaki_data_lease", "idx_emaki_lease_expiry", "lease_until"));
        return steps;
    }

    static int totalSteps(DatabaseDialect dialect) {
        return steps(dialect).size();
    }

    static Set<Integer> appliedSteps(Connection connection) throws SQLException {
        Set<Integer> applied = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT step FROM emaki_schema_version")) {
            while (resultSet.next()) {
                applied.add(resultSet.getInt(1));
            }
        }
        return applied;
    }

    static void run(Connection connection, DatabaseDialect dialect, SingleWriterGate gate) throws SQLException {
        List<String> steps = steps(dialect);
        try (SingleWriterGate.Scope ignored = gate.enter();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS emaki_schema_version (step INT PRIMARY KEY, applied_at BIGINT NOT NULL)"
                    + dialect.tableOptions());
        }
        Set<Integer> applied = appliedSteps(connection);
        List<Integer> pending = new ArrayList<>();
        for (int step = 1; step <= steps.size(); step++) {
            if (!applied.contains(step)) {
                pending.add(step);
            }
        }
        if (!pending.isEmpty()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (SingleWriterGate.Scope ignored = gate.enter()) {
                for (int step : pending) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute(steps.get(step - 1));
                    }
                    try (PreparedStatement statement = connection
                            .prepareStatement("INSERT INTO emaki_schema_version (step, applied_at) VALUES (?, ?)")) {
                        statement.setInt(1, step);
                        statement.setLong(2, System.currentTimeMillis());
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
        for (String indexSql : indexSteps(dialect)) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(indexSql);
            }
        }
    }

    private static void addIndex(List<String> steps, String sql) {
        if (!sql.isEmpty()) {
            steps.add(sql);
        }
    }
}
