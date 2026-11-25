package ng.speakahouse.api.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "design_version")
public class DesignVersionEntity {

    @Id
    @Column(length = 40)
    private String id;

    @Column(name = "session_id", nullable = false, length = 40)
    private String sessionId;

    @Column(nullable = false)
    private int seq;

    @Column(name = "parent_id", length = 40)
    private String parentId;

    @Column(name = "input_text", length = 500)
    private String inputText;

    @Column(name = "spec_json", nullable = false, columnDefinition = "text")
    private String specJson;

    @Column(name = "plan_json", nullable = false, columnDefinition = "text")
    private String planJson;

    @Column(name = "estimate_json", nullable = false, columnDefinition = "text")
    private String estimateJson;

    @Column(name = "diff_json", columnDefinition = "text")
    private String diffJson;

    @Column(name = "explanation_json", columnDefinition = "text")
    private String explanationJson;

    @Column(name = "expected_ngn", nullable = false)
    private long expectedNgn;

    @Column(name = "budget_status", length = 10)
    private String budgetStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DesignVersionEntity() {}

    public DesignVersionEntity(
            String id,
            String sessionId,
            int seq,
            String parentId,
            String inputText,
            String specJson,
            String planJson,
            String estimateJson,
            String diffJson,
            String explanationJson,
            long expectedNgn,
            String budgetStatus,
            Instant createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.seq = seq;
        this.parentId = parentId;
        this.inputText = inputText;
        this.specJson = specJson;
        this.planJson = planJson;
        this.estimateJson = estimateJson;
        this.diffJson = diffJson;
        this.explanationJson = explanationJson;
        this.expectedNgn = expectedNgn;
        this.budgetStatus = budgetStatus;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public int getSeq() { return seq; }
    public String getParentId() { return parentId; }
    public String getInputText() { return inputText; }
    public String getSpecJson() { return specJson; }
    public String getPlanJson() { return planJson; }
    public String getEstimateJson() { return estimateJson; }
    public String getDiffJson() { return diffJson; }
    public String getExplanationJson() { return explanationJson; }
    public long getExpectedNgn() { return expectedNgn; }
    public String getBudgetStatus() { return budgetStatus; }
    public Instant getCreatedAt() { return createdAt; }
}

