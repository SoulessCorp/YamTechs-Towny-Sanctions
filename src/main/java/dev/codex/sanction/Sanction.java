package dev.codex.sanction;

import java.util.UUID;

public final class Sanction {
    private final UUID targetNationId;
    private final String targetNationName;
    private final UUID issuerNationId;
    private final String issuerName;
    private final String imposedBy;
    private final long expiresAt;
    private final String reason;

    public Sanction(UUID targetNationId, String targetNationName, UUID issuerNationId,
                    String issuerName, String imposedBy, long expiresAt, String reason) {
        this.targetNationId = targetNationId;
        this.targetNationName = targetNationName;
        this.issuerNationId = issuerNationId;
        this.issuerName = issuerName;
        this.imposedBy = imposedBy;
        this.expiresAt = expiresAt;
        this.reason = reason;
    }

    public UUID getTargetNationId() {
        return targetNationId;
    }

    public String getTargetNationName() {
        return targetNationName;
    }

    public UUID getIssuerNationId() {
        return issuerNationId;
    }

    public String getIssuerName() {
        return issuerName;
    }

    public String getImposedBy() {
        return imposedBy;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getReason() {
        return reason;
    }

    public boolean isPermanent() {
        return expiresAt < 0;
    }

    public boolean isExpired(long now) {
        return !isPermanent() && expiresAt <= now;
    }

    public String storageKey() {
        return targetNationId + ":" + (issuerNationId == null ? "server" : issuerNationId);
    }
}
