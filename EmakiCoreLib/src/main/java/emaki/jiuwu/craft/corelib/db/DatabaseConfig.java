package emaki.jiuwu.craft.corelib.db;

import emaki.jiuwu.craft.corelib.api.yaml.YamlSection;

public record DatabaseConfig(
        boolean enabled,
        String engine,
        String host,
        int port,
        String database,
        String username,
        String password,
        boolean ssl,
        int poolSize,
        String serverId,
        int leaseTtlSeconds,
        int leaseHeartbeatSeconds,
        int leaseTakeoverSeconds,
        String startupPolicy) {

    public static DatabaseConfig defaults() {
        return new DatabaseConfig(false, "auto", "localhost", 3306, "emaki", "emaki", "",
                false, 10, "server-1", 45, 15, 90, "fail");
    }

    public static DatabaseConfig fromConfig(YamlSection section) {
        DatabaseConfig defaults = defaults();
        if (section == null) {
            return defaults;
        }
        YamlSection lease = section.getSection("lease");
        return new DatabaseConfig(
                Boolean.TRUE.equals(section.getBoolean("enabled", defaults.enabled())),
                section.getString("engine", defaults.engine()),
                section.getString("host", defaults.host()),
                section.getInt("port", defaults.port()),
                section.getString("database", defaults.database()),
                section.getString("username", defaults.username()),
                section.getString("password", defaults.password()),
                Boolean.TRUE.equals(section.getBoolean("ssl", defaults.ssl())),
                section.getInt("pool_size", defaults.poolSize()),
                section.getString("server_id", defaults.serverId()),
                lease == null ? defaults.leaseTtlSeconds() : lease.getInt("ttl_seconds", defaults.leaseTtlSeconds()),
                lease == null ? defaults.leaseHeartbeatSeconds()
                        : lease.getInt("heartbeat_seconds", defaults.leaseHeartbeatSeconds()),
                lease == null ? defaults.leaseTakeoverSeconds()
                        : lease.getInt("takeover_seconds", defaults.leaseTakeoverSeconds()),
                section.getString("startup_policy", defaults.startupPolicy()));
    }

    public long leaseTtlMillis() {
        return leaseTtlSeconds * 1000L;
    }

    public long leaseHeartbeatMillis() {
        return leaseHeartbeatSeconds * 1000L;
    }

    public long leaseTakeoverMillis() {
        return leaseTakeoverSeconds * 1000L;
    }

    public boolean fallbackFile() {
        return "fallback_file".equalsIgnoreCase(startupPolicy);
    }
}
