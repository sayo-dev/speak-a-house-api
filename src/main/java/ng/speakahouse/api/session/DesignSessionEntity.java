package ng.speakahouse.api.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "design_session")
public class DesignSessionEntity {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "head_version_id", length = 40)
    private String headVersionId;

    @Column(name = "ai_calls", nullable = false)
    private int aiCalls;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DesignSessionEntity() {}

    public DesignSessionEntity(String id, String tokenHash, Instant createdAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.aiCalls = 0;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getTokenHash() { return tokenHash; }
    public String getHeadVersionId() { return headVersionId; }
    public void setHeadVersionId(String headVersionId) { this.headVersionId = headVersionId; }
    public int getAiCalls() { return aiCalls; }
    public void setAiCalls(int aiCalls) { this.aiCalls = aiCalls; }
    public Instant getCreatedAt() { return createdAt; }
}

