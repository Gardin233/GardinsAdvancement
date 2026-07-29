package org.gardin.gardinsadvancement.storage;

import org.gardin.gardinsadvancement.conf.DatabaseConfig;
import org.gardin.gardinsadvancement.util.GLogger;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class JdbcAdvancementStorage implements AdvancementStorage {
    private static final String TABLE_NAME = "ga_player_advancements";

    private final File dataFolder;
    private final DatabaseConfig config;
    private boolean available;

    public JdbcAdvancementStorage(File dataFolder, DatabaseConfig config) {
        this.dataFolder = dataFolder;
        this.config = config;
    }

    @Override
    public void initialize() {
        try {
            ensureDriverLoaded();
            ensureParentFolder();
            ensureMySqlDatabase();
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(buildCreateTableSql());
            }
            available = true;
            GLogger.infoLang("storage.connected", config.getType().name());
        } catch (Exception exception) {
            available = false;
            GLogger.errorLang("storage.connect_failed", config.getType().name(), exception.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public Map<String, PlayerAdvancementRecord> loadPlayerRecords(UUID playerUuid) {
        if (!available || playerUuid == null) {
            return Map.of();
        }
        String sql = "SELECT advancement_key, finished, completed_at FROM " + TABLE_NAME + " WHERE player_uuid = ?";
        Map<String, PlayerAdvancementRecord> records = new LinkedHashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String advancementKey = resultSet.getString("advancement_key");
                    boolean finished = resultSet.getBoolean("finished");
                    long completedAtRaw = resultSet.getLong("completed_at");
                    Long completedAt = resultSet.wasNull() ? null : completedAtRaw;
                    records.put(advancementKey, new PlayerAdvancementRecord(advancementKey, finished, completedAt));
                }
            }
            return Map.copyOf(records);
        } catch (SQLException exception) {
            GLogger.errorLang("storage.load_failed", playerUuid, exception.getMessage());
            return Map.of();
        }
    }

    @Override
    public void saveAdvancementState(UUID playerUuid, String advancementKey, boolean finished, Long completedAt) {
        if (!available || playerUuid == null || advancementKey == null || advancementKey.isBlank()) {
            return;
        }
        String sql = buildUpsertSql();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setString(2, advancementKey);
            statement.setBoolean(3, finished);
            if (completedAt == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, completedAt);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            GLogger.errorLang("storage.save_failed", playerUuid, advancementKey, exception.getMessage());
        }
    }

    @Override
    public void close() {
        if (available) {
            GLogger.infoLang("storage.closed", config.getType().name());
        }
        available = false;
    }

    private void ensureDriverLoaded() throws ClassNotFoundException {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLITE) {
            Class.forName("org.sqlite.JDBC");
            return;
        }
        Class.forName("com.mysql.cj.jdbc.Driver");
    }

    private void ensureParentFolder() {
        if (config.getType() != DatabaseConfig.DatabaseType.SQLITE) {
            return;
        }
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            GLogger.warningLang("storage.folder_create_failed", dataFolder.getAbsolutePath());
        }
    }

    private Connection openConnection() throws SQLException {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLITE) {
            File databaseFile = new File(dataFolder, config.getSqliteFile());
            String jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            return DriverManager.getConnection(jdbcUrl);
        }
        DatabaseConfig.MysqlConfig mysql = config.getMysql();
        String jdbcUrl = buildMysqlJdbcUrl(mysql.getDatabase());
        return DriverManager.getConnection(jdbcUrl, mysql.getUsername(), mysql.getPassword());
    }

    private void ensureMySqlDatabase() throws SQLException {
        if (config.getType() != DatabaseConfig.DatabaseType.MYSQL) {
            return;
        }
        DatabaseConfig.MysqlConfig mysql = config.getMysql();
        try (Connection connection = DriverManager.getConnection(
                buildMysqlJdbcUrl(null),
                mysql.getUsername(),
                mysql.getPassword()
        ); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + mysql.getDatabase() + "`");
        }
    }

    private String buildCreateTableSql() {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLITE) {
            return "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + "player_uuid TEXT NOT NULL,"
                    + "advancement_key TEXT NOT NULL,"
                    + "finished INTEGER NOT NULL,"
                    + "completed_at INTEGER NULL,"
                    + "PRIMARY KEY (player_uuid, advancement_key)"
                    + ")";
        }
        return "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "advancement_key VARCHAR(255) NOT NULL,"
                + "finished TINYINT(1) NOT NULL,"
                + "completed_at BIGINT NULL,"
                + "PRIMARY KEY (player_uuid, advancement_key)"
                + ")";
    }

    private String buildUpsertSql() {
        if (config.getType() == DatabaseConfig.DatabaseType.SQLITE) {
            return "INSERT INTO " + TABLE_NAME + " (player_uuid, advancement_key, finished, completed_at) "
                    + "VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT(player_uuid, advancement_key) DO UPDATE SET "
                    + "finished = excluded.finished, "
                    + "completed_at = excluded.completed_at";
        }
        return "INSERT INTO " + TABLE_NAME + " (player_uuid, advancement_key, finished, completed_at) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "finished = VALUES(finished), "
                + "completed_at = VALUES(completed_at)";
    }

    private String buildMysqlJdbcUrl(String database) {
        DatabaseConfig.MysqlConfig mysql = config.getMysql();
        StringBuilder builder = new StringBuilder("jdbc:mysql://")
                .append(mysql.getHost())
                .append(":")
                .append(mysql.getPort());
        if (database != null && !database.isBlank()) {
            builder.append("/").append(database);
        }
        builder.append("?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        return builder.toString();
    }
}
