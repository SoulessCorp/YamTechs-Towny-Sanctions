package yt.yamtechs.sanctions;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public final class SanctionVote {
    private final UUID id;
    private final UUID teamId;
    private final String teamName;
    private final boolean lifting;
    private final UUID targetNationId;
    private final String targetNationName;
    private final long sanctionDurationMillis;
    private final long deadline;
    private final String reason;
    private final UUID proposerNationId;
    private final String proposerNationName;
    private final Map<UUID, String> electorate = new LinkedHashMap<>();
    private final Map<UUID, Boolean> votes = new LinkedHashMap<>();

    public SanctionVote(UUID id, UUID teamId, String teamName, boolean lifting, UUID targetNationId,
                        String targetNationName, long sanctionDurationMillis, long deadline,
                        String reason, UUID proposerNationId, String proposerNationName,
                        Map<UUID, String> electorate) {
        this.id = id;
        this.teamId = teamId;
        this.teamName = teamName;
        this.lifting = lifting;
        this.targetNationId = targetNationId;
        this.targetNationName = targetNationName;
        this.sanctionDurationMillis = sanctionDurationMillis;
        this.deadline = deadline;
        this.reason = reason;
        this.proposerNationId = proposerNationId;
        this.proposerNationName = proposerNationName;
        this.electorate.putAll(electorate);
        if (this.electorate.containsKey(proposerNationId)) {
            votes.put(proposerNationId, true);
        }
    }

    public UUID getId() { return id; }
    public UUID getTeamId() { return teamId; }
    public String getTeamName() { return teamName; }
    public boolean isLifting() { return lifting; }
    public UUID getTargetNationId() { return targetNationId; }
    public String getTargetNationName() { return targetNationName; }
    public long getSanctionDurationMillis() { return sanctionDurationMillis; }
    public long getDeadline() { return deadline; }
    public String getReason() { return reason; }
    public UUID getProposerNationId() { return proposerNationId; }
    public String getProposerNationName() { return proposerNationName; }
    public Map<UUID, String> getElectorate() { return Map.copyOf(electorate); }
    public Map<UUID, Boolean> getVotes() { return Map.copyOf(votes); }

    public void setVote(UUID nationId, boolean yes) {
        votes.put(nationId, yes);
    }

    public int yesCount() {
        return (int) votes.values().stream().filter(Boolean::booleanValue).count();
    }

    public int noCount() {
        return votes.size() - yesCount();
    }

    public int voteCount() {
        return votes.size();
    }

    public int electorateSize() {
        return electorate.size();
    }

    public boolean canVote(UUID nationId) {
        return electorate.containsKey(nationId);
    }
}
