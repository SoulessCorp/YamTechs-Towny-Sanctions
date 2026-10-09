package yt.yamtechs.sanctions;

import com.palmergames.bukkit.towny.object.Nation;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SanctionManager {
    private final SanctionsPlugin plugin;
    private final Map<String, Sanction> sanctions = new LinkedHashMap<>();

    public SanctionManager(SanctionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        sanctions.clear();
        for (Map.Entry<String, YamlConfiguration> item : plugin.getStorage().loadCollection("sanctions").entrySet()) {
            YamlConfiguration entry = item.getValue();
            try {
                UUID targetId = UUID.fromString(entry.getString("target-uuid", ""));
                String targetName = entry.getString("target-name", "Unknown");
                Sanction.TargetType targetType = parseEnum(Sanction.TargetType.class,
                        entry.getString("target-type", "NATION"), Sanction.TargetType.NATION);
                String issuerUuidText = entry.getString("issuer-uuid", "");
                UUID issuerId = issuerUuidText.isBlank() ? null : UUID.fromString(issuerUuidText);
                Sanction.IssuerType legacyIssuer = issuerId == null
                        ? Sanction.IssuerType.SERVER : Sanction.IssuerType.NATION;
                Sanction.IssuerType issuerType = parseEnum(Sanction.IssuerType.class,
                        entry.getString("issuer-type", legacyIssuer.name()), legacyIssuer);
                Sanction sanction = new Sanction(targetType, targetId, targetName, issuerType, issuerId,
                        entry.getString("issuer-name", "Server"), entry.getString("imposed-by", "Unknown"),
                        entry.getLong("expires-at", -1L), entry.getString("reason", "No reason given."));
                sanctions.put(sanction.storageKey(), sanction);
                if (!item.getKey().equals(sanction.storageKey())) {
                    plugin.getStorage().deleteRecord("sanctions", item.getKey());
                    save(sanction);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid sanction record '" + item.getKey() + "'.");
            }
        }
        removeExpired();
    }

    public List<Sanction> allActive() {
        removeExpired();
        return sanctions.values().stream()
                .sorted(Comparator.comparing(Sanction::getTargetName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Sanction::getIssuerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<Sanction> forNation(Nation nation) {
        removeExpired();
        List<Sanction> active = new ArrayList<>();
        AllyTeam team = plugin.getAllyTeamManager().findTeamForNation(nation.getUUID());
        for (Sanction sanction : sanctions.values()) {
            boolean targetsNation = sanction.getTargetType() == Sanction.TargetType.NATION
                    && sanction.getTargetId().equals(nation.getUUID());
            boolean targetsTeam = team != null && sanction.getTargetType() == Sanction.TargetType.ALLY_TEAM
                    && sanction.getTargetId().equals(team.getId());
            if (targetsNation || targetsTeam) {
                active.add(sanction);
            }
        }
        active.sort(Comparator.comparing(Sanction::getIssuerName, String.CASE_INSENSITIVE_ORDER));
        return active;
    }

    public List<Sanction> forTarget(Sanction.TargetType targetType, UUID targetId) {
        removeExpired();
        return sanctions.values().stream()
                .filter(sanction -> sanction.getTargetType() == targetType
                        && sanction.getTargetId().equals(targetId))
                .sorted(Comparator.comparing(Sanction::getIssuerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean hasActiveSanction(Nation nation) {
        return !forNation(nation).isEmpty();
    }

    public Sanction add(Sanction.TargetType targetType, UUID targetId, String targetName,
                        Sanction.IssuerType issuerType, UUID issuerId, String issuerName,
                        String imposedBy, long expiresAt, String reason) {
        Sanction sanction = new Sanction(targetType, targetId, targetName, issuerType, issuerId,
                issuerName, imposedBy, expiresAt, reason);
        sanctions.put(sanction.storageKey(), sanction);
        save(sanction);
        return sanction;
    }

    public int removeFromTarget(Sanction.TargetType targetType, UUID targetId,
                                Sanction.IssuerType issuerType, UUID issuerId) {
        List<Sanction> remove = sanctions.values().stream()
                .filter(sanction -> sanction.getTargetType() == targetType
                        && sanction.getTargetId().equals(targetId)
                        && sanction.getIssuerType() == issuerType
                        && java.util.Objects.equals(sanction.getIssuerId(), issuerId))
                .toList();
        remove.forEach(this::delete);
        return remove.size();
    }

    public int removeAllForTarget(Sanction.TargetType targetType, UUID targetId) {
        List<Sanction> remove = sanctions.values().stream()
                .filter(sanction -> sanction.getTargetType() == targetType
                        && sanction.getTargetId().equals(targetId))
                .toList();
        remove.forEach(this::delete);
        return remove.size();
    }

    public int removeAllByIssuer(Sanction.IssuerType issuerType, UUID issuerId) {
        List<Sanction> remove = sanctions.values().stream()
                .filter(sanction -> sanction.getIssuerType() == issuerType
                        && java.util.Objects.equals(sanction.getIssuerId(), issuerId))
                .toList();
        remove.forEach(this::delete);
        return remove.size();
    }

    public int clear() {
        int removed = sanctions.size();
        sanctions.clear();
        plugin.getStorage().clearCollection("sanctions");
        return removed;
    }

    public void removeExpired() {
        long now = System.currentTimeMillis();
        List<Sanction> expired = sanctions.values().stream().filter(sanction -> sanction.isExpired(now)).toList();
        expired.forEach(this::delete);
    }

    private void save(Sanction sanction) {
        YamlConfiguration record = new YamlConfiguration();
        record.set("target-type", sanction.getTargetType().name());
        record.set("target-uuid", sanction.getTargetId().toString());
        record.set("target-name", sanction.getTargetName());
        record.set("issuer-type", sanction.getIssuerType().name());
        record.set("issuer-uuid", sanction.getIssuerId() == null ? "" : sanction.getIssuerId().toString());
        record.set("issuer-name", sanction.getIssuerName());
        record.set("imposed-by", sanction.getImposedBy());
        record.set("expires-at", sanction.getExpiresAt());
        record.set("reason", sanction.getReason());
        plugin.getStorage().saveRecord("sanctions", sanction.storageKey(), record);
    }

    private void delete(Sanction sanction) {
        sanctions.remove(sanction.storageKey());
        plugin.getStorage().deleteRecord("sanctions", sanction.storageKey());
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }
}
