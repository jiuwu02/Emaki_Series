package emaki.jiuwu.craft.corelib.db;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

final class JdbcDrivers {

    private JdbcDrivers() {
    }

    static void register(DbEngine engine) throws SQLException, ClassNotFoundException {
        switch (engine) {
            case MYSQL:
            case MARIADB:
                DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
                break;
            case POSTGRES:
                Class.forName("org.postgresql.Driver");
                break;
            default:
                Class.forName("org.sqlite.JDBC");
                break;
        }
    }

    static String driverClassName(DbEngine engine) {
        switch (engine) {
            case POSTGRES:
                return "org.postgresql.Driver";
            case SQLITE:
                return "org.sqlite.JDBC";
            default:
                return com.mysql.cj.jdbc.Driver.class.getName();
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
