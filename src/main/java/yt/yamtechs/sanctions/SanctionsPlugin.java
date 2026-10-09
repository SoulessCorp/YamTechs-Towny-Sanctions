package yt.yamtechs.sanctions;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** YamTech's Towny diplomacy and trade-sanctions plugin. */
public final class SanctionsPlugin extends JavaPlugin {
    private StateStorage storage;
    private SanctionManager sanctionManager;
    private AllyTeamManager allyTeamManager;

    @Override
    public void onEnable() {
        migrateLegacyFolder();
        saveDefaultConfig();
        openStorage();

        allyTeamManager = new AllyTeamManager(this);
        allyTeamManager.load();
        sanctionManager = new SanctionManager(this);
        sanctionManager.load();

        SanctionCommand sanctions = new SanctionCommand(this, sanctionManager);
        register("sanction", sanctions);
        register("allyteam", new AllyTeamCommand(this, allyTeamManager));

        Bukkit.getPluginManager().registerEvents(new EmbargoListener(this, sanctionManager), this);
        Bukkit.getScheduler().runTaskTimer(this, sanctionManager::removeExpired, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, allyTeamManager::resolveExpired, 1200L, 1200L);
        getLogger().info("YamTech Sanctions enabled using " + getConfig().getString("storage.type", "YAML")
                + " storage.");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
    }

    public SanctionManager getSanctionManager() {
        return sanctionManager;
    }

    public AllyTeamManager getAllyTeamManager() {
        return allyTeamManager;
    }

    public StateStorage getStorage() {
        return storage;
    }

    public void reloadPluginState() {
        String oldType = getConfig().getString("storage.type", "YAML").toUpperCase();
        Map<String, Map<String, org.bukkit.configuration.file.YamlConfiguration>> oldData = new LinkedHashMap<>();
        if (storage != null) {
            oldData.put("sanctions", storage.loadCollection("sanctions"));
            oldData.put("allyteams", storage.loadCollection("allyteams"));
            storage.close();
        }
        reloadConfig();
        openStorage();
        String newType = getConfig().getString("storage.type", "YAML").toUpperCase();
        if (!oldType.equals(newType)) {
            oldData.forEach((collection, records) -> {
                storage.loadCollection(collection);
                storage.clearCollection(collection);
                records.forEach((key, record) -> storage.saveRecord(collection, key, record));
            });
        }
        allyTeamManager.load();
        sanctionManager.load();
    }

    private void register(String commandName, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            throw new IllegalStateException("The " + commandName + " command is missing from plugin.yml");
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void openStorage() {
        String type = getConfig().getString("storage.type", "YAML");
        if ("sqlite".equalsIgnoreCase(type)) {
            try {
                storage = new SqliteStateStorage(this);
                return;
            } catch (IllegalStateException exception) {
                getLogger().severe("SQLite could not start; falling back to YAML: " + exception.getMessage());
            }
        } else if (!"yaml".equalsIgnoreCase(type)) {
            getLogger().warning("Unknown storage.type '" + type + "'; using YAML.");
        }
        storage = new YamlStateStorage(this);
    }

    private void migrateLegacyFolder() {
        File current = getDataFolder();
        File oldFolder = new File(current.getParentFile(), "Sanction");
        if (!oldFolder.isDirectory() || oldFolder.equals(current)) {
            return;
        }
        current.mkdirs();
        for (String name : new String[]{"config.yml", "sanctions.yml"}) {
            File oldFile = new File(oldFolder, name);
            File newFile = new File(current, name);
            if (!newFile.exists() && oldFile.isFile()) {
                try {
                    Files.copy(oldFile.toPath(), newFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    getLogger().info("Migrated legacy " + name + " from the Sanction data folder.");
                } catch (IOException exception) {
                    getLogger().warning("Could not migrate " + name + ": " + exception.getMessage());
                }
            }
        }
    }
}
