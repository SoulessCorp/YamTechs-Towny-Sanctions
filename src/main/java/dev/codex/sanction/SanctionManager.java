package dev.codex.sanction;

import com.palmergames.bukkit.towny.object.Nation;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SanctionManager {
    private final SanctionPlugin plugin;
    private final Map<String, Sanction> sanctions = new LinkedHashMap<>();
    private final File dataFile;

    public SanctionManager(SanctionPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "sanctions.yml");
    }

    public void load() {
        sanctions.clear();
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            save();
            return;
        }

        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("sanctions");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            try {
                UUID targetId = UUID.fromString(entry.getString("target-uuid", ""));
                String targetName = entry.getString("target-name", "Unknown");
                String issuerUuidText = entry.getString("issuer-uuid", "");
                UUID issuerId = issuerUuidText.isBlank() ? null : UUID.fromString(issuerUuidText);
                String issuerName = entry.getString("issuer-name", "Server");
                String imposedBy = entry.getString("imposed-by", "Unknown");
                long expiresAt = entry.getLong("expires-at", -1L);
                String reason = entry.getString("reason", "No reason given.");
                Sanction sanction = new Sanction(targetId, targetName, issuerId, issuerName,
                        imposedBy, expiresAt, reason);
                sanctions.put(sanction.storageKey(), sanction);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid sanction record '" + key + "'.");
            }
        }
        removeExpired();
    }

    public List<Sanction> allActive() {
        removeExpired();
        List<Sanction> active = new ArrayList<>(sanctions.values());
        active.sort(Comparator.comparing(Sanction::getTargetNationName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Sanction::getIssuerName, String.CASE_INSENSITIVE_ORDER));
        return active;
    }

    public List<Sanction> forNation(Nation nation) {
        return forNation(nation.getUUID());
    }

    public List<Sanction> forNation(UUID nationId) {
        removeExpired();
        return sanctions.values().stream()
                .filter(sanction -> sanction.getTargetNationId().equals(nationId))
                .sorted(Comparator.comparing(Sanction::getIssuerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean hasActiveSanction(Nation nation) {
        return !forNation(nation).isEmpty();
    }

    public Sanction add(Nation target, UUID issuerId, String issuerName, String imposedBy,
                        long expiresAt, String reason) {
        Sanction sanction = new Sanction(target.getUUID(), target.getName(), issuerId,
                issuerName, imposedBy, expiresAt, reason);
        sanctions.put(sanction.storageKey(), sanction);
        save();
        return sanction;
    }

    public int removeFromNation(Nation target, UUID issuerId) {
        int before = sanctions.size();
        sanctions.values().removeIf(sanction -> sanction.getTargetNationId().equals(target.getUUID())
                && issuerId != null && issuerId.equals(sanction.getIssuerNationId()));
        int removed = before - sanctions.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public int removeAllForNation(Nation target) {
        int before = sanctions.size();
        sanctions.values().removeIf(sanction -> sanction.getTargetNationId().equals(target.getUUID()));
        int removed = before - sanctions.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public int clear() {
        int removed = sanctions.size();
        sanctions.clear();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public void removeExpired() {
        long now = System.currentTimeMillis();
        int before = sanctions.size();
        sanctions.values().removeIf(sanction -> sanction.isExpired(now));
        if (before != sanctions.size()) {
            save();
        }
    }

    private void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Sanction sanction : sanctions.values()) {
            String root = "sanctions." + sanction.storageKey();
            data.set(root + ".target-uuid", sanction.getTargetNationId().toString());
            data.set(root + ".target-name", sanction.getTargetNationName());
            data.set(root + ".issuer-uuid", sanction.getIssuerNationId() == null
                    ? "" : sanction.getIssuerNationId().toString());
            data.set(root + ".issuer-name", sanction.getIssuerName());
            data.set(root + ".imposed-by", sanction.getImposedBy());
            data.set(root + ".expires-at", sanction.getExpiresAt());
            data.set(root + ".reason", sanction.getReason());
        }
        try {
            plugin.getDataFolder().mkdirs();
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save sanctions.yml: " + exception.getMessage());
        }
    }
}
