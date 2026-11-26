package emaki.jiuwu.craft.corelib.db;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

final class JdbcDrivers {

    // MySQL 驱动与连接池自 5.0.16 起由 RuntimeLibraryLoader 在运行期准备, 不在插件 Jar 内。
    // 这里一律用字符串类名 + Class.forName, 不用类字面量, 避免驱动缺席时在方法解析阶段
    // 直接抛 NoClassDefFoundError (那是 Error 不是 Exception, 会绕过 startup_policy 的 try/catch)。
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String POSTGRES_DRIVER = "org.postgresql.Driver";
    private static final String SQLITE_DRIVER = "org.sqlite.JDBC";

    private JdbcDrivers() {
    }

    static void register(DbEngine engine) throws SQLException, ClassNotFoundException {
        switch (engine) {
            case MYSQL:
            case MARIADB:
                Class<?> mysqlDriver = Class.forName(MYSQL_DRIVER);
                try {
                    DriverManager.registerDriver((java.sql.Driver) mysqlDriver.getDeclaredConstructor().newInstance());
                } catch (ReflectiveOperationException exception) {
                    throw new ClassNotFoundException("MySQL 驱动无法实例化: " + MYSQL_DRIVER, exception);
                }
                break;
            case POSTGRES:
                Class.forName(POSTGRES_DRIVER);
                break;
            default:
                Class.forName(SQLITE_DRIVER);
                break;
        }
    }

    static String driverClassName(DbEngine engine) {
        switch (engine) {
            case POSTGRES:
                return POSTGRES_DRIVER;
            case SQLITE:
                return SQLITE_DRIVER;
            default:
                return MYSQL_DRIVER;
        }
    }

    static String jdbcUrl(DbEngine engine, DatabaseConfig config, Path baseDirectory) {
        String host = config.host() == null ? "" : config.host().trim();
        if (host.startsWith("jdbc:")) {
            return host;
        }
        switch (engine) {
            case POSTGRES:
                return "jdbc:postgresql://" + config.host() + ":" + config.port() + "/" + config.database()
                        + (config.ssl() ? "?sslmode=require" : "?sslmode=disable");
            case SQLITE:
                return "jdbc:sqlite:" + sqlitePath(config.database(), baseDirectory)
                        + "?transaction_mode=IMMEDIATE&busy_timeout=5000";
            default:
                return "jdbc:mysql://" + config.host() + ":" + config.port() + "/" + config.database()
                        + (config.ssl() ? "?useSSL=true&requireSSL=true" : "?useSSL=false");
        }
    }

    static Path sqlitePath(String database, Path baseDirectory) {
        String value = database == null || database.isBlank() ? "emaki" : database.trim();
        Path candidate = Path.of(value);
        if (candidate.isAbsolute()) {
            return candidate;
        }
        if (value.contains("/") || value.contains("\\") || value.endsWith(".db") || value.endsWith(".sqlite")
                || value.endsWith(".sqlite3")) {
            return baseDirectory.resolve(value).normalize();
        }
        return baseDirectory.resolve(value + ".db");
    }
}
