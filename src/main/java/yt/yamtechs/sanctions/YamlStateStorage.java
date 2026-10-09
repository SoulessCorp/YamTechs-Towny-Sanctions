package yt.yamtechs.sanctions;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class YamlStateStorage implements StateStorage {
    private final SanctionsPlugin plugin;

    public YamlStateStorage(SanctionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public synchronized Map<String, YamlConfiguration> loadCollection(String collection) {
        File file = fileFor(collection);
        if (!file.isFile()) {
            return new LinkedHashMap<>();
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = data.getConfigurationSection(collection);
        Map<String, YamlConfiguration> records = new LinkedHashMap<>();
        if (section == null) {
            return records;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry != null) {
                records.put(key, copySection(entry));
            }
        }
        return records;
    }

    @Override
    public synchronized void saveRecord(String collection, String key, YamlConfiguration record) {
        File file = fileFor(collection);
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String path = collection + "." + key;
        data.set(path, null);
        data.createSection(path, toMap(record));
        save(file, data);
    }

    @Override
    public synchronized void deleteRecord(String collection, String key) {
        File file = fileFor(collection);
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        data.set(collection + "." + key, null);
        save(file, data);
    }

    @Override
    public synchronized void clearCollection(String collection) {
        File file = fileFor(collection);
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        data.set(collection, null);
        save(file, data);
    }

    @Override
    public void close() {
    }

    static YamlConfiguration copySection(ConfigurationSection section) {
        YamlConfiguration copy = new YamlConfiguration();
        for (String path : section.getKeys(true)) {
            if (!section.isConfigurationSection(path)) {
                copy.set(path, section.get(path));
            }
        }
        return copy;
    }

    static Map<String, Object> toMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection child = section.getConfigurationSection(key);
            result.put(key, child == null ? section.get(key) : toMap(child));
        }
        return result;
    }

    static YamlConfiguration loadRecord(String payload) throws InvalidConfigurationException {
        YamlConfiguration record = new YamlConfiguration();
        record.loadFromString(payload);
        return record;
    }

    private File fileFor(String collection) {
        return new File(plugin.getDataFolder(), collection + ".yml");
    }

    private void save(File file, YamlConfiguration data) {
        try {
            plugin.getDataFolder().mkdirs();
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save " + file.getName() + ": " + exception.getMessage());
        }
    }
}
