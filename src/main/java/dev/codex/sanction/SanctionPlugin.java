package dev.codex.sanction;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/** Plugin entry point; compiled for Java 17 bytecode compatibility. */
public final class SanctionPlugin extends JavaPlugin {
    private SanctionManager sanctionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        sanctionManager = new SanctionManager(this);
        sanctionManager.load();

        SanctionCommand commandHandler = new SanctionCommand(this, sanctionManager);
        PluginCommand command = getCommand("sanction");
        if (command == null) {
            throw new IllegalStateException("The sanction command is missing from plugin.yml");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        Bukkit.getPluginManager().registerEvents(new EmbargoListener(this, sanctionManager), this);
        Bukkit.getScheduler().runTaskTimer(this, sanctionManager::removeExpired, 1200L, 1200L);
        getLogger().info("Sanction is enabled. Towny nation trade embargoes are active.");
    }

    public SanctionManager getSanctionManager() {
        return sanctionManager;
    }
}
