package yt.yamtechs.sanctions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SqliteStateStorage implements StateStorage {
    private final SanctionsPlugin plugin;
    private Connection connection;

    public SqliteStateStorage(SanctionsPlugin plugin) {
        this.plugin = plugin;
        try {
            plugin.getDataFolder().mkdirs();
            Class.forName("org.sqlite.JDBC");
            File database = new File(plugin.getDataFolder(), "sanctions.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + database.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS records ("
                        + "collection TEXT NOT NULL, record_key TEXT NOT NULL, payload TEXT NOT NULL, "
                        + "PRIMARY KEY(collection, record_key))");
                statement.executeUpdate("CREATE INDEX IF NOT EXISTS records_collection_idx ON records(collection)");
                statement.executeUpdate("CREATE TABLE IF NOT EXISTS initialized_collections ("
                        + "collection TEXT PRIMARY KEY)");
            }
        } catch (ReflectiveOperationException | SQLException exception) {
            throw new IllegalStateException("Could not open the SQLite database", exception);
        }
    }

    @Override
    public synchronized Map<String, YamlConfiguration> loadCollection(String collection) {
        Map<String, YamlConfiguration> records = new LinkedHashMap<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT record_key, payload FROM records WHERE collection = ? ORDER BY record_key")) {
            query.setString(1, collection);
            try (ResultSet result = query.executeQuery()) {
                while (result.next()) {
                    records.put(result.getString(1), YamlStateStorage.loadRecord(result.getString(2)));
                }
            }
            if (!isInitialized(collection)) {
                importLegacyYaml(collection, records);
                markInitialized(collection);
            }
        } catch (SQLException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Could not load SQLite collection '" + collection + "': "
                    + exception.getMessage());
        }
        return records;
    }

    @Override
    public synchronized void saveRecord(String collection, String key, YamlConfiguration record) {
        try (PreparedStatement update = connection.prepareStatement(
                "INSERT INTO records(collection, record_key, payload) VALUES(?, ?, ?) "
                        + "ON CONFLICT(collection, record_key) DO UPDATE SET payload = excluded.payload")) {
            update.setString(1, collection);
            update.setString(2, key);
            update.setString(3, record.saveToString());
            update.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not save SQLite record '" + key + "': " + exception.getMessage());
        }
    }

    @Override
    public synchronized void deleteRecord(String collection, String key) {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM records WHERE collection = ? AND record_key = ?")) {
            delete.setString(1, collection);
            delete.setString(2, key);
            delete.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not delete SQLite record '" + key + "': " + exception.getMessage());
        }
    }

    @Override
    public synchronized void clearCollection(String collection) {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM records WHERE collection = ?")) {
            delete.setString(1, collection);
            delete.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not clear SQLite collection '" + collection + "': "
                    + exception.getMessage());
        }
    }

    @Override
    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().warning("Could not close SQLite cleanly: " + exception.getMessage());
        } finally {
            connection = null;
        }
    }

    private void importLegacyYaml(String collection, Map<String, YamlConfiguration> records) {
        File legacyFile = new File(plugin.getDataFolder(), collection + ".yml");
        if (!legacyFile.isFile()) {
            return;
        }
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection section = legacy.getConfigurationSection(collection);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            YamlConfiguration record = YamlStateStorage.copySection(entry);
            saveRecord(collection, key, record);
            records.put(key, record);
        }
        if (!records.isEmpty()) {
            plugin.getLogger().info("Imported " + records.size() + " legacy " + collection + " record(s) into SQLite.");
        }
    }

    private boolean isInitialized(String collection) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM initialized_collections WHERE collection = ?")) {
            query.setString(1, collection);
            try (ResultSet result = query.executeQuery()) {
                return result.next();
            }
        }
    }

    private void markInitialized(String collection) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO initialized_collections(collection) VALUES(?)")) {
            insert.setString(1, collection);
            insert.executeUpdate();
        }
    }
}
