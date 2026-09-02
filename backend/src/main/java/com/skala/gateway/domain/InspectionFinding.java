package com.skala.gateway.domain;

import com.skala.gateway.ai.AiAssessment;
import com.skala.gateway.domain.enums.FindingSource;
import com.skala.gateway.domain.enums.PolicyCategory;
import com.skala.gateway.domain.enums.ReviewStatus;
import com.skala.gateway.domain.enums.RuleAction;
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
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 검사에서 발견된 항목. 규칙 매칭과 AI 후보가 같은 테이블을 쓴다 (기획서 6.2, 6.4).
 *
 * <p><b>{@code reviewStatus}의 기본값 {@code SUGGESTED}는 DB DEFAULT + CHECK 제약으로
 * 강제된다</b> (0.5 D6). AI 후보가 사람의 확정 없이 효력을 갖지 못하게 하는 장치이며,
 * 책임 경계 설계(4장)의 강제 지점 중 하나다. 애플리케이션 기본값으로만 두면 엔티티를
 * 우회한 INSERT 한 번에 무너진다.
 *
 * <p>규칙 finding은 사람의 검토 대상이 아니므로 {@code CONFIRMED} 고정이며 화면에
 * ACCEPT/REJECT 버튼을 노출하지 않는다.
 *
 * <p>{@code spanStart}/{@code spanEnd}는 <b>원문 기준</b> 오프셋이다. 마스킹은 길이를
 * 바꾸므로 이 값으로 {@code submittedText}를 자르면 하이라이트가 밀린다. 재계산하지 않고
 * FE가 {@code maskLabel} 문자열을 검색해 처리한다 (0.5 D3).
 */
@Entity
@Table(name = "inspection_finding")
public class InspectionFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "finding_id")
    private Long findingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_id", nullable = false)
    private Inspection inspection;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private FindingSource source;

    /** AI 후보는 항상 {@code null}이다. DB CHECK로 강제된다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id")
    private PolicyRule rule;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private PolicyCategory category;

    @Column(name = "span_start")
    private Integer spanStart;

    @Column(name = "span_end")
    private Integer spanEnd;

    /** 규칙 finding만 값을 갖는다. AI 후보는 액션을 결정하지 않는다 (4장). */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20)
    private RuleAction action;

    @Column(name = "rationale", columnDefinition = "text")
    private String rationale;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private List<AiAssessment.Evidence> evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus = ReviewStatus.SUGGESTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private AppUser reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    protected InspectionFinding() {
    }

    /** 규칙 매칭 finding. 사람의 검토 대상이 아니므로 CONFIRMED로 태어난다. */
    public static InspectionFinding ofRule(Inspection inspection, PolicyRule rule, PolicyCategory category,
                                           Integer spanStart, Integer spanEnd) {
        InspectionFinding f = new InspectionFinding();
        f.inspection = inspection;
        f.source = FindingSource.RULE;
        f.rule = rule;
        f.code = rule.getCode();
        f.category = category;
        f.spanStart = spanStart;
        f.spanEnd = spanEnd;
        f.action = rule.getAction();
        f.reviewStatus = ReviewStatus.CONFIRMED;
        return f;
    }

    /** AI 후보 finding. SUGGESTED로 태어나며 사람이 ACCEPTED/REJECTED로 확정한다. */
    public static InspectionFinding ofAi(Inspection inspection, String code, PolicyCategory category,
                                         String rationale, List<AiAssessment.Evidence> evidence) {
        InspectionFinding f = new InspectionFinding();
        f.inspection = inspection;
        f.source = FindingSource.AI;
        f.rule = null;
        f.code = code;
        f.category = category;
        f.rationale = rationale;
        f.evidence = evidence;
        f.reviewStatus = ReviewStatus.SUGGESTED;
        return f;
    }

    /** 사람의 확정. {@code reviewStatus}를 바꾸는 유일한 경로다. */
    public void review(ReviewStatus decision, AppUser reviewer, OffsetDateTime at) {
        this.reviewStatus = decision;
        this.reviewedBy = reviewer;
        this.reviewedAt = at;
    }

    public Long getFindingId() {
        return findingId;
    }

    public Inspection getInspection() {
        return inspection;
    }

    public FindingSource getSource() {
        return source;
    }

    public PolicyRule getRule() {
        return rule;
    }

    public String getCode() {
        return code;
    }

    public PolicyCategory getCategory() {
        return category;
    }

    public Integer getSpanStart() {
        return spanStart;
    }

    public Integer getSpanEnd() {
        return spanEnd;
    }

    public RuleAction getAction() {
        return action;
    }

    public String getRationale() {
        return rationale;
    }

    public List<AiAssessment.Evidence> getEvidence() {
        return evidence;
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public AppUser getReviewedBy() {
        return reviewedBy;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }
}
