package emaki.jiuwu.craft.corelib.db;

import java.util.Locale;

public enum DbEngine {

    MYSQL("mysql"),
    MARIADB("mariadb"),
    POSTGRES("postgresql"),
    SQLITE("sqlite");

    private final String id;

    DbEngine(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DbEngine resolve(String configured, String host) {
        String value = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "mysql":
                return MYSQL;
            case "mariadb":
                return MARIADB;
            case "postgresql":
            case "postgres":
            case "pg":
                return POSTGRES;
            case "sqlite":
                return SQLITE;
            case "auto":
            case "":
                return detect(host);
            default:
                throw new IllegalArgumentException("未知数据库引擎: " + configured);
        }
    }

    private static DbEngine detect(String host) {
        String value = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("jdbc:sqlite:")) {
            return SQLITE;
        }
        if (value.startsWith("jdbc:postgresql:")) {
            return POSTGRES;
        }
        if (value.startsWith("jdbc:mariadb:")) {
            return MARIADB;
        }
        if (value.startsWith("jdbc:mysql:")) {
            return MYSQL;
        }
        if (looksLikeSqliteFile(value)) {
            return SQLITE;
        }
        return MYSQL;
    }

    private static boolean looksLikeSqliteFile(String value) {
        boolean sqliteName = value.endsWith(".db") || value.endsWith(".sqlite") || value.endsWith(".sqlite3");
        return sqliteName && (value.contains("/") || value.contains("\\") || value.startsWith("."));
    }
}
