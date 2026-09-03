package com.skala.gateway.domain;

import com.skala.gateway.domain.enums.UnmaskStatus;
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

/**
 * 마스킹 해제 검토 요청 (D25).
 *
 * <p>규칙은 명단의 문자열만 본다. 고객과 이름이 같은 직원을 쓰면 그 이름도
 * {@code [고객명]}으로 가려지는데, 규칙만으로는 둘을 가를 방법이 없다. 그래서 사람에게
 * 넘긴다 — 직원이 사유를 적어 올리면 보안 담당자가 원문과 마스킹본을 비교해 정한다.
 *
 * <p>이 행이 있는 동안에만 담당자에게 원문이 열린다. 기획서 5.4의 원문 미노출은 감사
 * 콘솔이 남의 원문을 기본으로 보여주지 않는다는 뜻이고, 여기는 <b>작성자가 자기 문장을
 * 스스로 내놓는</b> 자리다. 열람 사실은 이 행으로 남는다.
 */
@Entity
@Table(name = "unmask_request")
public class UnmaskRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "message_id", nullable = false)
    private Message message;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private AppUser requester;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UnmaskStatus status = UnmaskStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private AppUser decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected UnmaskRequest() {
    }

    public UnmaskRequest(Message message, AppUser requester, String reason) {
        this.message = message;
        this.requester = requester;
        this.reason = reason;
        this.status = UnmaskStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /** 담당자가 확정한다. 한 번 정해진 요청은 다시 열지 않는다 */
    public void decide(AppUser decider, boolean approve, String note) {
        this.status = approve ? UnmaskStatus.APPROVED : UnmaskStatus.REJECTED;
        this.decidedBy = decider;
        this.decisionNote = note;
        this.decidedAt = OffsetDateTime.now();
    }

    public Long getRequestId() {
        return requestId;
    }

    public Message getMessage() {
        return message;
    }

    public AppUser getRequester() {
        return requester;
    }

    public String getReason() {
        return reason;
    }

    public UnmaskStatus getStatus() {
        return status;
    }

    public AppUser getDecidedBy() {
        return decidedBy;
    }

    public OffsetDateTime getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
