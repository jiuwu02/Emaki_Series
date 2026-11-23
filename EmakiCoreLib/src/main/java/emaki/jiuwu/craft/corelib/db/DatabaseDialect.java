package emaki.jiuwu.craft.corelib.db;

final class DatabaseDialect {

    private final DbEngine engine;
    private final String uuidType;
    private final String payloadType;
    private final String valueType;
    private final String tableOptions;
    private final boolean serialWrites;

    private DatabaseDialect(DbEngine engine, String uuidType, String payloadType, String valueType,
            String tableOptions, boolean serialWrites) {
        this.engine = engine;
        this.uuidType = uuidType;
        this.payloadType = payloadType;
        this.valueType = valueType;
        this.tableOptions = tableOptions;
        this.serialWrites = serialWrites;
    }

    static DatabaseDialect forEngine(DbEngine engine) {
        switch (engine) {
            case MYSQL:
            case MARIADB:
                return new DatabaseDialect(engine, "BINARY(16)", "MEDIUMBLOB", "LONGTEXT",
                        " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4", false);
            case POSTGRES:
                return new DatabaseDialect(engine, "BYTEA", "BYTEA", "TEXT", "", false);
            case SQLITE:
                return new DatabaseDialect(engine, "BLOB", "BLOB", "BLOB", "", true);
            default:
                throw new IllegalArgumentException("未知数据库引擎: " + engine);
        }
    }

    DbEngine engine() {
        return engine;
    }

    String uuidType() {
        return uuidType;
    }

    String payloadType() {
        return payloadType;
    }

    String valueType() {
        return valueType;
    }

    String tableOptions() {
        return tableOptions;
    }

    boolean serialWrites() {
        return serialWrites;
    }

    boolean mysqlFamily() {
        return engine == DbEngine.MYSQL || engine == DbEngine.MARIADB;
    }

    String inlineIndex(String name, String columns) {
        return mysqlFamily() ? ", KEY " + name + " (" + columns + ")" : "";
    }

    String indexDdl(String table, String name, String columns) {
        return mysqlFamily() ? "" : "CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + columns + ")";
    }

    String autoIncrementColumn(String name) {
        switch (engine) {
            case MYSQL:
            case MARIADB:
                return name + " BIGINT NOT NULL AUTO_INCREMENT";
            case POSTGRES:
                return name + " BIGSERIAL NOT NULL";
            default:
                return name + " INTEGER PRIMARY KEY AUTOINCREMENT";
        }
    }

    String autoIncrementUniqueKey(String name, String column) {
        return mysqlFamily() ? ", UNIQUE KEY " + name + " (" + column + ")" : "";
    }

    String sharedStateKey() {
        return engine == DbEngine.SQLITE ? "UNIQUE (namespace, k)" : "PRIMARY KEY (namespace, k)";
    }

    String insertIgnoreStart() {
        return mysqlFamily() ? "INSERT IGNORE" : "INSERT";
    }

    String insertIgnoreTail() {
        return mysqlFamily() ? "" : " ON CONFLICT DO NOTHING";
    }

    String upsertTail(String table, String primaryKeyColumns, String assignments) {
        return mysqlFamily()
                ? " ON DUPLICATE KEY UPDATE " + assignments
                : " ON CONFLICT (" + primaryKeyColumns + ") DO UPDATE SET " + assignments;
    }

    String excluded(String column) {
        return mysqlFamily() ? "VALUES(" + column + ")" : "excluded." + column;
    }

    String lockTail() {
        return engine == DbEngine.SQLITE ? "" : " FOR UPDATE";
    }

    String maxExpr(String left, String right) {
        return engine == DbEngine.SQLITE ? "MAX(" + left + ", " + right + ")" : "GREATEST(" + left + ", " + right + ")";
    }
}
