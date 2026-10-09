package yt.yamtechs.sanctions;

import java.util.Locale;
import java.util.UUID;

public final class Sanction {
    public enum TargetType { NATION, ALLY_TEAM }
    public enum IssuerType { SERVER, NATION, ALLY_TEAM }

    private final TargetType targetType;
    private final UUID targetId;
    private final String targetName;
    private final IssuerType issuerType;
    private final UUID issuerId;
    private final String issuerName;
    private final String imposedBy;
    private final long expiresAt;
    private final String reason;

    public Sanction(TargetType targetType, UUID targetId, String targetName,
                    IssuerType issuerType, UUID issuerId, String issuerName,
                    String imposedBy, long expiresAt, String reason) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetName = targetName;
        this.issuerType = issuerType;
        this.issuerId = issuerId;
        this.issuerName = issuerName;
        this.imposedBy = imposedBy;
        this.expiresAt = expiresAt;
        this.reason = reason;
    }

    public TargetType getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getTargetName() { return targetName; }
    public IssuerType getIssuerType() { return issuerType; }
    public UUID getIssuerId() { return issuerId; }
    public String getIssuerName() { return issuerName; }
    public String getImposedBy() { return imposedBy; }
    public long getExpiresAt() { return expiresAt; }
    public String getReason() { return reason; }

    public boolean isPermanent() { return expiresAt < 0; }
    public boolean isExpired(long now) { return !isPermanent() && expiresAt <= now; }

    public String storageKey() {
        String issuer = issuerId == null ? "server" : issuerId.toString();
        return targetType.name().toLowerCase(Locale.ROOT) + "_" + targetId + "__"
                + issuerType.name().toLowerCase(Locale.ROOT) + "_" + issuer;
    }
}
