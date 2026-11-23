package emaki.jiuwu.craft.corelib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.plugin.java.JavaPlugin;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import emaki.jiuwu.craft.corelib.api.data.PlayerDataLease;
import emaki.jiuwu.craft.corelib.api.data.PlayerDomainRepository;
import emaki.jiuwu.craft.corelib.api.data.SharedStateRepository;
import emaki.jiuwu.craft.corelib.log.LogTag;
import emaki.jiuwu.craft.corelib.text.LogMessages;
import emaki.jiuwu.craft.corelib.text.LogMessagesProvider;

public final class DatabaseService {

    private final DatabaseConfig config;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;
    private final ScheduledExecutorService leaseScheduler;
    private final JdbcPlayerDomainRepository repository;
    private final JdbcPlayerDataLease lease;
    private final JdbcSharedStateRepository sharedState;
    private final DatabaseMaintenance maintenance;
    private final SingleWriterGate writeGate;
    private final Set<UUID> heldLeases;
    private final LogMessages messages;

    private DatabaseService(DatabaseConfig config, HikariDataSource dataSource, ExecutorService executor,
            ScheduledExecutorService leaseScheduler, JdbcPlayerDomainRepository repository,
            JdbcPlayerDataLease lease, JdbcSharedStateRepository sharedState, DatabaseMaintenance maintenance,
            SingleWriterGate writeGate, Set<UUID> heldLeases, LogMessages messages) {
        this.config = config;
        this.dataSource = dataSource;
        this.executor = executor;
        this.leaseScheduler = leaseScheduler;
        this.repository = repository;
        this.lease = lease;
        this.sharedState = sharedState;
        this.maintenance = maintenance;
        this.writeGate = writeGate;
        this.heldLeases = heldLeases;
        this.messages = messages;
    }

    public static DatabaseService start(JavaPlugin plugin, DatabaseConfig config) throws Exception {
        DbEngine engine = DbEngine.resolve(config.engine(), config.host());
        DatabaseDialect dialect = DatabaseDialect.forEngine(engine);
        SingleWriterGate writeGate = SingleWriterGate.forEngine(engine);
        JdbcDrivers.register(engine);
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("emaki-db");
        hikariConfig.setJdbcUrl(JdbcDrivers.jdbcUrl(engine, config, plugin.getDataFolder().toPath()));
        hikariConfig.setDriverClassName(JdbcDrivers.driverClassName(engine));
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(Math.max(1, config.poolSize()));
        hikariConfig.setInitializationFailTimeout(5000L);
        HikariDataSource dataSource = new HikariDataSource(hikariConfig);
        try (Connection connection = dataSource.getConnection()) {
            SchemaMigrator.run(connection, dialect, writeGate);
        } catch (Exception exception) {
            dataSource.close();
            throw exception;
        }
        LogMessages messages = plugin instanceof LogMessagesProvider provider ? provider.messageService() : null;
        AtomicInteger threadIndex = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(2, config.poolSize() / 4), task -> {
            Thread thread = new Thread(task, "emaki-db-" + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        JdbcPlayerDomainRepository repository = new JdbcPlayerDomainRepository(
                () -> dataSource, executor, config.serverId(), dialect, writeGate);
        Set<UUID> heldLeases = ConcurrentHashMap.newKeySet();
        JdbcPlayerDataLease lease = new JdbcPlayerDataLease(() -> dataSource, executor, config.serverId(),
                config.leaseTtlMillis(), config.leaseTakeoverMillis(), heldLeases, messages, dialect, writeGate);
        JdbcSharedStateRepository sharedState = new JdbcSharedStateRepository(
                () -> dataSource, executor, dialect, config.serverId(), writeGate);
        DatabaseMaintenance maintenance = new DatabaseMaintenance(
                () -> dataSource, dialect, engine, config, writeGate);
        ScheduledExecutorService leaseScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "emaki-db-lease");
            thread.setDaemon(true);
            return thread;
        });
        DatabaseService service = new DatabaseService(config, dataSource, executor, leaseScheduler,
                repository, lease, sharedState, maintenance, writeGate, heldLeases, messages);
        leaseScheduler.scheduleAtFixedRate(service::renewHeldLeases,
                config.leaseHeartbeatMillis(), config.leaseHeartbeatMillis(), TimeUnit.MILLISECONDS);
        return service;
    }

    public PlayerDomainRepository repository() {
        return repository;
    }

    public PlayerDataLease lease() {
        return lease;
    }

    public SharedStateRepository sharedState() {
        return sharedState;
    }

    public DatabaseMaintenance maintenance() {
        return maintenance;
    }

    public DatabaseConfig config() {
        return config;
    }

    public void close() {
        leaseScheduler.shutdownNow();
        for (UUID player : heldLeases) {
            lease.release(player);
        }
        heldLeases.clear();
        executor.shutdown();
        try {
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }

    private void renewHeldLeases() {
        if (heldLeases.isEmpty()) {
            return;
        }
        long until = System.currentTimeMillis() + config.leaseTtlMillis();
        for (UUID player : heldLeases) {
            try (SingleWriterGate.Scope ignored = writeGate.enter();
                    Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE emaki_data_lease SET lease_until = ? WHERE player_uuid = ? AND server_id = ?")) {
                statement.setLong(1, until);
                statement.setBytes(2, UuidBytes.toBytes(player));
                statement.setString(3, config.serverId());
                if (statement.executeUpdate() == 0) {
                    heldLeases.remove(player);
                    warn("console.database_lease_lost", Map.of("player", String.valueOf(player)));
                }
            } catch (SQLException exception) {
                return;
            }
        }
    }

    private void warn(String key, Map<String, ?> replacements) {
        if (messages != null) {
            messages.warning(LogTag.PERSISTENCE, key, replacements);
        }
    }
}
