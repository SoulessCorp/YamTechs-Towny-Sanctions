package yt.yamtechs.sanctions;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;

public interface StateStorage extends AutoCloseable {
    Map<String, YamlConfiguration> loadCollection(String collection);

    void saveRecord(String collection, String key, YamlConfiguration record);

    void deleteRecord(String collection, String key);

    void clearCollection(String collection);

    @Override
    void close();
}
