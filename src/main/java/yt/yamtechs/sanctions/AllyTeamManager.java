package yt.yamtechs.sanctions;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AllyTeamManager {
    private final SanctionsPlugin plugin;
    private final Map<UUID, AllyTeam> teams = new LinkedHashMap<>();
    private final Map<UUID, SanctionVote> proposals = new LinkedHashMap<>();

    public AllyTeamManager(SanctionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        teams.clear();
        proposals.clear();
        Map<String, YamlConfiguration> rows = plugin.getStorage().loadCollection("allyteams");
        for (Map.Entry<String, YamlConfiguration> entry : rows.entrySet()) {
            try {
                YamlConfiguration row = entry.getValue();
                String kind = row.getString("kind", "team");
                if (kind.equalsIgnoreCase("proposal")) {
                    SanctionVote proposal = loadProposal(row);
                    proposals.put(proposal.getId(), proposal);
                } else {
                    AllyTeam team = loadTeam(row);
                    teams.put(team.getId(), team);
                }
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipping invalid AllyTeam record '" + entry.getKey() + "'.");
            }
        }
    }

    public List<AllyTeam> allTeams() {
        return teams.values().stream().sorted(Comparator.comparing(AllyTeam::getName,
                String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public List<SanctionVote> proposalsFor(AllyTeam team) {
        return proposals.values().stream().filter(proposal -> proposal.getTeamId().equals(team.getId()))
                .sorted(Comparator.comparingLong(SanctionVote::getDeadline)).toList();
    }

    public AllyTeam findTeamByName(String name) {
        return teams.values().stream().filter(team -> team.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }

    public AllyTeam findTeamById(UUID id) {
        return teams.get(id);
    }

    public AllyTeam findTeamForNation(UUID nationId) {
        return teams.values().stream().filter(team -> team.containsNation(nationId)).findFirst().orElse(null);
    }

    public SanctionVote findProposal(String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        List<SanctionVote> matches = proposals.values().stream()
                .filter(proposal -> proposal.getId().toString().toLowerCase(Locale.ROOT).startsWith(normalized))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    public AllyTeam create(String name, Nation leaderNation) {
        if (name.isBlank() || findTeamByName(name) != null || findTeamForNation(leaderNation.getUUID()) != null) {
            return null;
        }
        AllyTeam team = new AllyTeam(UUID.randomUUID(), name.trim(), leaderNation.getUUID(), leaderNation.getName());
        teams.put(team.getId(), team);
        saveTeam(team);
        return team;
    }

    public boolean invite(AllyTeam team, Nation nation) {
        if (findTeamForNation(nation.getUUID()) != null || team.containsNation(nation.getUUID())) {
            return false;
        }
        boolean changed = team.invite(nation.getUUID(), nation.getName());
        if (changed) {
            saveTeam(team);
        }
        return changed;
    }

    public boolean accept(AllyTeam team, Nation nation) {
        if (findTeamForNation(nation.getUUID()) != null) {
            return false;
        }
        boolean changed = team.accept(nation.getUUID(), nation.getName());
        if (changed) {
            saveTeam(team);
        }
        return changed;
    }

    public void leave(AllyTeam team, Nation nation) {
        team.leave(nation.getUUID());
        saveTeam(team);
    }

    public void transferLeadership(AllyTeam team, Nation newLeader) {
        team.transferLeadership(newLeader.getUUID(), newLeader.getName());
        saveTeam(team);
    }

    public int disband(AllyTeam team) {
        teams.remove(team.getId());
        List<SanctionVote> teamProposals = proposalsFor(team);
        teamProposals.forEach(this::removeProposal);
        plugin.getSanctionManager().removeAllByIssuer(Sanction.IssuerType.ALLY_TEAM, team.getId());
        int sanctionsRemoved = plugin.getSanctionManager().removeAllForTarget(
                Sanction.TargetType.ALLY_TEAM, team.getId());
        plugin.getStorage().deleteRecord("allyteams", teamKey(team.getId()));
        return sanctionsRemoved;
    }

    public SanctionVote proposeSanction(AllyTeam team, Nation proposer, Nation target,
                                        long durationMillis, String reason) {
        if (team.containsNation(target.getUUID())) return null;
        return createProposal(team, proposer, target, false, durationMillis, reason);
    }

    public SanctionVote proposeLift(AllyTeam team, Nation proposer, Nation target) {
        boolean hasTeamSanction = plugin.getSanctionManager().forTarget(Sanction.TargetType.NATION, target.getUUID())
                .stream().anyMatch(sanction -> sanction.getIssuerType() == Sanction.IssuerType.ALLY_TEAM
                        && team.getId().equals(sanction.getIssuerId()));
        if (!hasTeamSanction) return null;
        return createProposal(team, proposer, target, true, -1L, "Approved lift vote");
    }

    private SanctionVote createProposal(AllyTeam team, Nation proposer, Nation target,
                                        boolean lifting, long durationMillis, String reason) {
        boolean duplicate = proposals.values().stream().anyMatch(proposal ->
                proposal.getTeamId().equals(team.getId())
                        && proposal.getTargetNationId().equals(target.getUUID()));
        if (duplicate) return null;
        SanctionVote proposal = new SanctionVote(UUID.randomUUID(), team.getId(), team.getName(), lifting,
                target.getUUID(), target.getName(), durationMillis, System.currentTimeMillis() + 86_400_000L,
                reason, proposer.getUUID(), proposer.getName(), team.getMembers());
        proposals.put(proposal.getId(), proposal);
        saveProposal(proposal);
        evaluate(proposal, false);
        return proposal;
    }

    public VoteResult castVote(SanctionVote proposal, Nation voterNation, boolean yes) {
        if (!proposal.canVote(voterNation.getUUID())) {
            return VoteResult.NOT_ELIGIBLE;
        }
        if (proposal.getVotes().containsKey(voterNation.getUUID())) {
            return VoteResult.ALREADY_VOTED;
        }
        proposal.setVote(voterNation.getUUID(), yes);
        saveProposal(proposal);
        VoteResult result = evaluate(proposal, false);
        return result == VoteResult.PENDING ? VoteResult.RECORDED : result;
    }

    public void resolveExpired() {
        long now = System.currentTimeMillis();
        for (SanctionVote proposal : new ArrayList<>(proposals.values())) {
            if (!teams.containsKey(proposal.getTeamId())) {
                removeProposal(proposal);
                continue;
            }
            if (now >= proposal.getDeadline()) {
                evaluate(proposal, true);
            }
        }
    }

    private VoteResult evaluate(SanctionVote proposal, boolean deadlineReached) {
        int majority = proposal.electorateSize() / 2 + 1;
        if (proposal.yesCount() >= majority) {
            Nation target = TownyAPI.getInstance().getNations().stream()
                    .filter(nation -> nation.getUUID().equals(proposal.getTargetNationId()))
                    .findFirst().orElse(null);
            AllyTeam team = teams.get(proposal.getTeamId());
            if (target != null && team != null) {
                if (proposal.isLifting()) {
                    int removed = plugin.getSanctionManager().removeFromTarget(Sanction.TargetType.NATION,
                            target.getUUID(), Sanction.IssuerType.ALLY_TEAM, team.getId());
                    announce(team, ChatColor.GREEN + "Vote passed: lifted " + removed + " AllyTeam sanction(s) from "
                            + target.getName() + ".");
                    removeProposal(proposal);
                    return VoteResult.PASSED;
                }
                long expiresAt = proposal.getSanctionDurationMillis() < 0 ? -1L
                        : System.currentTimeMillis() + proposal.getSanctionDurationMillis();
                plugin.getSanctionManager().add(Sanction.TargetType.NATION, target.getUUID(), target.getName(),
                        Sanction.IssuerType.ALLY_TEAM, team.getId(), team.getName(),
                        "AllyTeam vote proposed by " + proposal.getProposerNationName(), expiresAt,
                        proposal.getReason());
                announce(team, ChatColor.GREEN + "Vote passed: " + target.getName() + " is sanctioned.");
                removeProposal(proposal);
                return VoteResult.PASSED;
            }
            announce(team, ChatColor.RED + "Vote ended: target nation or AllyTeam no longer exists.");
            removeProposal(proposal);
            return VoteResult.FAILED;
        }
        if (proposal.noCount() >= majority) {
            announce(teams.get(proposal.getTeamId()), ChatColor.RED + "Vote failed: the majority voted no.");
            removeProposal(proposal);
            return VoteResult.FAILED;
        }
        if (proposal.voteCount() >= proposal.electorateSize() || deadlineReached) {
            AllyTeam team = teams.get(proposal.getTeamId());
            announce(team, ChatColor.RED + "Vote failed: it did not receive a majority (ties fail).");
            removeProposal(proposal);
            return VoteResult.FAILED;
        }
        return VoteResult.PENDING;
    }

    private void announce(AllyTeam team, String message) {
        if (team == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Nation nation = TownyAPI.getInstance().getNation(player);
            if (nation != null && team.containsNation(nation.getUUID())) {
                player.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "AllyTeam"
                        + ChatColor.DARK_GRAY + "] " + message);
            }
        }
        plugin.getLogger().info("AllyTeam " + team.getName() + ": " + ChatColor.stripColor(message));
    }

    private AllyTeam loadTeam(YamlConfiguration row) {
        UUID id = UUID.fromString(row.getString("uuid", ""));
        UUID leaderId = UUID.fromString(row.getString("leader-nation-uuid", ""));
        AllyTeam team = new AllyTeam(id, row.getString("name", "Unknown"), leaderId,
                row.getString("leader-nation-name", "Unknown"));
        ConfigurationSection members = row.getConfigurationSection("members");
        if (members != null) {
            for (String nationId : members.getKeys(false)) {
                UUID parsedId = UUID.fromString(nationId);
                if (!parsedId.equals(leaderId)) {
                    team.acceptedMemberForLoad(parsedId, members.getString(nationId, "Unknown"));
                }
            }
        }
        ConfigurationSection invites = row.getConfigurationSection("invitations");
        if (invites != null) {
            for (String nationId : invites.getKeys(false)) {
                team.invite(UUID.fromString(nationId), invites.getString(nationId, "Unknown"));
            }
        }
        String leaderName = row.getString("leader-nation-name", "Unknown");
        team.transferLeadership(leaderId, leaderName);
        return team;
    }

    private SanctionVote loadProposal(YamlConfiguration row) {
        UUID id = UUID.fromString(row.getString("uuid", ""));
        UUID teamId = UUID.fromString(row.getString("team-uuid", ""));
        UUID targetId = UUID.fromString(row.getString("target-nation-uuid", ""));
        UUID proposerId = UUID.fromString(row.getString("proposer-nation-uuid", ""));
        Map<UUID, String> electorate = uuidMap(row.getConfigurationSection("electorate"));
        SanctionVote proposal = new SanctionVote(id, teamId, row.getString("team-name", "Unknown"),
                row.getBoolean("lifting", false),
                targetId, row.getString("target-nation-name", "Unknown"), row.getLong("duration-ms", -1L),
                row.getLong("deadline", -1L), row.getString("reason", "No reason given."),
                proposerId, row.getString("proposer-nation-name", "Unknown"), electorate);
        ConfigurationSection votes = row.getConfigurationSection("votes");
        if (votes != null) {
            for (String nationId : votes.getKeys(false)) {
                proposal.setVote(UUID.fromString(nationId), votes.getBoolean(nationId));
            }
        }
        return proposal;
    }

    private Map<UUID, String> uuidMap(ConfigurationSection section) {
        Map<UUID, String> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            values.put(UUID.fromString(key), section.getString(key, "Unknown"));
        }
        return values;
    }

    private void saveTeam(AllyTeam team) {
        YamlConfiguration row = new YamlConfiguration();
        row.set("kind", "team");
        row.set("uuid", team.getId().toString());
        row.set("name", team.getName());
        row.set("leader-nation-uuid", team.getLeaderNationId().toString());
        row.set("leader-nation-name", team.getLeaderNationName());
        team.getMembers().forEach((id, name) -> row.set("members." + id, name));
        team.getInvitations().forEach((id, name) -> row.set("invitations." + id, name));
        plugin.getStorage().saveRecord("allyteams", teamKey(team.getId()), row);
    }

    private void saveProposal(SanctionVote proposal) {
        YamlConfiguration row = new YamlConfiguration();
        row.set("kind", "proposal");
        row.set("uuid", proposal.getId().toString());
        row.set("team-uuid", proposal.getTeamId().toString());
        row.set("team-name", proposal.getTeamName());
        row.set("lifting", proposal.isLifting());
        row.set("target-nation-uuid", proposal.getTargetNationId().toString());
        row.set("target-nation-name", proposal.getTargetNationName());
        row.set("duration-ms", proposal.getSanctionDurationMillis());
        row.set("deadline", proposal.getDeadline());
        row.set("reason", proposal.getReason());
        row.set("proposer-nation-uuid", proposal.getProposerNationId().toString());
        row.set("proposer-nation-name", proposal.getProposerNationName());
        proposal.getElectorate().forEach((id, name) -> row.set("electorate." + id, name));
        proposal.getVotes().forEach((id, yes) -> row.set("votes." + id, yes));
        plugin.getStorage().saveRecord("allyteams", proposalKey(proposal.getId()), row);
    }

    private void removeProposal(SanctionVote proposal) {
        proposals.remove(proposal.getId());
        plugin.getStorage().deleteRecord("allyteams", proposalKey(proposal.getId()));
    }

    private String teamKey(UUID id) { return "team_" + id; }
    private String proposalKey(UUID id) { return "proposal_" + id; }

    public enum VoteResult { PENDING, RECORDED, PASSED, FAILED, NOT_ELIGIBLE, ALREADY_VOTED }
}
