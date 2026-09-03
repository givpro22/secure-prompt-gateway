package com.skala.gateway.domain;

import com.skala.gateway.ai.AiAssessment;
import com.skala.gateway.domain.enums.AiStatus;
import com.skala.gateway.domain.enums.DecidedBy;
import com.skala.gateway.domain.enums.FinalDecision;
import com.skala.gateway.domain.enums.InspectionPhase;
import com.skala.gateway.domain.jsonb.PolicySnapshot;
import com.skala.gateway.domain.jsonb.RuleResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 검사 1회의 결과 (기획서 6.2, 6.4).
 *
 * <p>JSONB 3개({@code policySnapshot}, {@code ruleResult}, {@code aiResult})가 AI-Ready
 * "Structured Data" 원칙의 증거다. 문자열이 아니라 타입 있는 객체로 매핑하며, AI 응답
 * 스키마가 확장돼도 컬럼을 추가하지 않는다.
 *
 * <p>{@code aiResult}는 {@code com.skala.gateway.ai.AiAssessment}를 그대로 쓴다. 저장용
 * 타입을 따로 만들면 AI 스키마와 DB 스키마가 조용히 갈린다. API 응답 필드명만
 * {@code aiAssessment}로 바뀐다 (계약서 §2).
 *
 * <p>{@code aiResult}가 {@code null}인 경우는 둘이다 — BLOCK이라 AI를 호출하지 않았거나
 * (7.5), 호출했는데 실패했다({@code aiStatus=FAILED}).
 */
@Entity
@Table(name = "inspection")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long inspectionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 10)
    private InspectionPhase phase = InspectionPhase.INPUT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "policy_snapshot", nullable = false, columnDefinition = "jsonb")
    private PolicySnapshot policySnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_result", nullable = false, columnDefinition = "jsonb")
    private RuleResult ruleResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_status", nullable = false, length = 20)
    private AiStatus aiStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_result", columnDefinition = "jsonb")
    private AiAssessment aiResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_decision", length = 20)
    private FinalDecision finalDecision;

    @Enumerated(EnumType.STRING)
    @Column(name = "decided_by", length = 10)
    private DecidedBy decidedBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    protected Inspection() {
    }

    public Inspection(Message message, PolicySnapshot policySnapshot, RuleResult ruleResult,
                      AiStatus aiStatus, FinalDecision finalDecision, DecidedBy decidedBy) {
        this(message, InspectionPhase.INPUT, policySnapshot, ruleResult, aiStatus, finalDecision, decidedBy);
    }

    /** 출력 검사(UC-08)는 {@code phase=OUTPUT}으로 같은 메시지에 한 건 더 붙는다 */
    public Inspection(Message message, InspectionPhase phase, PolicySnapshot policySnapshot,
                      RuleResult ruleResult, AiStatus aiStatus, FinalDecision finalDecision,
                      DecidedBy decidedBy) {
        this.phase = phase;
        this.message = message;
        this.policySnapshot = policySnapshot;
        this.ruleResult = ruleResult;
        this.aiStatus = aiStatus;
        this.finalDecision = finalDecision;
        this.decidedBy = decidedBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public Long getInspectionId() {
        return inspectionId;
    }

    public Message getMessage() {
        return message;
    }

    public InspectionPhase getPhase() {
        return phase;
    }

    public PolicySnapshot getPolicySnapshot() {
        return policySnapshot;
    }

    public RuleResult getRuleResult() {
        return ruleResult;
    }

    public AiStatus getAiStatus() {
        return aiStatus;
    }

    public void setAiStatus(AiStatus aiStatus) {
        this.aiStatus = aiStatus;
    }

    public AiAssessment getAiResult() {
        return aiResult;
    }

    public void setAiResult(AiAssessment aiResult) {
        this.aiResult = aiResult;
    }

    public FinalDecision getFinalDecision() {
        return finalDecision;
    }

    public void setFinalDecision(FinalDecision finalDecision) {
        this.finalDecision = finalDecision;
    }

    public DecidedBy getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(DecidedBy decidedBy) {
        this.decidedBy = decidedBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
