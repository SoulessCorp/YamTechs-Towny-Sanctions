package yt.yamtechs.sanctions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class AllyTeam {
    private final UUID id;
    private final String name;
    private UUID leaderNationId;
    private String leaderNationName;
    private final Map<UUID, String> members = new LinkedHashMap<>();
    private final Map<UUID, String> invitations = new LinkedHashMap<>();

    public AllyTeam(UUID id, String name, UUID leaderNationId, String leaderNationName) {
        this.id = id;
        this.name = name;
        this.leaderNationId = leaderNationId;
        this.leaderNationName = leaderNationName;
        members.put(leaderNationId, leaderNationName);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getLeaderNationId() {
        return leaderNationId;
    }

    public String getLeaderNationName() {
        return leaderNationName;
    }

    public Map<UUID, String> getMembers() {
        return Map.copyOf(members);
    }

    public Map<UUID, String> getInvitations() {
        return Map.copyOf(invitations);
    }

    public boolean containsNation(UUID nationId) {
        return members.containsKey(nationId);
    }

    public boolean isLeader(UUID nationId) {
        return leaderNationId.equals(nationId);
    }

    public boolean invite(UUID nationId, String nationName) {
        if (members.containsKey(nationId)) {
            return false;
        }
        invitations.put(nationId, nationName);
        return true;
    }

    public boolean accept(UUID nationId, String nationName) {
        if (!invitations.containsKey(nationId)) {
            return false;
        }
        invitations.remove(nationId);
        members.put(nationId, nationName);
        return true;
    }

    public void leave(UUID nationId) {
        members.remove(nationId);
        invitations.remove(nationId);
    }

    public void removeInvitation(UUID nationId) {
        invitations.remove(nationId);
    }

    void acceptedMemberForLoad(UUID nationId, String nationName) {
        members.put(nationId, nationName);
    }

    public void transferLeadership(UUID nationId, String nationName) {
        if (!members.containsKey(nationId)) {
            throw new IllegalArgumentException("The new leader must be a team member.");
        }
        leaderNationId = nationId;
        leaderNationName = nationName;
    }
}
