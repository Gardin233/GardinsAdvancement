package org.gardin.gardinsadvancement.conf;

public class DatabaseConfig {
    private final DatabaseType type;
    private final String sqliteFile;
    private final MysqlConfig mysql;

    public DatabaseConfig(DatabaseType type, String sqliteFile, MysqlConfig mysql) {
        this.type = type;
        this.sqliteFile = sqliteFile;
        this.mysql = mysql;
    }

    public DatabaseType getType() {
        return type;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    public MysqlConfig getMysql() {
        return mysql;
    }

    public enum DatabaseType {
        SQLITE,
        MYSQL;

        public static DatabaseType fromRaw(String raw) {
            if (raw == null || raw.isBlank()) {
                return SQLITE;
            }
            for (DatabaseType value : values()) {
                if (value.name().equalsIgnoreCase(raw.trim())) {
                    return value;
                }
            }
            return SQLITE;
        }
    }

    public static class MysqlConfig {
        private final String host;
        private final int port;
        private final String username;
        private final String password;
        private final String database;

        public MysqlConfig(String host, int port, String username, String password, String database) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.database = database;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getDatabase() {
            return database;
        }
    }
}
